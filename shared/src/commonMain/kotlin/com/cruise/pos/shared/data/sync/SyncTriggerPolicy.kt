package com.cruise.pos.shared.data.sync

enum class NetworkConstraintType {
    CONNECTED,
    UNMETERED_WIFI,
    NOT_ROAMING
}

data class SyncTriggerPolicy(
    val periodicIntervalMinutes: Long = 15L,
    val requiresUnmeteredWifi: Boolean = true,
    val requiresBatteryNotLow: Boolean = true,
    val requiresDeviceIdle: Boolean = false,
    val networkConstraint: NetworkConstraintType = NetworkConstraintType.UNMETERED_WIFI
) {
    fun canExecuteSync(
        isWifiConnected: Boolean,
        isBatteryOk: Boolean,
        isDeviceIdle: Boolean
    ): Boolean {
        if (requiresUnmeteredWifi && !isWifiConnected) return false
        if (requiresBatteryNotLow && !isBatteryOk) return false
        if (requiresDeviceIdle && !isDeviceIdle) return false
        return true
    }
}
