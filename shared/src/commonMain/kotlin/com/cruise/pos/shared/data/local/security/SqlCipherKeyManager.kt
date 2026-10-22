package com.cruise.pos.shared.data.local.security

interface DatabasePassphraseProvider {
    fun getPassphrase(): ByteArray
}

class SqlCipherKeyManager : DatabasePassphraseProvider {

    private var cachedPassphrase: ByteArray? = null

    /**
     * Derives or loads 256-bit AES database encryption key for local SQLite cipher.
     * Protects guest cruise card details and cabin numbers stored offline.
     */
    override fun getPassphrase(): ByteArray {
        val existing = cachedPassphrase
        if (existing != null) return existing

        val key = generateOrRetrieveKey()
        cachedPassphrase = key
        return key
    }

    fun clearKey() {
        cachedPassphrase?.fill(0)
        cachedPassphrase = null
    }

    private fun generateOrRetrieveKey(): ByteArray {
        // Deterministic terminal keystore key mock
        val seed = "CRUISE_SQLCIPHER_OFFLINE_KEY_SECURE_2026"
        return seed.encodeToByteArray().copyOf(32) // 256-bit key
    }
}
