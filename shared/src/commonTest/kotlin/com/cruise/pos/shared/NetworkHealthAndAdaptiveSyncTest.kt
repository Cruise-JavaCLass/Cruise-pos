package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.DiscoveredEdgeGateway
import com.cruise.pos.shared.domain.model.NetworkHealthTier
import com.cruise.pos.shared.domain.model.SyncCompressionMode
import com.cruise.pos.shared.domain.model.SyncGatewayType
import com.cruise.pos.shared.domain.service.AdaptiveNetworkSyncEngine
import com.cruise.pos.shared.domain.service.MockEdgeGatewayDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkHealthAndAdaptiveSyncTest {

    private val discovery = MockEdgeGatewayDiscovery()
    private val engine = AdaptiveNetworkSyncEngine(discovery)

    @Test
    fun testEvaluateHealthTierCriteria() {
        // Excellent 5GHz Wi-Fi on Lido deck
        val tierExcellent = engine.evaluateHealthTier(
            latencyMillis = 35L,
            rssiDbm = -50,
            bandwidthKbps = 5000,
            packetLossPercent = 0.0f
        )
        assertEquals(NetworkHealthTier.EXCELLENT_5GHZ_DECK, tierExcellent)

        // Severe partial packet loss (0.99f) must not select EXCELLENT_5GHZ_DECK even with low latency
        val tierSevereLoss = engine.evaluateHealthTier(
            latencyMillis = 50L,
            rssiDbm = -55,
            bandwidthKbps = 3000,
            packetLossPercent = 0.99f
        )
        assertEquals(NetworkHealthTier.OFFLINE_DEAD_ZONE, tierSevereLoss)

        // Degraded Satellite link
        val tierDegraded = engine.evaluateHealthTier(
            latencyMillis = 450L,
            rssiDbm = -75,
            bandwidthKbps = 200,
            packetLossPercent = 0.05f
        )
        assertEquals(NetworkHealthTier.DEGRADED_SATELLITE, tierDegraded)

        // High Latency Satellite (Low bandwidth < 50kbps)
        val tierHighLatency = engine.evaluateHealthTier(
            latencyMillis = 1200L,
            rssiDbm = -82,
            bandwidthKbps = 35,
            packetLossPercent = 0.15f
        )
        assertEquals(NetworkHealthTier.HIGH_LATENCY_SAT, tierHighLatency)

        // Dead Zone / Airplane Mode
        val tierOffline = engine.evaluateHealthTier(
            latencyMillis = 10000L,
            rssiDbm = -99,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f
        )
        assertEquals(NetworkHealthTier.OFFLINE_DEAD_ZONE, tierOffline)
    }

    @Test
    fun testAdaptiveThrottlingAndCompressionSwitching() {
        // High-speed deck Wi-Fi -> RAW_JSON, large batch
        val policy5G = engine.recordNetworkMeasurement(
            latencyMillis = 50L,
            rssiDbm = -55,
            bandwidthKbps = 3000,
            packetLossPercent = 0.0f,
            timestampMillis = 1000L
        )
        assertEquals(SyncCompressionMode.RAW_JSON, policy5G.compressionMode)
        assertEquals(50, policy5G.syncBatchSize)
        assertEquals(SyncGatewayType.CLOUD_SHORE_PRIMARY, policy5G.gatewayType)

        // Drop to degraded satellite -> GZIP_COMPRESSED, medium batch
        val policySat = engine.recordNetworkMeasurement(
            latencyMillis = 500L,
            rssiDbm = -78,
            bandwidthKbps = 150,
            packetLossPercent = 0.02f,
            timestampMillis = 2000L
        )
        assertEquals(SyncCompressionMode.GZIP_COMPRESSED, policySat.compressionMode)
        assertEquals(20, policySat.syncBatchSize)

        // Ultra-low bandwidth satellite -> ULTRA_LOW_BANDWIDTH_BINARY, tiny batch
        val policyUltraLow = engine.recordNetworkMeasurement(
            latencyMillis = 1500L,
            rssiDbm = -88,
            bandwidthKbps = 25,
            packetLossPercent = 0.20f,
            timestampMillis = 3000L
        )
        assertEquals(SyncCompressionMode.ULTRA_LOW_BANDWIDTH_BINARY, policyUltraLow.compressionMode)
        assertEquals(5, policyUltraLow.syncBatchSize)
    }

    @Test
    fun testNetworkTransitionOnlineToAirplaneModeToEdgeMesh() {
        // Step 1: Start Online
        val onlinePolicy = engine.recordNetworkMeasurement(
            latencyMillis = 40L,
            rssiDbm = -52,
            bandwidthKbps = 2500,
            packetLossPercent = 0.0f,
            timestampMillis = 1000L
        )
        assertEquals(SyncGatewayType.CLOUD_SHORE_PRIMARY, onlinePolicy.gatewayType)
        assertTrue(engine.networkState.value.isOnline)

        // Step 2: Airplane Mode / Dead zone entered (No edge gateway yet)
        val offlinePolicy = engine.recordNetworkMeasurement(
            latencyMillis = 10000L,
            rssiDbm = -100,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f,
            timestampMillis = 2000L
        )
        assertEquals(SyncGatewayType.LOCAL_OFFLINE_STORAGE, offlinePolicy.gatewayType)
        assertEquals(0, offlinePolicy.syncBatchSize)
        assertFalse(engine.networkState.value.isOnline)

        // Step 3: mDNS / NSD discovers local Ship Edge Mesh Gateway on shipboard intranet
        discovery.registerGateway(
            DiscoveredEdgeGateway(
                serviceName = "_cruise-edge-sync._tcp",
                host = "192.168.10.5",
                port = 8443,
                discoveredAtMillis = 2500L
            )
        )

        val edgeMeshPolicy = engine.recordNetworkMeasurement(
            latencyMillis = 9999L, // Shore internet still down
            rssiDbm = -65, // Local ship mesh Wi-Fi AP is strong!
            bandwidthKbps = 0, // No external internet
            packetLossPercent = 1.0f,
            timestampMillis = 3000L
        )

        // Must automatically failover to shipboard edge mesh gateway
        assertEquals(SyncGatewayType.SHIP_EDGE_MESH_GATEWAY, edgeMeshPolicy.gatewayType)
        assertEquals("192.168.10.5:8443", edgeMeshPolicy.targetGatewayHost)
        assertEquals(25, edgeMeshPolicy.syncBatchSize)
        assertEquals(SyncCompressionMode.GZIP_COMPRESSED, edgeMeshPolicy.compressionMode)

        // Derived policy without passing edgeGateway argument directly preserves edge gateway
        val derivedPolicy = engine.computeSyncPolicy(engine.networkState.value)
        assertEquals(SyncGatewayType.SHIP_EDGE_MESH_GATEWAY, derivedPolicy.gatewayType)
        assertEquals("192.168.10.5:8443", derivedPolicy.targetGatewayHost)
        assertEquals(25, derivedPolicy.syncBatchSize)
    }

    @Test
    fun testPayloadCompressionEfficiency() {
        val sampleJson = """
            {"transactions":[{"id":"TX-101","amount":45.0,"items":["Steak","Wine"]},{"id":"TX-102","amount":12.5,"items":["Beer"]}]}
        """.trimIndent().repeat(10)

        val rawBytes = engine.compressPayload(sampleJson, SyncCompressionMode.RAW_JSON)
        val gzipBytes = engine.compressPayload(sampleJson, SyncCompressionMode.GZIP_COMPRESSED)

        assertTrue(gzipBytes.size < rawBytes.size)
        assertTrue(gzipBytes.size > 0)
    }
    @Test
    fun testStaleMeasurementDoesNotAffectAcceptedSnapshotPolicy() {
        // Retained fresh snapshot at t = 5000: OFFLINE without edge gateway
        val freshOfflinePolicy = engine.recordNetworkMeasurement(
            latencyMillis = 9999L,
            rssiDbm = -100,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f,
            timestampMillis = 5000L
        )
        assertEquals(SyncGatewayType.LOCAL_OFFLINE_STORAGE, freshOfflinePolicy.gatewayType)

        // Register edge gateway
        discovery.registerGateway(
            DiscoveredEdgeGateway(
                serviceName = "_cruise-edge-sync._tcp",
                host = "192.168.10.99",
                port = 8443,
                discoveredAtMillis = 2000L
            )
        )

        // Incoming measurement with stale timestamp t = 3000 (earlier than 5000)
        val stalePolicy = engine.recordNetworkMeasurement(
            latencyMillis = 9999L,
            rssiDbm = -65,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f,
            timestampMillis = 3000L
        )

        // Must retain previous accepted snapshot policy (LOCAL_OFFLINE_STORAGE), not switch to edge gateway
        assertEquals(SyncGatewayType.LOCAL_OFFLINE_STORAGE, stalePolicy.gatewayType)
        assertEquals(5000L, engine.networkState.value.timestampMillis)
    }
}