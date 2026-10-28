package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.BlindDropDeclaration
import com.cruise.pos.shared.domain.model.LedgerSyncStatus
import com.cruise.pos.shared.domain.model.PaymentMethodBreakdown
import com.cruise.pos.shared.domain.model.PaymentMethodType
import com.cruise.pos.shared.domain.model.ShiftXReport
import com.cruise.pos.shared.domain.model.ShiftZReport

interface BackendAccountingLedgerClient {
    suspend fun syncShiftSettlement(zReport: ShiftZReport): Result<String>
}

class ShiftSettlementService(
    private val accountingClient: BackendAccountingLedgerClient = MockBackendAccountingLedgerClient(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private fun formatMoney(value: Double): String {
        val cents = kotlin.math.round(kotlin.math.abs(value) * 100.0).toLong()
        val sign = if (value < 0.0 && cents != 0L) "-" else ""
        return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

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
        currentTimestampMillis: Long = 0L
    ): ShiftXReport {
        val actualTimestamp = if (currentTimestampMillis > 0L) currentTimestampMillis else clock()
        return ShiftXReport(
            shiftId = shiftId,
            stationId = stationId,
            cashierId = cashierId,
            cashierName = cashierName,
            openedAtMillis = openedAtMillis,
            generatedAtMillis = actualTimestamp,
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
        closedAtMillis: Long = 0L,
        supervisorPin: String? = null
    ): ShiftZReport {
        require(blindDrop.countedCashAmount.isFinite() && blindDrop.countedCashAmount >= 0.0) {
            "Counted cash amount must be a finite, non-negative number"
        }
        val actualClosedAt = if (closedAtMillis > 0L) closedAtMillis else clock()
        val discrepancy = blindDrop.countedCashAmount - xReport.expectedCashInDrawer
        val requiresApproval = kotlin.math.abs(discrepancy) > 10.0 // > $10 discrepancy requires manager signoff

        return ShiftZReport(
            zReportId = "Z-${xReport.shiftId}-${actualClosedAt}",
            shiftId = xReport.shiftId,
            stationId = xReport.stationId,
            cashierId = xReport.cashierId,
            cashierName = xReport.cashierName,
            openedAtMillis = xReport.openedAtMillis,
            closedAtMillis = actualClosedAt,
            startingCashFloat = xReport.startingCashFloat,
            totalCashDrops = xReport.totalCashDrops,
            paymentBreakdowns = xReport.paymentBreakdowns,
            totalNetRevenue = xReport.totalNetRevenue,
            totalOrdersCount = xReport.totalOrdersCount,
            expectedCashInDrawer = xReport.expectedCashInDrawer,
            countedCashAmount = blindDrop.countedCashAmount,
            cashDiscrepancy = discrepancy,
            isBalanced = kotlin.math.abs(discrepancy) <= 0.009,
            isOverShortApproved = !requiresApproval || !supervisorPin.isNullOrBlank(),
            supervisorOverridePin = supervisorPin,
            witnessCashierName = blindDrop.witnessCashierName,
            notes = blindDrop.notes
        )
    }

        private fun sanitizeText(input: String, maxLength: Int? = 40): String {
        val filtered = input.filter { !it.isISOControl() }
        val result = if (filtered.isNotBlank()) filtered else input
        return if (maxLength != null) result.take(maxLength) else result
    }

    private fun appendWrappedField(sb: StringBuilder, label: String, value: String, lineWidth: Int = 42) {
        val prefix = "$label: "
        val firstLineAvailable = lineWidth - prefix.length
        if (value.length <= firstLineAvailable) {
            sb.append("$prefix$value\n")
        } else {
            sb.append("$prefix${value.take(firstLineAvailable)}\n")
            var remaining = value.substring(firstLineAvailable)
            while (remaining.isNotEmpty()) {
                val chunkSize = minOf(lineWidth - 2, remaining.length)
                sb.append("  ${remaining.take(chunkSize)}\n")
                remaining = remaining.substring(chunkSize)
            }
        }
    }

    /**
     * Formats thermal receipt payload for printing settlement slips.
     */
    fun renderSettlementReceiptText(zReport: ShiftZReport): String {
        val sb = StringBuilder()
        val sep = "------------------------------------------\n"
        val dsep = "==========================================\n"

        val cashierName = sanitizeText(zReport.cashierName)
        val cashierId = sanitizeText(zReport.cashierId)
        val shiftId = sanitizeText(zReport.shiftId)
        val stationId = sanitizeText(zReport.stationId)
        val zReportId = sanitizeText(zReport.zReportId, maxLength = null)
        val witness = zReport.witnessCashierName?.let { sanitizeText(it) }
        val notes = zReport.notes?.let { sanitizeText(it, 100) }

        sb.append(dsep)
        sb.append("         FINAL SHIFT Z-REPORT\n")
        sb.append("     CRUISE SHIP POS RECONCILIATION\n")
        sb.append(dsep)
        appendWrappedField(sb, "Z-Report ID ", zReportId)
        sb.append("Shift ID    : $shiftId\n")
        sb.append("Station     : $stationId\n")
        sb.append("Cashier     : $cashierName ($cashierId)\n")
                val hasWitness = !witness.isNullOrBlank() || !zReport.witnessCashierName.isNullOrBlank()
        if (hasWitness) {
            val witnessName = witness?.takeIf { it.isNotBlank() } ?: zReport.witnessCashierName.orEmpty()
            sb.append("Witness     : $witnessName\n")
        }
        sb.append("Total Orders: ${zReport.totalOrdersCount}\n")
        sb.append(sep)
        sb.append("PAYMENT METHOD BREAKDOWN:\n")
        sb.append(sep)

        for (pb in zReport.paymentBreakdowns) {
            val name = pb.method.name.padEnd(20)
            val countStr = "(${pb.transactionCount})".padStart(6)
            val amountStr = "$${formatMoney(pb.netSales)}".padStart(12)
            sb.append("$name $countStr $amountStr\n")
        }

        sb.append(sep)
        sb.append("TOTAL NET REVENUE: $${formatMoney(zReport.totalNetRevenue)}\n")
        sb.append(sep)
        sb.append("CASH DRAWER RECONCILIATION (BLIND DROP):\n")
        sb.append("Starting Cash Float : $${formatMoney(zReport.startingCashFloat)}\n")
        sb.append("Mid-Shift Cash Drops: -$${formatMoney(zReport.totalCashDrops)}\n")
        sb.append("Expected In Drawer  : $${formatMoney(zReport.expectedCashInDrawer)}\n")
        sb.append("Actual Counted Cash : $${formatMoney(zReport.countedCashAmount)}\n")

        val disc = zReport.cashDiscrepancy
        val discLabel = when {
            disc > 0.009 -> "OVERAGE (+)"
            disc < -0.009 -> "SHORTAGE (-)"
            else -> "BALANCED (0.00)"
        }
        sb.append("Discrepancy Status  : $discLabel ($${formatMoney(disc)})\n")
        if (!notes.isNullOrBlank()) {
            sb.append("Notes               : $notes\n")
        }
        sb.append(dsep)
        sb.append("Cashier Signature: _______________________\n\n")
                if (hasWitness) {
            sb.append("Witness Signature: _______________________\n\n")
        }
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
        return init + text.encodeToByteArray() + cut
    }

    /**
     * Transmits finalized Z-Report to central backend accounting ledger.
     */
    suspend fun transmitToAccountingLedger(zReport: ShiftZReport): ShiftZReport {
        val result = accountingClient.syncShiftSettlement(zReport)
        return if (result.isSuccess) {
            zReport.copy(backendLedgerSyncStatus = LedgerSyncStatus.SYNCED)
        } else {
            zReport.copy(backendLedgerSyncStatus = LedgerSyncStatus.FAILED_RETRY)
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
