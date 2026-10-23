package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.data.repository.ShiftRepositoryImpl
import com.cruise.pos.shared.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CashDropSharedTest {

    @Test
    fun testCashCalculationUtils_expectedCashWithDrops() {
        val startingFloat = 300.0
        val cashSales = 1200.0
        val cashDrops = 500.0

        val expected = CashCalculationUtils.calculateExpectedCash(startingFloat, cashSales, cashDrops)
        assertEquals(1000.0, expected)

        val totalFromDenoms = CashCalculationUtils.calculateTotal(
            listOf(
                CashDenomination(100.0, 3),
                CashDenomination(50.0, 4)
            )
        )
        assertEquals(500.0, totalFromDenoms)

        val discrepancy = CashCalculationUtils.calculateDiscrepancy(1000.0, expected)
        assertEquals(0.0, discrepancy)
    }

    @Test
    fun testRecordCashDrop_deductsFromShiftAndAppendsHistory() = runTest {
        val repo = ShiftRepositoryImpl()

        val initialShift = ShiftRecord(
            id = "shift-001",
            cashierId = 42L,
            cashierName = "Roving Bartender",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 200.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
        repo.setCurrentShift(initialShift)

        val request = CashDropRequest(
            shiftId = "shift-001",
            amount = 400.0,
            cashierId = "42",
            witnessId = "MGR-01",
            witnessPin = "8888",
            denominations = listOf(
                CashDenomination(100.0, 4)
            ),
            notes = "Limit reached"
        )

        val result = repo.recordCashDrop(request)
        assertTrue(result is ApiResult.Success)

        val updatedShift = repo.currentShift.value
        assertNotNull(updatedShift)
        assertEquals(400.0, updatedShift.totalCashDrops)
        assertEquals(1, updatedShift.cashDropHistory.size)
        assertEquals(400.0, updatedShift.cashDropHistory[0].amount)
        assertEquals("MGR-01", updatedShift.cashDropHistory[0].witnessId)
    }

    @Test
    fun testRecordCashDrop_closedShiftReturnsError() = runTest {
        val repo = ShiftRepositoryImpl()

        val closedShift = ShiftRecord(
            id = "shift-002",
            cashierId = 42L,
            cashierName = "Roving Bartender",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.CLOSED,
            startingFloat = 200.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
        repo.setCurrentShift(closedShift)

        val request = CashDropRequest(
            shiftId = "shift-002",
            amount = 100.0,
            cashierId = "42",
            witnessId = "MGR-01",
            witnessPin = "8888"
        )

        val result = repo.recordCashDrop(request)
        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).code)
    }
    @Test
    fun testCashCalculationUtils_expectedCashNegativeDeficitPreserved() {
        val startingFloat = 200.0
        val cashSales = 100.0
        val totalCashDrops = 400.0

        val expected = CashCalculationUtils.calculateExpectedCash(startingFloat, cashSales, totalCashDrops)
        assertEquals(-100.0, expected)

        val discrepancy = CashCalculationUtils.calculateDiscrepancy(0.0, expected)
        assertEquals(100.0, discrepancy)
    }

    @Test
    fun testRecordCashDrop_rejectsNonFiniteAmount() = runTest {
        val repo = ShiftRepositoryImpl()
        val initialShift = ShiftRecord(
            id = "shift-003",
            cashierId = 42L,
            cashierName = "Roving Bartender",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 200.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
        repo.setCurrentShift(initialShift)

        val request = CashDropRequest(
            shiftId = "shift-003",
            amount = Double.NaN,
            cashierId = "42",
            witnessId = "MGR-01",
            witnessPin = "8888"
        )

        val result = repo.recordCashDrop(request)
        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).code)
    }

    @Test
    fun testRecordCashDrop_rejectsMismatchedShiftId() = runTest {
        val repo = ShiftRepositoryImpl()
        val initialShift = ShiftRecord(
            id = "shift-004",
            cashierId = 42L,
            cashierName = "Roving Bartender",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 200.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
        repo.setCurrentShift(initialShift)

        val request = CashDropRequest(
            shiftId = "shift-OTHER",
            amount = 100.0,
            cashierId = "42",
            witnessId = "MGR-01",
            witnessPin = "8888"
        )

        val result = repo.recordCashDrop(request)
        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).code)
    }
    @Test
    fun testRecordCashDrop_rejectsMatchingWitnessAndCashier() = runTest {
        val repo = ShiftRepositoryImpl()
        val initialShift = ShiftRecord(
            id = "shift-005",
            cashierId = 42L,
            cashierName = "Roving Bartender",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 200.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
        repo.setCurrentShift(initialShift)

        val request = CashDropRequest(
            shiftId = "shift-005",
            amount = 100.0,
            cashierId = "42",
            witnessId = "42",
            witnessPin = "8888"
        )

        val result = repo.recordCashDrop(request)
        assertTrue(result is ApiResult.Error)
        assertEquals(403, (result as ApiResult.Error).code)
    }

    @Test
    fun testRecordCashDrop_rejectsInvalidWitnessPin() = runTest {
        val repo = ShiftRepositoryImpl()
        val initialShift = ShiftRecord(
            id = "shift-006",
            cashierId = 42L,
            cashierName = "Roving Bartender",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 200.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
        repo.setCurrentShift(initialShift)

        val request = CashDropRequest(
            shiftId = "shift-006",
            amount = 100.0,
            cashierId = "42",
            witnessId = "MGR-02",
            witnessPin = "12"
        )

        val result = repo.recordCashDrop(request)
        assertTrue(result is ApiResult.Error)
        assertEquals(403, (result as ApiResult.Error).code)
    }
}