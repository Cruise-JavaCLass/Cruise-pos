package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.AdaptiveSyncPolicy
import com.cruise.pos.shared.domain.model.DiscoveredEdgeGateway
import com.cruise.pos.shared.domain.model.NetworkHealthTier
import com.cruise.pos.shared.domain.model.NetworkQualitySnapshot
import com.cruise.pos.shared.domain.model.SyncCompressionMode
import com.cruise.pos.shared.domain.model.SyncGatewayType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class AdaptiveNetworkSyncEngine(
    private val edgeGatewayDiscovery: EdgeGatewayDiscovery = MockEdgeGatewayDiscovery()
) {

    private val _networkState = MutableStateFlow(
        NetworkQualitySnapshot(
            tier = NetworkHealthTier.EXCELLENT_5GHZ_DECK,
            latencyMillis = 45L,
            rssiDbm = -55,
            estimatedBandwidthKbps = 2500,
            activeGatewayType = SyncGatewayType.CLOUD_SHORE_PRIMARY
        )
    )
    val networkState: StateFlow<NetworkQualitySnapshot> = _networkState.asStateFlow()

    /**
     * Determines health tier based on measured roundtrip latency, RSSI signal, bandwidth, and packet loss.
     * Note: packetLossPercent is represented as a fraction between 0.0f (0%) and 1.0f (100%).
     */
    fun evaluateHealthTier(
        latencyMillis: Long,
        rssiDbm: Int,
        bandwidthKbps: Int,
        packetLossPercent: Float
    ): NetworkHealthTier {
        if (packetLossPercent >= 0.50f || latencyMillis >= 5000L || rssiDbm <= -95) {
            return NetworkHealthTier.OFFLINE_DEAD_ZONE
        }

        return when {
            latencyMillis <= 120L && bandwidthKbps >= 500 && rssiDbm >= -70 && packetLossPercent < 0.02f -> {
                NetworkHealthTier.EXCELLENT_5GHZ_DECK
            }
            latencyMillis <= 800L && bandwidthKbps >= 50 && rssiDbm >= -85 && packetLossPercent < 0.10f -> {
                NetworkHealthTier.DEGRADED_SATELLITE
            }
            else -> {
                NetworkHealthTier.HIGH_LATENCY_SAT
            }
        }
    }

    /**
     * Updates network metrics and adapts sync policy.
     */
    fun recordNetworkMeasurement(
        latencyMillis: Long,
        rssiDbm: Int,
        bandwidthKbps: Int,
        packetLossPercent: Float,
        timestampMillis: Long
    ): AdaptiveSyncPolicy {
        val tier = evaluateHealthTier(latencyMillis, rssiDbm, bandwidthKbps, packetLossPercent)

        // If offline or high-latency satellite, check for active ship edge mesh gateway
        val discoveredEdge = if (tier == NetworkHealthTier.OFFLINE_DEAD_ZONE || tier == NetworkHealthTier.HIGH_LATENCY_SAT) {
            val now = timestampMillis
            edgeGatewayDiscovery.getDiscoveredGateways()
                .filter { now - it.discoveredAtMillis < 60_000L || it.discoveredAtMillis == 0L }
                .minByOrNull { it.priority }
        } else {
            null
        }

        val edgeHost = discoveredEdge?.let { "${it.host}:${it.port}" }
        val activeGateway = when {
            discoveredEdge != null -> SyncGatewayType.SHIP_EDGE_MESH_GATEWAY
            tier == NetworkHealthTier.OFFLINE_DEAD_ZONE -> SyncGatewayType.LOCAL_OFFLINE_STORAGE
            else -> SyncGatewayType.CLOUD_SHORE_PRIMARY
        }

        val snapshot = NetworkQualitySnapshot(
            tier = tier,
            latencyMillis = latencyMillis,
            rssiDbm = rssiDbm,
            estimatedBandwidthKbps = bandwidthKbps,
            packetLossPercent = packetLossPercent,
            activeGatewayType = activeGateway,
            edgeGatewayHost = edgeHost,
            timestampMillis = timestampMillis
        )
        _networkState.value = snapshot

        return computeSyncPolicy(snapshot, discoveredEdge)
    }

    /**
     * Computes batch size, frequency, and compression mode based on current connection tier.
     */
    fun computeSyncPolicy(
        snapshot: NetworkQualitySnapshot,
        edgeGateway: DiscoveredEdgeGateway? = null
    ): AdaptiveSyncPolicy {
        val targetHost = edgeGateway?.let { "${it.host}:${it.port}" } ?: snapshot.edgeGatewayHost
        val hasEdgeGateway = edgeGateway != null || snapshot.activeGatewayType == SyncGatewayType.SHIP_EDGE_MESH_GATEWAY || !snapshot.edgeGatewayHost.isNullOrBlank()

        return when (snapshot.tier) {
            NetworkHealthTier.EXCELLENT_5GHZ_DECK -> {
                AdaptiveSyncPolicy(
                    syncBatchSize = 50,
                    syncIntervalMillis = 3000L,
                    compressionMode = SyncCompressionMode.RAW_JSON,
                    gatewayType = SyncGatewayType.CLOUD_SHORE_PRIMARY
                )
            }
            NetworkHealthTier.DEGRADED_SATELLITE -> {
                AdaptiveSyncPolicy(
                    syncBatchSize = 20,
                    syncIntervalMillis = 10000L,
                    compressionMode = SyncCompressionMode.GZIP_COMPRESSED,
                    gatewayType = SyncGatewayType.CLOUD_SHORE_PRIMARY
                )
            }
            NetworkHealthTier.HIGH_LATENCY_SAT -> {
                if (hasEdgeGateway) {
                    AdaptiveSyncPolicy(
                        syncBatchSize = 30,
                        syncIntervalMillis = 5000L,
                        compressionMode = SyncCompressionMode.GZIP_COMPRESSED,
                        gatewayType = SyncGatewayType.SHIP_EDGE_MESH_GATEWAY,
                        targetGatewayHost = targetHost
                    )
                } else {
                    AdaptiveSyncPolicy(
                        syncBatchSize = 5,
                        syncIntervalMillis = 30000L,
                        compressionMode = SyncCompressionMode.ULTRA_LOW_BANDWIDTH_BINARY,
                        gatewayType = SyncGatewayType.CLOUD_SHORE_PRIMARY
                    )
                }
            }
            NetworkHealthTier.OFFLINE_DEAD_ZONE -> {
                if (hasEdgeGateway) {
                    AdaptiveSyncPolicy(
                        syncBatchSize = 25,
                        syncIntervalMillis = 5000L,
                        compressionMode = SyncCompressionMode.GZIP_COMPRESSED,
                        gatewayType = SyncGatewayType.SHIP_EDGE_MESH_GATEWAY,
                        targetGatewayHost = targetHost
                    )
                } else {
                    AdaptiveSyncPolicy(
                        syncBatchSize = 0,
                        syncIntervalMillis = 0L,
                        compressionMode = SyncCompressionMode.RAW_JSON,
                        gatewayType = SyncGatewayType.LOCAL_OFFLINE_STORAGE
                    )
                }
            }
        }
    }

    /**
     * Compresses payload according to selected compression mode.
     */
    fun compressPayload(rawJson: String, mode: SyncCompressionMode): ByteArray {
        val rawBytes = rawJson.toByteArray(Charsets.UTF_8)
        return when (mode) {
            SyncCompressionMode.RAW_JSON -> rawBytes
            SyncCompressionMode.GZIP_COMPRESSED -> {
                val bos = ByteArrayOutputStream()
                GZIPOutputStream(bos).use { gzip ->
                    gzip.write(rawBytes)
                }
                bos.toByteArray()
            }
            SyncCompressionMode.ULTRA_LOW_BANDWIDTH_BINARY -> {
                // High compression GZIP with binary indicator prefix
                val bos = ByteArrayOutputStream()
                GZIPOutputStream(bos).use { gzip ->
                    gzip.write(rawBytes)
                }
                byteArrayOf(0x01.toByte()) + bos.toByteArray()
            }
        }
    }
}

interface EdgeGatewayDiscovery {
    fun getDiscoveredGateways(): List<DiscoveredEdgeGateway>
    fun registerGateway(gateway: DiscoveredEdgeGateway)
    fun clearGateways()
}

class MockEdgeGatewayDiscovery(
    initialGateways: List<DiscoveredEdgeGateway> = emptyList()
) : EdgeGatewayDiscovery {
    private val lock = Any()
    private val gateways = initialGateways.toMutableList()

    override fun getDiscoveredGateways(): List<DiscoveredEdgeGateway> = synchronized(lock) {
        gateways.toList()
    }

    override fun registerGateway(gateway: DiscoveredEdgeGateway) {
        synchronized(lock) {
            gateways.removeAll { it.serviceName == gateway.serviceName }
            gateways.add(gateway)
        }
    }

    override fun clearGateways() {
        synchronized(lock) {
            gateways.clear()
        }
    }
}
