package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.BarcodeScanEvent
import com.cruise.pos.shared.domain.model.HardwareScannerConfig
import com.cruise.pos.shared.domain.model.ScanTriggerMode
import com.cruise.pos.shared.domain.model.ScannerDeviceType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    fun simulateHardwareScan(barcode: String, symbology: String = "QR_CODE", timestampMillis: Long = 0L)
}

class UnifiedHardwareScannerEngine(
    initialConfig: HardwareScannerConfig = HardwareScannerConfig(),
    private val feedbackController: HardwareFeedbackController = MockHardwareFeedbackController()
) : UnifiedBarcodeScanner {

    private val _config = MutableStateFlow(initialConfig)
    override val config: StateFlow<HardwareScannerConfig> = _config.asStateFlow()

    private val _scanEvents = MutableSharedFlow<BarcodeScanEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val scanEvents: SharedFlow<BarcodeScanEvent> = _scanEvents.asSharedFlow()

    private val lastScanState = MutableStateFlow<Pair<String, Long>?>(null)

    override fun updateConfig(newConfig: HardwareScannerConfig) {
        _config.value = newConfig
    }

    /**
     * Parses standard OEM Broadcast intents fired by physical side-triggers:
     * - Sunmi intent: "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED" ("data")
     * - Honeywell intent: "com.honeywell.decode.intent.action.SCAN_RESULT" ("data")
     * - Zebra DataWedge: "com.symbol.datawedge.ACTION_BARCODE" ("com.symbol.datawedge.data_string")
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
            val timestamp = (extrasMap["timestamp"] as? Long) ?: 0L
            handleCleanedBarcode(detectedBarcode, detectedSymbology ?: "UNKNOWN", deviceType, timestamp)
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

        var isDuplicate = false
        lastScanState.update { current ->
            if (current != null && current.first == rawBarcode && (timestampMillis - current.second) < cfg.duplicateScanCooldownMillis) {
                isDuplicate = true
                current
            } else {
                isDuplicate = false
                rawBarcode to timestampMillis
            }
        }
        if (isDuplicate) {
            return
        }

        var cleaned = rawBarcode
        cfg.prefixToStrip?.let { prefix ->
            if (cleaned.startsWith(prefix)) cleaned = cleaned.removePrefix(prefix)
        }
        cfg.suffixToStrip?.let { suffix ->
            if (cleaned.endsWith(suffix)) cleaned = cleaned.removeSuffix(suffix)
        }

        val event = BarcodeScanEvent(
            rawBarcode = cleaned,
            symbology = symbology,
            deviceType = deviceType,
            timestampMillis = timestampMillis
        )
        val delivered = _scanEvents.tryEmit(event)
        if (delivered) {
            if (cfg.isAudioBeepEnabled) {
                feedbackController.playScanSuccessBeep()
            }
            if (cfg.isHapticVibrationEnabled) {
                feedbackController.triggerHapticFeedback(50L)
            }
        }
    }

    companion object {
        const val SUNMI_ACTION_SCAN = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED"
        const val HONEYWELL_ACTION_SCAN = "com.honeywell.decode.intent.action.SCAN_RESULT"
        const val ZEBRA_ACTION_SCAN = "com.symbol.datawedge.ACTION_BARCODE"
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
