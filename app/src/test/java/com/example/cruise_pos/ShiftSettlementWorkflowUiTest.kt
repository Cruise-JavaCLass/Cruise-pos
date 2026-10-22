package com.example.cruise_pos

import com.cruise.pos.shared.domain.model.BlindDropDeclaration
import com.cruise.pos.shared.domain.model.PaymentMethodBreakdown
import com.cruise.pos.shared.domain.model.PaymentMethodType
import com.cruise.pos.shared.domain.service.MockBackendAccountingLedgerClient
import com.cruise.pos.shared.domain.service.ShiftSettlementService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShiftSettlementWorkflowUiTest {

    private lateinit var ledgerClient: MockBackendAccountingLedgerClient
    private lateinit var settlementService: ShiftSettlementService

    @Before
    fun setUp() {
        ledgerClient = MockBackendAccountingLedgerClient()
        settlementService = ShiftSettlementService(ledgerClient)
    }

    @Test
    fun testEndToEndShiftClosureAndSettlementFlow() = runTest {
        // Step 1: Cashier reviews live X-Report
        val breakdowns = listOf(
            PaymentMethodBreakdown(PaymentMethodType.CASH, transactionCount = 8, grossSales = 160.0),
            PaymentMethodBreakdown(PaymentMethodType.CABIN_FOLIO, transactionCount = 12, grossSales = 340.0)
        )
        val xReport = settlementService.generateXReport(
            shiftId = "SHIFT-MAIN-01",
            stationId = "STATION-LIDO",
            cashierId = "USR-55",
            cashierName = "Carlos Mendez",
            openedAtMillis = 1000L,
            startingCashFloat = 100.0,
            totalCashDrops = 50.0,
            paymentBreakdowns = breakdowns
        )

        assertEquals(500.0, xReport.totalNetRevenue, 0.001)
        // Expected cash: 100 (float) + 160 (cash sales) - 50 (drop) = 210.0
        assertEquals(210.0, xReport.expectedCashInDrawer, 0.001)

        // Step 2: Cashier conducts Blind Cash Count
        val blindDrop = BlindDropDeclaration(
            countedCashAmount = 210.0,
            witnessCashierName = "Sarah Connor",
            notes = "Evening shift closing"
        )

        // Step 3: Final Z-Report generated
        val zReport = settlementService.generateFinalZReport(
            xReport = xReport,
            blindDrop = blindDrop,
            closedAtMillis = 5000L
        )

        assertTrue(zReport.isBalanced)
        assertEquals(0.0, zReport.cashDiscrepancy, 0.001)
        assertTrue(zReport.isOverShortApproved)

        // Step 4: Receipt printing format validation
        val receiptText = settlementService.renderSettlementReceiptText(zReport)
        assertTrue(receiptText.contains("FINAL SHIFT Z-REPORT"))
        assertTrue(receiptText.contains("Carlos Mendez"))
        assertTrue(receiptText.contains("BALANCED (0.00)"))

        // Step 5: Sync to backend accounting ledger
        val synced = settlementService.transmitToAccountingLedger(zReport)
        assertEquals("SYNCED", synced.backendLedgerSyncStatus)
        assertEquals(1, ledgerClient.syncedReports.size)
    }

    @Test
    fun testDiscrepancyVarianceWithShortageCalculation() {
        val breakdowns = listOf(
            PaymentMethodBreakdown(PaymentMethodType.CASH, transactionCount = 4, grossSales = 100.0)
        )
        val xReport = settlementService.generateXReport(
            shiftId = "SHIFT-BAR-09",
            stationId = "STATION-PROMENADE",
            cashierId = "USR-10",
            cashierName = "Jane Doe",
            openedAtMillis = 1000L,
            startingCashFloat = 50.0,
            totalCashDrops = 0.0,
            paymentBreakdowns = breakdowns
        )
        // Expected cash: 150.0

        // Actual counted cash: 135.0 (Shortage of -$15.0)
        val zReport = settlementService.generateFinalZReport(
            xReport = xReport,
            blindDrop = BlindDropDeclaration(135.0),
            closedAtMillis = 6000L,
            supervisorPin = "5544"
        )

        assertFalse(zReport.isBalanced)
        assertEquals(-15.0, zReport.cashDiscrepancy, 0.001)
        assertTrue(zReport.isOverShortApproved)
        assertEquals("5544", zReport.supervisorOverridePin)
    }
}
