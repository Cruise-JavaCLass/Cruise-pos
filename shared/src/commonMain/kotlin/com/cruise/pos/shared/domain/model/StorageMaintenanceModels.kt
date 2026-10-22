package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MaintenanceTrigger {
    SCHEDULED_PERIODIC,
    CHARGING_IDLE_CRADLE,
    MANUAL_OPERATOR,
    LOW_DISK_SPACE_EMERGENCY
}

@Serializable
data class StoragePruningStats(
    val ordersPrunedCount: Int,
    val auditLogsPrunedCount: Int,
    val mediaCacheBytesReclaimed: Long,
    val databaseBytesBefore: Long,
    val databaseBytesAfter: Long,
    val databaseBytesReclaimed: Long = (databaseBytesBefore - databaseBytesAfter).coerceAtLeast(0L),
    val walCheckpointExecuted: Boolean = true,
    val durationMillis: Long = 0L,
    val trigger: MaintenanceTrigger = MaintenanceTrigger.CHARGING_IDLE_CRADLE
)

@Serializable
data class EncryptedArchiveBundle(
    val archiveId: String,
    val terminalId: String,
    val recordsCount: Int,
    val payloadChecksumSha256: String,
    val encryptedPayloadBase64: String,
    val targetDestination: String, // "EXTERNAL_SD", "USB_OTG", "CLOUD_BACKUP"
    val timestampMillis: Long
)
