package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.MaintenanceTrigger
import com.cruise.pos.shared.domain.service.MockDatabaseCompactor
import com.cruise.pos.shared.domain.service.MockMediaDiskCacheCleaner
import com.cruise.pos.shared.domain.service.StorageMaintenanceLifecycleManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StoragePruningAndCompactionTest {

    private val compactor = MockDatabaseCompactor()
    private val mediaCleaner = MockMediaDiskCacheCleaner()
    private val manager = StorageMaintenanceLifecycleManager(compactor, mediaCleaner)

    private val currentTimestampMillis = 1792677600000L // 2026-10-22

    @Test
    fun testPerformStorageCompactionReclaimsSpaceAndVacuum() {
        val stats = manager.performStorageCompaction(
            currentTimestampMillis = currentTimestampMillis,
            retentionDays = 7,
            trigger = MaintenanceTrigger.CHARGING_IDLE_CRADLE
        )

        assertEquals(450, stats.ordersPrunedCount)
        assertEquals(85, stats.auditLogsPrunedCount)
        assertTrue(stats.mediaCacheBytesReclaimed > 0) // Cleaned down to 200MB limit
        assertEquals(80 * 1024 * 1024L, stats.mediaCacheBytesReclaimed) // 280MB - 200MB = 80MB
        assertTrue(stats.databaseBytesBefore > stats.databaseBytesAfter)
        assertTrue(stats.walCheckpointExecuted)
        assertEquals(MaintenanceTrigger.CHARGING_IDLE_CRADLE, stats.trigger)
    }

    @Test
    fun testExportEncryptedAuditBundleAES256() {
        val sampleRecordsJson = """
            [
                {"id":"ORD-101","passengerId":"P-1001","amount":45.0,"status":"SETTLED"},
                {"id":"ORD-102","passengerId":"P-1002","amount":18.5,"status":"SETTLED"}
            ]
        """.trimIndent()

        val sampleKey = ByteArray(32) { 0x07 } // 256-bit AES key

        val bundle = manager.exportEncryptedAuditBundle(
            terminalId = "TERM-POS-04",
            recordsJson = sampleRecordsJson,
            encryptionKeyBytes = sampleKey,
            destination = "USB_OTG",
            timestampMillis = currentTimestampMillis
        )

        assertNotNull(bundle.archiveId)
        assertEquals("TERM-POS-04", bundle.terminalId)
        assertEquals(2, bundle.recordsCount)
        assertEquals(64, bundle.payloadChecksumSha256.length) // SHA-256
        assertEquals("USB_OTG", bundle.targetDestination)
        assertTrue(bundle.encryptedPayloadBase64.isNotEmpty())

        // Verify decryption roundtrip
        val iv = ByteArray(16) { 0x42 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(sampleKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

        val encryptedBytes = Base64.getDecoder().decode(bundle.encryptedPayloadBase64)
        val decryptedJson = String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        assertEquals(sampleRecordsJson, decryptedJson)
    }

    @Test
    fun testExportEncryptedAuditBundleEmptyRecordsCount() {
        val bundle = manager.exportEncryptedAuditBundle(
            terminalId = "TERM-POS-04",
            recordsJson = "",
            encryptionKeyBytes = ByteArray(32) { 0x07 },
            destination = "USB_OTG",
            timestampMillis = currentTimestampMillis
        )
        assertEquals(0, bundle.recordsCount)
    }
}
