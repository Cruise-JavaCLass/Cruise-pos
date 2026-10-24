package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMethodType {
    CASH,
    CREDIT_CARD,
    CABIN_FOLIO,
    ROOM_CHARGE,
    COUPON,
    VOUCHER
}

@Serializable
enum class LedgerSyncStatus {
    PENDING,
    SYNCED,
    FAILED_RETRY
}

@Serializable
data class PaymentMethodBreakdown(
    val method: PaymentMethodType,
    val transactionCount: Int,
    val grossSales: Double,
    val refunds: Double = 0.0,
    val netSales: Double = grossSales - refunds
)

@Serializable
data class ShiftXReport(
    val shiftId: String,
    val stationId: String,
    val cashierId: String,
    val cashierName: String,
    val openedAtMillis: Long,
    val generatedAtMillis: Long,
    val startingCashFloat: Double,
    val totalCashDrops: Double,
    val paymentBreakdowns: List<PaymentMethodBreakdown>,
    val totalGrossSales: Double = paymentBreakdowns.sumOf { it.grossSales },
    val totalRefunds: Double = paymentBreakdowns.sumOf { it.refunds },
    val totalNetRevenue: Double = totalGrossSales - totalRefunds,
    val totalOrdersCount: Int = paymentBreakdowns.sumOf { it.transactionCount }
) {
    val cashSales: Double
        get() = paymentBreakdowns
            .filter { it.method == PaymentMethodType.CASH }
            .sumOf { it.netSales }

    val expectedCashInDrawer: Double
        get() = startingCashFloat + cashSales - totalCashDrops
}

@Serializable
data class BlindDropDeclaration(
    val countedCashAmount: Double,
    val witnessCashierName: String? = null,
    val notes: String? = null
)

@Serializable
data class ShiftZReport(
    val zReportId: String,
    val shiftId: String,
    val stationId: String,
    val cashierId: String,
    val cashierName: String,
    val openedAtMillis: Long,
    val closedAtMillis: Long,
    val startingCashFloat: Double,
    val totalCashDrops: Double,
    val expectedCashInDrawer: Double,
    val countedCashAmount: Double,
    val cashDiscrepancy: Double,
    val isBalanced: Boolean,
    val isOverShortApproved: Boolean,
    val supervisorOverridePin: String? = null,
    val totalNetRevenue: Double,
    val totalOrdersCount: Int,
    val paymentBreakdowns: List<PaymentMethodBreakdown>,
    val backendLedgerSyncStatus: LedgerSyncStatus = LedgerSyncStatus.PENDING
) {
    val requiresSupervisorApproval: Boolean
        get() = kotlin.math.abs(cashDiscrepancy) > 10.0 && !isOverShortApproved
}
