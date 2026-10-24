package com.cruise.pos.shared.domain.service.fiscal

import com.cruise.pos.shared.domain.model.fiscal.CashDrawerReconciliation
import com.cruise.pos.shared.domain.model.fiscal.HourlySalesBucket
import com.cruise.pos.shared.domain.model.fiscal.MaritimeFiscalSummary
import com.cruise.pos.shared.domain.model.fiscal.MidDayXReportSnapshot
import com.cruise.pos.shared.domain.model.fiscal.PaymentTenderSummary

/**
 * Mid-day X-Report generator for supervisor auditing.
 * Produces a non-destructive audit snapshot of active shift transactions without resetting
 * sequence counters or altering drawer totals.
 */
class MidDayXReportGenerator(
    private val clock: () -> Long = { 1774274400000L }
) {

    private fun formatMoney(value: Double): String {
        val cents = kotlin.math.round(kotlin.math.abs(value) * 100.0).toLong()
        val sign = if (value < 0.0 && cents != 0L) "-" else ""
        return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    /**
     * Builds non-destructive mid-day X-Report snapshot.
     */
    fun generateSnapshot(
        shiftId: String,
        terminalId: String,
        outletId: String,
        outletName: String,
        cashierId: String,
        cashierName: String,
        supervisorId: String,
        supervisorName: String,
        openedAtEpochMs: Long,
        snapshotTimestampEpochMs: Long = clock(),
        totalTransactions: Int,
        totalGrossRevenue: Double,
        totalDiscounts: Double,
        totalRefunds: Double,
        paymentTenders: List<PaymentTenderSummary>,
        cashDrawer: CashDrawerReconciliation,
        hourlyActivity: List<HourlySalesBucket> = emptyList(),
        fiscalSummary: MaritimeFiscalSummary? = null
    ): MidDayXReportSnapshot {
        val totalNet = totalGrossRevenue - totalDiscounts - totalRefunds
        val xReportNumber = "X-$terminalId-${snapshotTimestampEpochMs}"

        return MidDayXReportSnapshot(
            xReportNumber = xReportNumber,
            shiftId = shiftId,
            terminalId = terminalId,
            outletId = outletId,
            outletName = outletName,
            cashierId = cashierId,
            cashierName = cashierName,
            supervisorId = supervisorId,
            supervisorName = supervisorName,
            openedAtEpochMs = openedAtEpochMs,
            snapshotTimestampEpochMs = snapshotTimestampEpochMs,
            isShiftClosed = false,
            totalTransactions = totalTransactions,
            totalGrossRevenue = totalGrossRevenue,
            totalDiscounts = totalDiscounts,
            totalRefunds = totalRefunds,
            totalNetRevenue = totalNet,
            paymentTenders = paymentTenders,
            cashDrawer = cashDrawer,
            hourlyActivity = hourlyActivity,
            fiscalSummary = fiscalSummary
        )
    }

    /**
     * Formats human-readable mid-day X-Report text layout suitable for supervisor review or slip printing.
     */
    fun formatSlipText(snapshot: MidDayXReportSnapshot): String {
        val sb = StringBuilder()
        val sep = "------------------------------------------\n"
        val dsep = "==========================================\n"

        sb.append(dsep)
        sb.append("         MID-DAY AUDIT X-REPORT\n")
        sb.append("       (NON-DESTRUCTIVE SNAPSHOT)\n")
        sb.append(dsep)
        sb.append("Report No   : ${snapshot.xReportNumber}\n")
        sb.append("Terminal    : ${snapshot.terminalId} | Outlet: ${snapshot.outletName}\n")
        sb.append("Cashier     : ${snapshot.cashierName} (${snapshot.cashierId})\n")
        sb.append("Supervisor  : ${snapshot.supervisorName} (${snapshot.supervisorId})\n")
        sb.append("Shift Status: ACTIVE / OPEN (Not closed)\n")
        sb.append("Total Orders: ${snapshot.totalTransactions}\n")
        sb.append(sep)
        sb.append("TENDER BREAKDOWN:\n")
        for (tender in snapshot.paymentTenders) {
            val label = tender.tenderType.padEnd(18)
            val cnt = "(${tender.count})".padStart(6)
            val amt = "$${formatMoney(tender.totalAmount)}".padStart(12)
            sb.append("$label $cnt $amt\n")
        }
        sb.append(sep)
        sb.append("Gross Sales : $${formatMoney(snapshot.totalGrossRevenue)}\n")
        sb.append("Discounts   : -$${formatMoney(snapshot.totalDiscounts)}\n")
        sb.append("Refunds     : -$${formatMoney(snapshot.totalRefunds)}\n")
        sb.append("Net Revenue : $${formatMoney(snapshot.totalNetRevenue)}\n")
        sb.append(sep)
        sb.append("CASH DRAWER SNAPSHOT:\n")
        sb.append("Opening Cash Float  : $${formatMoney(snapshot.cashDrawer.openingFloat)}\n")
        sb.append("Cash Sales          : $${formatMoney(snapshot.cashDrawer.cashSales)}\n")
        if (snapshot.cashDrawer.paidIns > 0.0) {
            sb.append("Paid In Cash        : +$${formatMoney(snapshot.cashDrawer.paidIns)}\n")
        }
        if (snapshot.cashDrawer.paidOuts > 0.0) {
            sb.append("Paid Out Cash       : -$${formatMoney(snapshot.cashDrawer.paidOuts)}\n")
        }
        sb.append("Mid-Shift Drops     : -$${formatMoney(snapshot.cashDrawer.cashDrops)}\n")
        sb.append("Expected In Drawer  : $${formatMoney(snapshot.cashDrawer.expectedCash)}\n")

        snapshot.fiscalSummary?.let { fiscal ->
            sb.append(sep)
            sb.append("MARITIME FISCAL & DUTY-FREE:\n")
            sb.append("Territorial Status : ${fiscal.territorialStatus.name}\n")
            sb.append("Duty-Free Exempt   : $${formatMoney(fiscal.totalExemptDutyFreeSales)}\n")
            sb.append("Taxable Sales      : $${formatMoney(fiscal.totalTaxableSales)}\n")
            sb.append("Tax Collected      : $${formatMoney(fiscal.totalTaxCollected)}\n")
        }

        sb.append(dsep)
        sb.append("Supervisor Signature: __________________\n")
        sb.append(dsep)
        return sb.toString()
    }
}
