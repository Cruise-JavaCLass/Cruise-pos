package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.BlindDropDeclaration
import com.cruise.pos.shared.domain.model.PaymentMethodBreakdown
import com.cruise.pos.shared.domain.model.PaymentMethodType
import com.cruise.pos.shared.domain.model.ShiftXReport
import com.cruise.pos.shared.domain.model.ShiftZReport

interface BackendAccountingLedgerClient {
    suspend fun syncShiftSettlement(zReport: ShiftZReport): Result<String>
}

class ShiftSettlementService(
    private val accountingClient: BackendAccountingLedgerClient = MockBackendAccountingLedgerClient()
) {

    /**
     * Generates mid-shift interim X-Report without closing the shift.
     */
    fun generateXReport(
        shiftId: String,
        stationId: String,
        cashierId: String,
        cashierName: String,
        openedAtMillis: Long,
        startingCashFloat: Double,
        totalCashDrops: Double,
        paymentBreakdowns: List<PaymentMethodBreakdown>,
        currentTimestampMillis: Long = System.currentTimeMillis()
    ): ShiftXReport {
        return ShiftXReport(
            shiftId = shiftId,
            stationId = stationId,
            cashierId = cashierId,
            cashierName = cashierName,
            openedAtMillis = openedAtMillis,
            generatedAtMillis = currentTimestampMillis,
            startingCashFloat = startingCashFloat,
            totalCashDrops = totalCashDrops,
            paymentBreakdowns = paymentBreakdowns
        )
    }

    /**
     * Completes blind drop audit and finalizes Shift Z-Report.
     */
    fun generateFinalZReport(
        xReport: ShiftXReport,
        blindDrop: BlindDropDeclaration,
        closedAtMillis: Long = System.currentTimeMillis(),
        supervisorPin: String? = null
    ): ShiftZReport {
        val discrepancy = blindDrop.countedCashAmount - xReport.expectedCashInDrawer
        val requiresApproval = kotlin.math.abs(discrepancy) > 10.0 // > $10 discrepancy requires manager signoff

        return ShiftZReport(
            zReportId = "Z-${xReport.shiftId}-${closedAtMillis}",
            shiftId = xReport.shiftId,
            stationId = xReport.stationId,
            cashierId = xReport.cashierId,
            cashierName = xReport.cashierName,
            openedAtMillis = xReport.openedAtMillis,
            closedAtMillis = closedAtMillis,
            startingCashFloat = xReport.startingCashFloat,
            totalCashDrops = xReport.totalCashDrops,
            paymentBreakdowns = xReport.paymentBreakdowns,
            totalNetRevenue = xReport.totalNetRevenue,
            totalOrdersCount = xReport.totalOrdersCount,
            expectedCashInDrawer = xReport.expectedCashInDrawer,
            actualCountedCash = blindDrop.countedCashAmount,
            cashDiscrepancy = discrepancy,
            isOverShortApproved = !requiresApproval || !supervisorPin.isNullOrBlank(),
            supervisorOverridePin = supervisorPin
        )
    }

    /**
     * Formats thermal receipt payload for printing settlement slips.
     */
    fun renderSettlementReceiptText(zReport: ShiftZReport): String {
        val sb = StringBuilder()
        val sep = "------------------------------------------\n"
        val dsep = "==========================================\n"

        sb.append(dsep)
        sb.append("         FINAL SHIFT Z-REPORT\n")
        sb.append("     CRUISE SHIP POS RECONCILIATION\n")
        sb.append(dsep)
        sb.append("Z-Report ID : ${zReport.zReportId}\n")
        sb.append("Shift ID    : ${zReport.shiftId}\n")
        sb.append("Station     : ${zReport.stationId}\n")
        sb.append("Cashier     : ${zReport.cashierName} (${zReport.cashierId})\n")
        sb.append("Total Orders: ${zReport.totalOrdersCount}\n")
        sb.append(sep)
        sb.append("PAYMENT METHOD BREAKDOWN:\n")
        sb.append(sep)

        for (pb in zReport.paymentBreakdowns) {
            val name = pb.method.name.padEnd(20)
            val countStr = "(${pb.transactionCount})".padStart(6)
            val amountStr = "$${"%.2f".format(pb.netSales)}".padStart(12)
            sb.append("$name $countStr $amountStr\n")
        }

        sb.append(sep)
        sb.append("TOTAL NET REVENUE: $${"%.2f".format(zReport.totalNetRevenue)}\n")
        sb.append(sep)
        sb.append("CASH DRAWER RECONCILIATION (BLIND DROP):\n")
        sb.append("Starting Cash Float : $${"%.2f".format(zReport.startingCashFloat)}\n")
        sb.append("Mid-Shift Cash Drops: -$${"%.2f".format(zReport.totalCashDrops)}\n")
        sb.append("Expected In Drawer  : $${"%.2f".format(zReport.expectedCashInDrawer)}\n")
        sb.append("Actual Counted Cash : $${"%.2f".format(zReport.actualCountedCash)}\n")

        val disc = zReport.cashDiscrepancy
        val discLabel = when {
            disc > 0.009 -> "OVERAGE (+)"
            disc < -0.009 -> "SHORTAGE (-)"
            else -> "BALANCED (0.00)"
        }
        sb.append("Discrepancy Status  : $discLabel ($${"%.2f".format(disc)})\n")
        sb.append(dsep)
        sb.append("Cashier Signature: _______________________\n\n")
        sb.append("Manager Signature: _______________________\n")
        sb.append(dsep)
        return sb.toString()
    }

    /**
     * Builds ESC/POS thermal printer bytes for Z-Report slip.
     */
    fun buildThermalReceiptPayload(zReport: ShiftZReport): ByteArray {
        val text = renderSettlementReceiptText(zReport)
        val init = byteArrayOf(0x1B, 0x40) // ESC @
        val cut = byteArrayOf(0x1D, 0x56, 0x41, 0x03) // GS V 65 3
        return init + text.toByteArray(Charsets.UTF_8) + cut
    }

    /**
     * Transmits finalized Z-Report to central backend accounting ledger.
     */
    suspend fun transmitToAccountingLedger(zReport: ShiftZReport): ShiftZReport {
        val result = accountingClient.syncShiftSettlement(zReport)
        return if (result.isSuccess) {
            zReport.copy(backendLedgerSyncStatus = "SYNCED")
        } else {
            zReport.copy(backendLedgerSyncStatus = "FAILED_RETRY")
        }
    }
}

class MockBackendAccountingLedgerClient(
    var shouldSucceed: Boolean = true
) : BackendAccountingLedgerClient {
    val syncedReports = mutableListOf<ShiftZReport>()

    override suspend fun syncShiftSettlement(zReport: ShiftZReport): Result<String> {
        return if (shouldSucceed) {
            syncedReports.add(zReport)
            Result.success("LEDGER-ACK-${zReport.zReportId}")
        } else {
            Result.failure(RuntimeException("Central ledger service unreachable on satellite network"))
        }
    }
}
