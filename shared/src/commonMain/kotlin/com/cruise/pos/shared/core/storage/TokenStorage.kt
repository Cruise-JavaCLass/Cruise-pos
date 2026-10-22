package com.cruise.pos.shared.core.storage

import com.russhwolf.settings.Settings

interface TokenStorage {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
    fun saveCurrentStation(stationId: String)
    fun getCurrentStation(): String?
}

class KmpTokenStorage(
    private val settings: Settings = Settings()
) : TokenStorage {

    companion object {
        private const val KEY_AUTH_TOKEN = "kmp_pos_auth_token"
        private const val KEY_STATION_ID = "kmp_pos_station_id"
    }

    override fun saveToken(token: String) {
        settings.putString(KEY_AUTH_TOKEN, token)
    }

    override fun getToken(): String? {
        val token = settings.getStringOrNull(KEY_AUTH_TOKEN)
        return if (token.isNullOrBlank()) null else token
    }

    override fun clearToken() {
        settings.remove(KEY_AUTH_TOKEN)
    }

    override fun saveCurrentStation(stationId: String) {
        settings.putString(KEY_STATION_ID, stationId)
    }

    override fun getCurrentStation(): String? {
        return settings.getStringOrNull(KEY_STATION_ID)
    }
}
