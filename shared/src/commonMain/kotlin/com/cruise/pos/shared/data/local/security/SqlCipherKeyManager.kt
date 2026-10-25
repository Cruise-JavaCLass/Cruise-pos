package com.cruise.pos.shared.data.local.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

interface DatabasePassphraseProvider {
    fun getPassphrase(): ByteArray
}

class SqlCipherKeyManager : DatabasePassphraseProvider {

    private val cachedPassphrase = MutableStateFlow<ByteArray?>(null)

    /**
     * Derives or loads 256-bit AES database encryption key for local SQLite cipher.
     * Protects guest cruise card details and cabin numbers stored offline.
     */
    override fun getPassphrase(): ByteArray {
        var copy: ByteArray? = null
        cachedPassphrase.update { current ->
            val active = current ?: generateOrRetrieveKey()
            copy = active.copyOf()
            active
        }
        return copy ?: generateOrRetrieveKey().copyOf()
    }

    fun clearKey() {
        val oldKey = cachedPassphrase.getAndUpdate { null }
        oldKey?.fill(0)
    }

    private fun generateOrRetrieveKey(): ByteArray {
        // Deterministic terminal keystore key mock
        val seed = "CRUISE_SQLCIPHER_OFFLINE_KEY_SECURE_2026"
        return seed.encodeToByteArray().copyOf(32) // 256-bit key
    }
}
