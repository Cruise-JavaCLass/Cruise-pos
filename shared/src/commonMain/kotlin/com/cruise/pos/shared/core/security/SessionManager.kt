package com.cruise.pos.shared.core.security

import com.russhwolf.settings.Settings

interface SessionManager {
    fun saveSession(token: String, cashierId: Long, username: String, role: String, stationId: String)
    fun getJwtToken(): String?
    fun getCashierId(): Long?
    fun getCashierUsername(): String?
    fun getCashierRole(): String?
    fun getAssignedStationId(): String?
    fun saveAssignedStationId(stationId: String)
    fun isSessionActive(): Boolean
    fun clearSession()
}

class KmpSessionManager(
    private val settings: Settings = Settings()
) : SessionManager {

    companion object {
        private const val KEY_JWT_TOKEN = "kmp_pos_jwt_token"
        private const val KEY_CASHIER_ID = "kmp_pos_cashier_id"
        private const val KEY_CASHIER_USER = "kmp_pos_cashier_user"
        private const val KEY_CASHIER_ROLE = "kmp_pos_cashier_role"
        private const val KEY_STATION_ID = "kmp_pos_station_id"
    }

    override fun saveSession(
        token: String,
        cashierId: Long,
        username: String,
        role: String,
        stationId: String
    ) {
        settings.putString(KEY_JWT_TOKEN, token)
        settings.putLong(KEY_CASHIER_ID, cashierId)
        settings.putString(KEY_CASHIER_USER, username)
        settings.putString(KEY_CASHIER_ROLE, role)
        settings.putString(KEY_STATION_ID, stationId)
    }

    override fun getJwtToken(): String? = settings.getStringOrNull(KEY_JWT_TOKEN)?.takeIf { it.isNotBlank() }

    override fun getCashierId(): Long? {
        val id = settings.getLong(KEY_CASHIER_ID, -1L)
        return if (id == -1L) null else id
    }

    override fun getCashierUsername(): String? = settings.getStringOrNull(KEY_CASHIER_USER)

    override fun getCashierRole(): String? = settings.getStringOrNull(KEY_CASHIER_ROLE)

    override fun getAssignedStationId(): String? = settings.getStringOrNull(KEY_STATION_ID)

    override fun saveAssignedStationId(stationId: String) {
        settings.putString(KEY_STATION_ID, stationId)
    }

    override fun isSessionActive(): Boolean = !getJwtToken().isNullOrBlank()

    override fun clearSession() {
        settings.remove(KEY_JWT_TOKEN)
        settings.remove(KEY_CASHIER_ID)
        settings.remove(KEY_CASHIER_USER)
        settings.remove(KEY_CASHIER_ROLE)
    }
}
