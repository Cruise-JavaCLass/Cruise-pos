package com.example.cruise_pos

import com.cruise.pos.shared.domain.model.DiscoveredEdgeGateway
import com.cruise.pos.shared.domain.model.NetworkHealthTier
import com.cruise.pos.shared.domain.model.NetworkQualitySnapshot
import com.cruise.pos.shared.domain.model.SyncCompressionMode
import com.cruise.pos.shared.domain.model.SyncGatewayType
import com.cruise.pos.shared.domain.service.AdaptiveNetworkSyncEngine
import com.cruise.pos.shared.domain.service.MockEdgeGatewayDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkHealthMonitoringAndUiTest {

    private lateinit var discovery: MockEdgeGatewayDiscovery
    private lateinit var engine: AdaptiveNetworkSyncEngine

    @Before
    fun setUp() {
        discovery = MockEdgeGatewayDiscovery()
        engine = AdaptiveNetworkSyncEngine(discovery)
    }

    @Test
    fun testStatusBarLatencyPillTierTransitions() {
        // Excellent Deck Wi-Fi
        var snapshot = engine.networkState.value
        assertEquals(NetworkHealthTier.EXCELLENT_5GHZ_DECK, snapshot.tier)
        assertTrue(snapshot.isOnline)

        // Satellite degraded
        engine.recordNetworkMeasurement(
            latencyMillis = 400L,
            rssiDbm = -75,
            bandwidthKbps = 200,
            packetLossPercent = 0.05f,
            timestampMillis = 1000L
        )
        snapshot = engine.networkState.value
        assertEquals(NetworkHealthTier.DEGRADED_SATELLITE, snapshot.tier)
        assertEquals(400L, snapshot.latencyMillis)

        // Offline Dead Zone
        engine.recordNetworkMeasurement(
            latencyMillis = 9000L,
            rssiDbm = -100,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f,
            timestampMillis = 2000L
        )
        snapshot = engine.networkState.value
        assertEquals(NetworkHealthTier.OFFLINE_DEAD_ZONE, snapshot.tier)
        assertFalse(snapshot.isOnline)
    }

    @Test
    fun testEdgeMeshGatewayAutoDiscoveryAndReroute() {
        // Drop external satellite connection
        engine.recordNetworkMeasurement(
            latencyMillis = 9999L,
            rssiDbm = -80,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f,
            timestampMillis = 1000L
        )
        assertEquals(SyncGatewayType.LOCAL_OFFLINE_STORAGE, engine.networkState.value.activeGatewayType)

        // Edge server announced via mDNS / NSD
        discovery.registerGateway(
            DiscoveredEdgeGateway(
                serviceName = "_cruise-edge-sync._tcp",
                host = "10.0.1.20",
                port = 8443,
                discoveredAtMillis = 1500L
            )
        )

        val policy = engine.recordNetworkMeasurement(
            latencyMillis = 9999L,
            rssiDbm = -70,
            bandwidthKbps = 0,
            packetLossPercent = 1.0f,
            timestampMillis = 2000L
        )

        assertEquals(SyncGatewayType.SHIP_EDGE_MESH_GATEWAY, policy.gatewayType)
        assertEquals("10.0.1.20:8443", policy.targetGatewayHost)
        assertEquals(SyncCompressionMode.GZIP_COMPRESSED, policy.compressionMode)
    }
}
