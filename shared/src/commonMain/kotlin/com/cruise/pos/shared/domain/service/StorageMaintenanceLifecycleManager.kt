package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.EncryptedArchiveBundle
import com.cruise.pos.shared.domain.model.MaintenanceTrigger
import com.cruise.pos.shared.domain.model.StoragePruningStats
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

interface DatabaseCompactor {
    fun executeVacuum(): Long
    fun executeWalCheckpoint(): Boolean
    fun pruneOldSyncedOrders(retentionCutoffMillis: Long): Int
    fun pruneOldAuditLogs(retentionCutoffMillis: Long): Int
    fun getDatabaseSizeBytes(): Long
}

interface MediaDiskCacheCleaner {
    fun pruneExpiredMedia(maxCacheSizeBytes: Long = 200 * 1024 * 1024L): Long
    fun getCurrentCacheSizeBytes(): Long
}

class StorageMaintenanceLifecycleManager(
    private val databaseCompactor: DatabaseCompactor = MockDatabaseCompactor(),
    private val mediaCleaner: MediaDiskCacheCleaner = MockMediaDiskCacheCleaner()
) {

    /**
     * Executes pruning and compaction when device is plugged into charging cradle or scheduled.
     * Orders older than retentionCutoffMillis (typically 7 days) and already synced are pruned.
     */
    fun performStorageCompaction(
        currentTimestampMillis: Long,
        retentionDays: Int = 7,
        trigger: MaintenanceTrigger = MaintenanceTrigger.CHARGING_IDLE_CRADLE
    ): StoragePruningStats {
        val startTime = System.currentTimeMillis()
        val retentionCutoffMillis = currentTimestampMillis - (retentionDays.toLong() * 24 * 60 * 60 * 1000L)

        val dbSizeBefore = databaseCompactor.getDatabaseSizeBytes()

        // 1. Prune settled synced orders > 7 days
        val prunedOrders = databaseCompactor.pruneOldSyncedOrders(retentionCutoffMillis)

        // 2. Prune old audit logs > 7 days
        val prunedLogs = databaseCompactor.pruneOldAuditLogs(retentionCutoffMillis)

        // 3. Clean media cache exceeding 200MB limit
        val mediaReclaimed = mediaCleaner.pruneExpiredMedia(maxCacheSizeBytes = 200 * 1024 * 1024L)

        // 4. Run SQLite VACUUM and WAL Checkpoint
        val walExecuted = databaseCompactor.executeWalCheckpoint()
        databaseCompactor.executeVacuum()

        val dbSizeAfter = databaseCompactor.getDatabaseSizeBytes()
        val duration = System.currentTimeMillis() - startTime

        return StoragePruningStats(
            ordersPrunedCount = prunedOrders,
            auditLogsPrunedCount = prunedLogs,
            mediaCacheBytesReclaimed = mediaReclaimed,
            databaseBytesBefore = dbSizeBefore,
            databaseBytesAfter = dbSizeAfter,
            walCheckpointExecuted = walExecuted,
            durationMillis = duration,
            trigger = trigger
        )
    }

    /**
     * Packages offline transaction audit records into an AES-256 encrypted backup bundle
     * for export to External SD card or USB-OTG drive before hardware swaps or resets.
     */
    fun exportEncryptedAuditBundle(
        terminalId: String,
        recordsJson: String,
        encryptionKeyBytes: ByteArray,
        destination: String = "EXTERNAL_SD",
        timestampMillis: Long = System.currentTimeMillis()
    ): EncryptedArchiveBundle {
        require(encryptionKeyBytes.size == 32) { "AES-256 key must be exactly 32 bytes" }

        // SHA-256 Checksum of raw records
        val md = MessageDigest.getInstance("SHA-256")
        val checksumBytes = md.digest(recordsJson.toByteArray(Charsets.UTF_8))
        val checksumHex = checksumBytes.joinToString("") { "%02x".format(it) }

        // AES-256-CBC Encryption
        val iv = ByteArray(16) { 0x42 } // Deterministic IV for archive verification
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(encryptionKeyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

        val encryptedBytes = cipher.doFinal(recordsJson.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes)

        val estimatedRecordCount = recordsJson.split("\"id\":").size - 1

        return EncryptedArchiveBundle(
            archiveId = "ARCHIVE-$terminalId-$timestampMillis",
            terminalId = terminalId,
            recordsCount = estimatedRecordCount.coerceAtLeast(1),
            payloadChecksumSha256 = checksumHex,
            encryptedPayloadBase64 = encryptedBase64,
            targetDestination = destination,
            timestampMillis = timestampMillis
        )
    }
}

class MockDatabaseCompactor(
    private var simulatedDbSize: Long = 150 * 1024 * 1024L, // 150MB initial
    var mockOrdersCount: Int = 1200,
    var mockLogsCount: Int = 300
) : DatabaseCompactor {

    override fun executeVacuum(): Long {
        simulatedDbSize = (simulatedDbSize * 0.65).toLong() // Reclaim ~35% on vacuum
        return simulatedDbSize
    }

    override fun executeWalCheckpoint(): Boolean = true

    override fun pruneOldSyncedOrders(retentionCutoffMillis: Long): Int {
        val pruned = (mockOrdersCount * 0.4).toInt()
        mockOrdersCount -= pruned
        return pruned
    }

    override fun pruneOldAuditLogs(retentionCutoffMillis: Long): Int {
        val pruned = (mockLogsCount * 0.3).toInt()
        mockLogsCount -= pruned
        return pruned
    }

    override fun getDatabaseSizeBytes(): Long = simulatedDbSize
}

class MockMediaDiskCacheCleaner(
    private var simulatedCacheSize: Long = 280 * 1024 * 1024L // 280MB initial (exceeds 200MB)
) : MediaDiskCacheCleaner {

    override fun pruneExpiredMedia(maxCacheSizeBytes: Long): Long {
        if (simulatedCacheSize > maxCacheSizeBytes) {
            val reclaimed = simulatedCacheSize - maxCacheSizeBytes
            simulatedCacheSize = maxCacheSizeBytes
            return reclaimed
        }
        return 0L
    }

    override fun getCurrentCacheSizeBytes(): Long = simulatedCacheSize
}
