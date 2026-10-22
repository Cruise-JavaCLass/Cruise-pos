package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class NetworkHealthTier {
    EXCELLENT_5GHZ_DECK, // Green (< 100ms, > 500kbps)
    DEGRADED_SATELLITE,  // Yellow (100ms - 800ms, 50 - 500kbps)
    HIGH_LATENCY_SAT,    // Orange (> 800ms, < 50kbps)
    OFFLINE_DEAD_ZONE    // Red (No network / packet drop)
}

@Serializable
enum class SyncCompressionMode {
    RAW_JSON,
    GZIP_COMPRESSED,
    ULTRA_LOW_BANDWIDTH_BINARY
}

@Serializable
enum class SyncGatewayType {
    CLOUD_SHORE_PRIMARY,
    SHIP_EDGE_MESH_GATEWAY,
    LOCAL_OFFLINE_STORAGE
}

@Serializable
data class NetworkQualitySnapshot(
    val tier: NetworkHealthTier,
    val latencyMillis: Long,
    val rssiDbm: Int = -60, // e.g. -45 dBm (strong) to -85 dBm (weak)
    val estimatedBandwidthKbps: Int = 1000,
    val packetLossPercent: Float = 0.0f,
    val activeGatewayType: SyncGatewayType = SyncGatewayType.CLOUD_SHORE_PRIMARY,
    val edgeGatewayHost: String? = null,
    val timestampMillis: Long = 0L
) {
    val isOnline: Boolean get() = tier != NetworkHealthTier.OFFLINE_DEAD_ZONE
}

@Serializable
data class AdaptiveSyncPolicy(
    val syncBatchSize: Int,
    val syncIntervalMillis: Long,
    val compressionMode: SyncCompressionMode,
    val gatewayType: SyncGatewayType,
    val targetGatewayHost: String? = null
)

@Serializable
data class DiscoveredEdgeGateway(
    val serviceName: String,
    val host: String,
    val port: Int,
    val priority: Int = 1,
    val discoveredAtMillis: Long = 0L
)
