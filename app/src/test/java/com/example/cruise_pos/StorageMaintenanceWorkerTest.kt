package com.example.cruise_pos

import com.cruise.pos.shared.domain.model.MaintenanceTrigger
import com.cruise.pos.shared.domain.service.MockDatabaseCompactor
import com.cruise.pos.shared.domain.service.MockMediaDiskCacheCleaner
import com.cruise.pos.shared.domain.service.StorageMaintenanceLifecycleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StorageMaintenanceWorkerTest {

    private lateinit var compactor: MockDatabaseCompactor
    private lateinit var mediaCleaner: MockMediaDiskCacheCleaner
    private lateinit var manager: StorageMaintenanceLifecycleManager

    @Before
    fun setUp() {
        compactor = MockDatabaseCompactor(simulatedDbSize = 100 * 1024 * 1024L, mockOrdersCount = 500, mockLogsCount = 200)
        mediaCleaner = MockMediaDiskCacheCleaner(simulatedCacheSize = 250 * 1024 * 1024L)
        manager = StorageMaintenanceLifecycleManager(compactor, mediaCleaner)
    }

    @Test
    fun testRoomDbPruningScheduledMaintenance() {
        val stats = manager.performStorageCompaction(
            currentTimestampMillis = 1792677600000L,
            retentionDays = 7,
            trigger = MaintenanceTrigger.SCHEDULED_PERIODIC
        )

        assertEquals(200, stats.ordersPrunedCount) // 40% of 500
        assertEquals(60, stats.auditLogsPrunedCount) // 30% of 200
        assertEquals(50 * 1024 * 1024L, stats.mediaCacheBytesReclaimed) // 250MB - 200MB
        assertTrue(stats.databaseBytesBefore > stats.databaseBytesAfter)
    }

    @Test
    fun testEncryptedArchiveBundleExport() {
        val sampleRecordsJson = """[{"id":"TX-909","passengerId":"P-99","amount":120.0}]"""
        val key = ByteArray(32) { 0x0A }

        val bundle = manager.exportEncryptedAuditBundle(
            terminalId = "TERM-01",
            recordsJson = sampleRecordsJson,
            encryptionKeyBytes = key,
            destination = "EXTERNAL_SD"
        )

        assertNotNull(bundle.archiveId)
        assertEquals("TERM-01", bundle.terminalId)
        assertEquals("EXTERNAL_SD", bundle.targetDestination)
        assertTrue(bundle.encryptedPayloadBase64.isNotBlank())
    }
}
