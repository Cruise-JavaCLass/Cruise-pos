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
     * Calculates expected drawer cash after taking mid-shift cash drops into account:
     * Current Drawer Cash = Starting Float + Cash Sales - Total Cash Drops
     */
    fun calculateExpectedCash(startingFloat: Double, cashSales: Double, totalCashDrops: Double = 0.0): Double {
        val expected = startingFloat + cashSales - totalCashDrops
        return roundToTwoDecimals(expected)
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
     * Validates whether a proposed cash drop amount is legal (positive and not exceeding cash in drawer)
     */
    fun isValidCashDrop(amount: Double, currentDrawerCash: Double): Boolean {
        return amount > 0.0 && amount <= currentDrawerCash
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