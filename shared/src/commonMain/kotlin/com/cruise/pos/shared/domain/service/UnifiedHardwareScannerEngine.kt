package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.BarcodeScanEvent
import com.cruise.pos.shared.domain.model.HardwareScannerConfig
import com.cruise.pos.shared.domain.model.ScanTriggerMode
import com.cruise.pos.shared.domain.model.ScannerDeviceType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

interface HardwareFeedbackController {
    fun playScanSuccessBeep()
    fun playScanErrorTone()
    fun triggerHapticFeedback(durationMillis: Long = 50L)
}

interface UnifiedBarcodeScanner {
    val scanEvents: SharedFlow<BarcodeScanEvent>
    val config: StateFlow<HardwareScannerConfig>
    fun updateConfig(newConfig: HardwareScannerConfig)
    fun processRawBroadcastIntent(action: String, extrasMap: Map<String, Any?>): Boolean
    fun simulateHardwareScan(barcode: String, symbology: String = "QR_CODE", timestampMillis: Long = System.currentTimeMillis())
}

class UnifiedHardwareScannerEngine(
    initialConfig: HardwareScannerConfig = HardwareScannerConfig(),
    private val feedbackController: HardwareFeedbackController = MockHardwareFeedbackController()
) : UnifiedBarcodeScanner {

    private val _config = MutableStateFlow(initialConfig)
    override val config: StateFlow<HardwareScannerConfig> = _config.asStateFlow()

    private val _scanEvents = MutableSharedFlow<BarcodeScanEvent>(
        replay = 10,
        extraBufferCapacity = 256
    )
    override val scanEvents: SharedFlow<BarcodeScanEvent> = _scanEvents.asSharedFlow()

    private var lastScannedBarcode: String? = null
    private var lastScanTimestamp: Long = 0L

    override fun updateConfig(newConfig: HardwareScannerConfig) {
        _config.value = newConfig
    }

    /**
     * Parses standard OEM Broadcast intents fired by physical side-triggers:
     * - Sunmi intent: "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED" ("data")
     * - Honeywell intent: "com.honeywell.decode.intent.action.SCAN_RESULT" ("data")
     * - Zebra DataWedge: "com.symbol.datawedge.api.ACTION_SOFTSCANTRIGGER" ("com.symbol.datawedge.data_string")
     */
    override fun processRawBroadcastIntent(action: String, extrasMap: Map<String, Any?>): Boolean {
        val detectedBarcode: String?
        val detectedSymbology: String?
        val deviceType: ScannerDeviceType

        when (action) {
            SUNMI_ACTION_SCAN -> {
                detectedBarcode = extrasMap["data"] as? String
                detectedSymbology = extrasMap["source_byte"]?.toString() ?: "SUNMI_CODE"
                deviceType = ScannerDeviceType.SUNMI_BUILTIN_LASER
            }
            HONEYWELL_ACTION_SCAN -> {
                detectedBarcode = extrasMap["data"] as? String
                detectedSymbology = extrasMap["codeId"] as? String ?: "HONEYWELL_CODE"
                deviceType = ScannerDeviceType.HONEYWELL_MOBILITY_SDK
            }
            ZEBRA_ACTION_SCAN -> {
                detectedBarcode = extrasMap["com.symbol.datawedge.data_string"] as? String
                detectedSymbology = extrasMap["com.symbol.datawedge.label_type"] as? String ?: "ZEBRA_CODE"
                deviceType = ScannerDeviceType.ZEBRA_EMDK_DATAWEDGE
            }
            else -> return false
        }

        if (!detectedBarcode.isNullOrBlank()) {
            handleCleanedBarcode(detectedBarcode, detectedSymbology ?: "UNKNOWN", deviceType, System.currentTimeMillis())
            return true
        }
        return false
    }

    override fun simulateHardwareScan(barcode: String, symbology: String, timestampMillis: Long) {
        handleCleanedBarcode(barcode, symbology, _config.value.enabledDeviceType, timestampMillis)
    }

    private fun handleCleanedBarcode(
        rawBarcode: String,
        symbology: String,
        deviceType: ScannerDeviceType,
        timestampMillis: Long
    ) {
        val cfg = _config.value

        // Check duplicate scan cooldown
        if (rawBarcode == lastScannedBarcode && (timestampMillis - lastScanTimestamp) < cfg.duplicateScanCooldownMillis) {
            return // Ignored duplicate bounce
        }

        var cleaned = rawBarcode
        cfg.prefixToStrip?.let { prefix ->
            if (cleaned.startsWith(prefix)) cleaned = cleaned.removePrefix(prefix)
        }
        cfg.suffixToStrip?.let { suffix ->
            if (cleaned.endsWith(suffix)) cleaned = cleaned.removeSuffix(suffix)
        }

        lastScannedBarcode = rawBarcode
        lastScanTimestamp = timestampMillis

        // Feedback
        if (cfg.isAudioBeepEnabled) {
            feedbackController.playScanSuccessBeep()
        }
        if (cfg.isHapticVibrationEnabled) {
            feedbackController.triggerHapticFeedback(50L)
        }

        val event = BarcodeScanEvent(
            rawBarcode = cleaned,
            symbology = symbology,
            deviceType = deviceType,
            timestampMillis = timestampMillis
        )
        _scanEvents.tryEmit(event)
    }

    companion object {
        const val SUNMI_ACTION_SCAN = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED"
        const val HONEYWELL_ACTION_SCAN = "com.honeywell.decode.intent.action.SCAN_RESULT"
        const val ZEBRA_ACTION_SCAN = "com.symbol.datawedge.api.ACTION_SOFTSCANTRIGGER"
    }
}

class MockHardwareFeedbackController : HardwareFeedbackController {
    var beepCount: Int = 0
    var errorToneCount: Int = 0
    var hapticCount: Int = 0

    override fun playScanSuccessBeep() {
        beepCount++
    }

    override fun playScanErrorTone() {
        errorToneCount++
    }

    override fun triggerHapticFeedback(durationMillis: Long) {
        hapticCount++
    }
}
