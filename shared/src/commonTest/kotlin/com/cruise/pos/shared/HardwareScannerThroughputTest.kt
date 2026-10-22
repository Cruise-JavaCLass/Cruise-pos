package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.BarcodeScanEvent
import com.cruise.pos.shared.domain.model.HardwareScannerConfig
import com.cruise.pos.shared.domain.model.ScanTriggerMode
import com.cruise.pos.shared.domain.model.ScannerDeviceType
import com.cruise.pos.shared.domain.service.MockHardwareFeedbackController
import com.cruise.pos.shared.domain.service.UnifiedHardwareScannerEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HardwareScannerThroughputTest {

    private lateinit var feedbackController: MockHardwareFeedbackController
    private lateinit var scannerEngine: UnifiedHardwareScannerEngine

    @BeforeTest
    fun setUp() {
        feedbackController = MockHardwareFeedbackController()
        scannerEngine = UnifiedHardwareScannerEngine(
            initialConfig = HardwareScannerConfig(
                enabledDeviceType = ScannerDeviceType.SUNMI_BUILTIN_LASER,
                triggerMode = ScanTriggerMode.SINGLE_KEY_PRESS,
                isAudioBeepEnabled = true,
                isHapticVibrationEnabled = true,
                duplicateScanCooldownMillis = 100L
            ),
            feedbackController = feedbackController
        )
    }

    @Test
    fun parseSunmiBroadcast_success() = runTest {
        val processed = scannerEngine.processRawBroadcastIntent(
            action = UnifiedHardwareScannerEngine.SUNMI_ACTION_SCAN,
            extrasMap = mapOf("data" to "CRUISE-GUEST-100234\n", "source_byte" to "QR_CODE")
        )

        assertTrue(processed)
        val event = scannerEngine.scanEvents.first()
        assertEquals("CRUISE-GUEST-100234", event.rawBarcode) // Suffix \n stripped
        assertEquals(ScannerDeviceType.SUNMI_BUILTIN_LASER, event.deviceType)
        assertEquals("QR_CODE", event.symbology)
        assertTrue(feedbackController.beepCount > 0)
        assertTrue(feedbackController.hapticCount > 0)
    }

    @Test
    fun parseHoneywellBroadcast_success() = runTest {
        val processed = scannerEngine.processRawBroadcastIntent(
            action = UnifiedHardwareScannerEngine.HONEYWELL_ACTION_SCAN,
            extrasMap = mapOf(
                "data" to "CRUISE-POS-ITEM-9988",
                "codeId" to "CODE128"
            )
        )

        assertTrue(processed)
        val event = scannerEngine.scanEvents.first()
        assertEquals("CRUISE-POS-ITEM-9988", event.rawBarcode)
        assertEquals(ScannerDeviceType.HONEYWELL_MOBILITY_SDK, event.deviceType)
    }

    @Test
    fun parseZebraBroadcast_success() = runTest {
        val processed = scannerEngine.processRawBroadcastIntent(
            action = UnifiedHardwareScannerEngine.ZEBRA_ACTION_SCAN,
            extrasMap = mapOf(
                "com.symbol.datawedge.data_string" to "FOLIO-TRANSACTION-7788",
                "com.symbol.datawedge.label_type" to "LABEL-TYPE-CODE128"
            )
        )

        assertTrue(processed)
        val event = scannerEngine.scanEvents.first()
        assertEquals("FOLIO-TRANSACTION-7788", event.rawBarcode)
        assertEquals(ScannerDeviceType.ZEBRA_EMDK_DATAWEDGE, event.deviceType)
        assertEquals("LABEL-TYPE-CODE128", event.symbology)
    }

    @Test
    fun duplicateCooldown_dropsDuplicateScansWithinWindow() = runTest {
        val p1 = scannerEngine.processRawBroadcastIntent(
            action = UnifiedHardwareScannerEngine.SUNMI_ACTION_SCAN,
            extrasMap = mapOf("data" to "DUPLICATE-CHECK-1")
        )
        assertTrue(p1)
        val initialBeepCount = feedbackController.beepCount

        // Immediate identical scan should be suppressed by duplicate cooldown window
        val p2 = scannerEngine.processRawBroadcastIntent(
            action = UnifiedHardwareScannerEngine.SUNMI_ACTION_SCAN,
            extrasMap = mapOf("data" to "DUPLICATE-CHECK-1")
        )
        assertTrue(p2)
        assertEquals(initialBeepCount, feedbackController.beepCount)
    }

    @Test
    fun highThroughputBenchmark_handlesRapidConsecutiveUniqueScans() = runTest {
        val scanCount = 100

        // Emit 100 distinct barcodes rapidly
        for (i in 1..scanCount) {
            val barcode = "ITEM-SERIAL-$i"
            val handled = scannerEngine.processRawBroadcastIntent(
                action = UnifiedHardwareScannerEngine.SUNMI_ACTION_SCAN,
                extrasMap = mapOf("data" to barcode)
            )
            assertTrue(handled)
        }

        assertEquals(scanCount, feedbackController.beepCount)
        assertEquals(scanCount, feedbackController.hapticCount)
    }

    @Test
    fun configUpdate_disablesAudioAndHapticFeedback() = runTest {
        scannerEngine.updateConfig(
            scannerEngine.config.value.copy(
                isAudioBeepEnabled = false,
                isHapticVibrationEnabled = false
            )
        )

        assertFalse(scannerEngine.config.value.isAudioBeepEnabled)
        assertFalse(scannerEngine.config.value.isHapticVibrationEnabled)

        val initialBeeps = feedbackController.beepCount
        val initialHaptics = feedbackController.hapticCount

        val handled = scannerEngine.processRawBroadcastIntent(
            action = UnifiedHardwareScannerEngine.SUNMI_ACTION_SCAN,
            extrasMap = mapOf("data" to "SILENT-SCAN-001")
        )
        assertTrue(handled)
        assertEquals(initialBeeps, feedbackController.beepCount)
        assertEquals(initialHaptics, feedbackController.hapticCount)
    }
}
