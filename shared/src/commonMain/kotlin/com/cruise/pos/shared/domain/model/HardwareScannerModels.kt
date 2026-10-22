package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScannerDeviceType {
    SUNMI_BUILTIN_LASER,
    HONEYWELL_MOBILITY_SDK,
    ZEBRA_EMDK_DATAWEDGE,
    GENERIC_KEYSTROKE_WEDGE,
    CAMERAX_SOFTWARE_FALLBACK
}

@Serializable
enum class ScanTriggerMode {
    SINGLE_KEY_PRESS,
    CONTINUOUS_PRESENTATION,
    AIM_AND_RELEASE
}

@Serializable
data class BarcodeScanEvent(
    val rawBarcode: String,
    val symbology: String = "QR_CODE", // EAN_13, CODE_128, QR_CODE
    val deviceType: ScannerDeviceType,
    val timestampMillis: Long = 0L,
    val scanDurationMillis: Long = 0L
)

@Serializable
data class HardwareScannerConfig(
    val enabledDeviceType: ScannerDeviceType = ScannerDeviceType.SUNMI_BUILTIN_LASER,
    val triggerMode: ScanTriggerMode = ScanTriggerMode.SINGLE_KEY_PRESS,
    val isAudioBeepEnabled: Boolean = true,
    val isHapticVibrationEnabled: Boolean = true,
    val illuminationLaserLevel: Int = 5, // 1 to 10
    val prefixToStrip: String? = null,
    val suffixToStrip: String? = "\n",
    val duplicateScanCooldownMillis: Long = 300L
)
