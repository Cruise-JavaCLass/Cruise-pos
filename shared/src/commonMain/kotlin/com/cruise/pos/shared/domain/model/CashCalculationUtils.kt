package com.cruise.pos.shared.domain.model

object CashCalculationUtils {

    /**
     * Standard currency denominations in USD (common cruise ship currency)
     */
    val STANDARD_USD_DENOMINATIONS = listOf(100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.50, 0.25, 0.10, 0.05, 0.01)

    /**
     * Calculates the total sum from a list of cash denominations.
     */
    fun calculateTotal(denominations: List<CashDenomination>): Double {
        val sum = denominations.sumOf { it.value * it.count }
        return roundToTwoDecimals(sum)
    }

    /**
     * Calculates reconciliation difference between actual counted cash and expected cash.
     * Positive value = Over (surplus)
     * Negative value = Short (deficit)
     * Zero = Perfect match
     */
    fun calculateDiscrepancy(actualCounted: Double, expected: Double): Double {
        return roundToTwoDecimals(actualCounted - expected)
    }

    /**
     * Checks if starting float is valid (> 0 and within reasonable ceiling limit)
     */
    fun isValidStartingFloat(amount: Double, maxAllowedFloat: Double = 10000.0): Boolean {
        return amount >= 0.0 && amount <= maxAllowedFloat
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }
}
