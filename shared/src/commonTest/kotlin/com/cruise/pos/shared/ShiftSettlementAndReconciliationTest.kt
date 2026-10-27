package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.BlindDropDeclaration
import com.cruise.pos.shared.domain.model.LedgerSyncStatus
import com.cruise.pos.shared.domain.model.PaymentMethodBreakdown
import com.cruise.pos.shared.domain.model.PaymentMethodType
import com.cruise.pos.shared.domain.service.MockBackendAccountingLedgerClient
import com.cruise.pos.shared.domain.service.ShiftSettlementService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShiftSettlementAndReconciliationTest {

    private val ledgerClient = MockBackendAccountingLedgerClient()
    private val service = ShiftSettlementService(ledgerClient)

    @Test
    fun testGenerateXReportRevenueAggregation() {
        val breakdowns = listOf(
            PaymentMethodBreakdown(PaymentMethodType.CASH, transactionCount = 10, grossSales = 250.0, refunds = 0.0),
            PaymentMethodBreakdown(PaymentMethodType.CREDIT_CARD, transactionCount = 25, grossSales = 800.0, refunds = 50.0),
            PaymentMethodBreakdown(PaymentMethodType.CABIN_FOLIO, transactionCount = 40, grossSales = 1200.0, refunds = 0.0)
        )

        val xReport = service.generateXReport(
            shiftId = "SHIFT-POOL-01",
            stationId = "STATION-LIDO-POOL",
            cashierId = "USR-77",
            cashierName = "Lucas Vance",
            openedAtMillis = 1000L,
            startingCashFloat = 200.0,
            totalCashDrops = 150.0,
            paymentBreakdowns = breakdowns,
            currentTimestampMillis = 2000L
        )

        assertEquals("SHIFT-POOL-01", xReport.shiftId)
        assertEquals(75, xReport.totalOrdersCount)
        assertEquals(2250.0, xReport.totalGrossSales)
        assertEquals(50.0, xReport.totalRefunds)
        assertEquals(2200.0, xReport.totalNetRevenue)
        assertEquals(250.0, xReport.cashSales)

        // Starting float (200) + Cash Sales (250) - Drops (150) = Expected (300)
        assertEquals(300.0, xReport.expectedCashInDrawer)
    }

    @Test
    fun testFinalZReportWithBlindDropDiscrepancyAndSupervisorApproval() {
        val breakdowns = listOf(
            PaymentMethodBreakdown(PaymentMethodType.CASH, transactionCount = 5, grossSales = 100.0)
        )
        val xReport = service.generateXReport(
            shiftId = "SHIFT-BAR-02",
            stationId = "STATION-MAIN-BAR",
            cashierId = "USR-12",
            cashierName = "Alice Doe",
            openedAtMillis = 1000L,
            startingCashFloat = 100.0,
            totalCashDrops = 0.0,
            paymentBreakdowns = breakdowns
        )
        // Expected cash: 100 + 100 = 200.0

        // 1. Blind drop with shortage (-$20 shortage) -> Requires supervisor approval
        val shortageBlindDrop = BlindDropDeclaration(countedCashAmount = 180.0)
        val unapprovedZReport = service.generateFinalZReport(
            xReport = xReport,
            blindDrop = shortageBlindDrop,
            closedAtMillis = 3000L,
            supervisorPin = null
        )
        assertEquals(-20.0, unapprovedZReport.cashDiscrepancy)
        assertFalse(unapprovedZReport.isBalanced)
        assertFalse(unapprovedZReport.isOverShortApproved)

        // 2. With supervisor PIN approval
        val approvedZReport = service.generateFinalZReport(
            xReport = xReport,
            blindDrop = shortageBlindDrop,
            closedAtMillis = 3000L,
            supervisorPin = "9988"
        )
        assertTrue(approvedZReport.isOverShortApproved)
        assertEquals("9988", approvedZReport.supervisorOverridePin)
    }

    @Test
    fun testThermalReceiptFormatting() {
        val xReport = service.generateXReport(
            shiftId = "SHIFT-03",
            stationId = "BAR-01",
            cashierId = "USR-01",
            cashierName = "John",
            openedAtMillis = 1000L,
            startingCashFloat = 100.0,
            totalCashDrops = 0.0,
            paymentBreakdowns = listOf(
                PaymentMethodBreakdown(PaymentMethodType.CASH, 2, 50.0)
            )
        )
        val zReport = service.generateFinalZReport(xReport, BlindDropDeclaration(150.0))

        val receiptText = service.renderSettlementReceiptText(zReport)
        assertTrue(receiptText.contains("FINAL SHIFT Z-REPORT"))
        assertTrue(receiptText.contains("TOTAL NET REVENUE: $50.00"))
        assertTrue(receiptText.contains("BALANCED (0.00)"))

        val rawPayload = service.buildThermalReceiptPayload(zReport)
        assertTrue(rawPayload.isNotEmpty())
        assertEquals(0x1B.toByte(), rawPayload[0])
    }

    @Test
    fun testBackendAccountingLedgerTransmission() = runTest {
        val xReport = service.generateXReport(
            shiftId = "SHIFT-04",
            stationId = "BAR-01",
            cashierId = "USR-01",
            cashierName = "John",
            openedAtMillis = 1000L,
            startingCashFloat = 100.0,
            totalCashDrops = 0.0,
            paymentBreakdowns = emptyList()
        )
        val zReport = service.generateFinalZReport(xReport, BlindDropDeclaration(100.0))

        // Success sync
        val synced = service.transmitToAccountingLedger(zReport)
        assertEquals(LedgerSyncStatus.SYNCED, synced.backendLedgerSyncStatus)
        assertEquals(1, ledgerClient.syncedReports.size)

        // Failure sync
        ledgerClient.shouldSucceed = false
        val failed = service.transmitToAccountingLedger(zReport)
        assertEquals(LedgerSyncStatus.FAILED_RETRY, failed.backendLedgerSyncStatus)
    }

    @Test
    fun testMultipleCashPaymentBreakdownsAreSummedInCashSales() {
        val breakdowns = listOf(
            PaymentMethodBreakdown(PaymentMethodType.CASH, transactionCount = 5, grossSales = 100.0, refunds = 0.0),
            PaymentMethodBreakdown(PaymentMethodType.CASH, transactionCount = 3, grossSales = 50.0, refunds = 10.0)
        )
        val xReport = service.generateXReport(
            shiftId = "SHIFT-09",
            stationId = "STATION-01",
            cashierId = "USR-01",
            cashierName = "Cashier",
            openedAtMillis = 1000L,
            startingCashFloat = 100.0,
            totalCashDrops = 0.0,
            paymentBreakdowns = breakdowns
        )
        assertEquals(140.0, xReport.cashSales)
        assertEquals(240.0, xReport.expectedCashInDrawer)
    }
    @Test
    fun testLongZReportIdPreservedAndNonAsciiWitnessRendered() {
        // Shift with long 36-character UUID shiftId
        val longShiftId = "550e8400-e29b-41d4-a716-446655440000"
        val xReport = service.generateXReport(
            shiftId = longShiftId,
            stationId = "STATION-POOL-01",
            cashierId = "USR-99",
            cashierName = "Cashier Name",
            openedAtMillis = 1774268000000L,
            startingCashFloat = 200.0,
            totalCashDrops = 0.0,
            paymentBreakdowns = emptyList()
        )

        // Non-ASCII witness name
        val nonAsciiWitness = "\u674E\u534E"
        val zReport = service.generateFinalZReport(
            xReport = xReport,
            blindDrop = BlindDropDeclaration(
                countedCashAmount = 200.0,
                witnessCashierName = nonAsciiWitness,
                notes = "Closing shift"
            ),
            closedAtMillis = 1774271600000L
        )

        val receiptText = service.renderSettlementReceiptText(zReport)

        // Verify full zReportId is present without truncation
        val expectedFullId = "Z-$longShiftId-1774271600000"
        assertTrue(zReport.zReportId == expectedFullId)
        assertTrue(receiptText.contains(expectedFullId.take(28)))
        assertTrue(receiptText.contains(expectedFullId.substring(28)))

        // Verify non-ASCII witness is preserved and signature line exists
        assertTrue(receiptText.contains("Witness     : \u674E\u534E"))
        assertTrue(receiptText.contains("Witness Signature: _______________________"))
    }
}