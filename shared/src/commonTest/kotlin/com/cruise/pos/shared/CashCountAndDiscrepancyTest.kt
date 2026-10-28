package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.CashCalculationUtils
import com.cruise.pos.shared.domain.model.CashDenomination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CashCountAndDiscrepancyTest {

    @Test
    fun testCashDenominationTotal() {
        val denom100 = CashDenomination(value = 100.0, count = 5)
        assertEquals(500.0, denom100.total)

        val denom20 = CashDenomination(value = 20.0, count = 15)
        assertEquals(300.0, denom20.total)

        val denomQuarter = CashDenomination(value = 0.25, count = 8)
        assertEquals(2.0, denomQuarter.total)
    }

    @Test
    fun testCalculateTotalSum() {
        val denominations = listOf(
            CashDenomination(100.0, 2), // 200.0
            CashDenomination(50.0, 4),  // 200.0
            CashDenomination(20.0, 5),  // 100.0
            CashDenomination(10.0, 10), // 100.0
            CashDenomination(1.0, 25),  // 25.0
            CashDenomination(0.25, 4)   // 1.0
        )
        val total = CashCalculationUtils.calculateTotal(denominations)
        assertEquals(626.0, total)
    }

    @Test
    fun testCalculateDiscrepancyExactMatch() {
        val discrepancy = CashCalculationUtils.calculateDiscrepancy(actualCounted = 1500.0, expected = 1500.0)
        assertEquals(0.0, discrepancy)
    }

    @Test
    fun testCalculateDiscrepancyOverCashDrawer() {
        val discrepancy = CashCalculationUtils.calculateDiscrepancy(actualCounted = 1550.50, expected = 1500.0)
        assertEquals(50.50, discrepancy)
    }

    @Test
    fun testCalculateDiscrepancyShortCashDrawer() {
        val discrepancy = CashCalculationUtils.calculateDiscrepancy(actualCounted = 1480.25, expected = 1500.0)
        assertEquals(-19.75, discrepancy)
    }

    @Test
    fun testValidStartingFloat() {
        assertTrue(CashCalculationUtils.isValidStartingFloat(0.0))
        assertTrue(CashCalculationUtils.isValidStartingFloat(500.0))
        assertTrue(CashCalculationUtils.isValidStartingFloat(10000.0))
        assertFalse(CashCalculationUtils.isValidStartingFloat(-1.0))
        assertFalse(CashCalculationUtils.isValidStartingFloat(10000.01))
    }

    @Test
    fun testValidCashDrop() {
        assertTrue(CashCalculationUtils.isValidCashDrop(50.0, 100.0))
        assertTrue(CashCalculationUtils.isValidCashDrop(100.0, 100.0))
        assertFalse(CashCalculationUtils.isValidCashDrop(0.0, 100.0))
        assertFalse(CashCalculationUtils.isValidCashDrop(-10.0, 100.0))
        assertFalse(CashCalculationUtils.isValidCashDrop(150.0, 100.0))
    }
}
