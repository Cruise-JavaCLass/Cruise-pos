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
class MidDayXReportGenerator {

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
        snapshotTimestampEpochMs: Long = System.currentTimeMillis(),
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
            val amt = "$${"%.2f".format(tender.totalAmount)}".padStart(12)
            sb.append("$label $cnt $amt\n")
        }
        sb.append(sep)
        sb.append("Gross Sales : $${"%.2f".format(snapshot.totalGrossRevenue)}\n")
        sb.append("Discounts   : -$${"%.2f".format(snapshot.totalDiscounts)}\n")
        sb.append("Refunds     : -$${"%.2f".format(snapshot.totalRefunds)}\n")
        sb.append("Net Revenue : $${"%.2f".format(snapshot.totalNetRevenue)}\n")
        sb.append(sep)
        sb.append("CASH DRAWER SNAPSHOT:\n")
        sb.append("Opening Cash Float  : $${"%.2f".format(snapshot.cashDrawer.openingFloat)}\n")
        sb.append("Cash Sales          : $${"%.2f".format(snapshot.cashDrawer.cashSales)}\n")
        sb.append("Mid-Shift Drops     : -$${"%.2f".format(snapshot.cashDrawer.cashDrops)}\n")
        sb.append("Expected In Drawer  : $${"%.2f".format(snapshot.cashDrawer.expectedCash)}\n")

        snapshot.fiscalSummary?.let { fiscal ->
            sb.append(sep)
            sb.append("MARITIME FISCAL & DUTY-FREE:\n")
            sb.append("Territorial Status : ${fiscal.territorialStatus.name}\n")
            sb.append("Duty-Free Exempt   : $${"%.2f".format(fiscal.totalExemptDutyFreeSales)}\n")
            sb.append("Taxable Sales      : $${"%.2f".format(fiscal.totalTaxableSales)}\n")
            sb.append("Tax Collected      : $${"%.2f".format(fiscal.totalTaxCollected)}\n")
        }

        sb.append(dsep)
        sb.append("Supervisor Signature: __________________\n")
        sb.append(dsep)
        return sb.toString()
    }
}
