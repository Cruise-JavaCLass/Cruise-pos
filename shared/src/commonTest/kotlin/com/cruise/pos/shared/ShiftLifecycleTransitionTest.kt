package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.data.repository.ShiftRepositoryImpl
import com.cruise.pos.shared.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShiftLifecycleTransitionTest {

    @Test
    fun testShiftOpenAndCloseLifecycle() = runTest {
        val repo = ShiftRepositoryImpl()

        // 1. Initially no shift
        assertNull(repo.currentShift.value)

        // 2. Open Shift with invalid float (< 0)
        val invalidOpen = repo.openShift(
            OpenShiftRequest(
                cashierId = 1,
                stationId = "STATION_BAR_A",
                startingFloat = -50.0
            )
        )
        assertTrue(invalidOpen is ApiResult.Error)
        assertEquals(400, (invalidOpen as ApiResult.Error).code)

        // 3. Open Shift with valid float
        val openResult = repo.openShift(
            OpenShiftRequest(
                cashierId = 1,
                stationId = "STATION_BAR_A",
                startingFloat = 200.0,
                denominations = listOf(
                    CashDenomination(100.0, 1),
                    CashDenomination(50.0, 2)
                )
            )
        )
        assertTrue(openResult is ApiResult.Success)
        val shift = (openResult as ApiResult.Success).data
        assertEquals(ShiftStatus.OPEN, shift.status)
        assertEquals(200.0, shift.startingFloat)
        assertEquals(shift, repo.currentShift.value)

        // 4. Query Active Shift
        val activeResult = repo.getCurrentActiveShift(1L)
        assertTrue(activeResult is ApiResult.Success)
        assertEquals("STATION_BAR_A", (activeResult as ApiResult.Success).data?.stationId)

        // 5. Close Shift with reconciliation
        val closeResult = repo.closeShift(
            CloseShiftRequest(
                shiftId = shift.id,
                actualCountedCash = 550.0,
                closingNote = "Normal balanced closing"
            )
        )
        assertTrue(closeResult is ApiResult.Success)
        val closedShift = (closeResult as ApiResult.Success).data
        assertEquals(ShiftStatus.CLOSED, closedShift.status)
        assertEquals(550.0, closedShift.actualCountedCash)
        assertEquals(0.0, closedShift.cashDifference) // 200 + 350 expected = 550
        assertNull(repo.currentShift.value)
    }
}
