package com.cruise.pos.shared.data.sync

import kotlin.math.min
import kotlin.math.pow

data class ExponentialBackoffPolicy(
    val initialDelayMs: Long = 10_000L,     // 10 seconds base
    val multiplier: Double = 2.0,            // 2x backoff
    val maxDelayMs: Long = 300_000L,        // Max 5 minutes cap
    val maxRetryAttempts: Int = 5
) {
    /**
     * Calculates backoff duration for the given 1-based attempt index.
     * attempt 1: 10s
     * attempt 2: 20s
     * attempt 3: 40s
     * attempt 4: 80s
     * attempt 5: 160s
     */
    fun calculateDelayMs(attemptNumber: Int): Long {
        if (attemptNumber <= 1) return initialDelayMs
        val factor = multiplier.pow((attemptNumber - 1).toDouble())
        val calculated = (initialDelayMs * factor).toLong()
        return min(calculated, maxDelayMs)
    }

    fun hasExceededMaxRetries(attemptNumber: Int): Boolean {
        return attemptNumber >= maxRetryAttempts
    }
}
