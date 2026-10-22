package com.cruise.pos.shared.domain.model.fiscal

import kotlinx.serialization.Serializable

@Serializable
data class PaymentTenderSummary(
    val tenderType: String,
    val count: Int,
    val totalAmount: Double
)

@Serializable
data class HourlySalesBucket(
    val hourOfDay: Int, // 0..23
    val transactionCount: Int,
    val salesAmount: Double
)

@Serializable
data class CashDrawerReconciliation(
    val openingFloat: Double,
    val cashSales: Double,
    val paidIns: Double = 0.0,
    val paidOuts: Double = 0.0,
    val cashDrops: Double = 0.0,
    val expectedCash: Double = openingFloat + cashSales + paidIns - paidOuts - cashDrops
)

@Serializable
data class MidDayXReportSnapshot(
    val xReportNumber: String,
    val shiftId: String,
    val terminalId: String,
    val outletId: String,
    val outletName: String,
    val cashierId: String,
    val cashierName: String,
    val supervisorId: String,
    val supervisorName: String,
    val openedAtEpochMs: Long,
    val snapshotTimestampEpochMs: Long,
    val isShiftClosed: Boolean = false, // Must be strictly false for X-Report
    val totalTransactions: Int,
    val totalGrossRevenue: Double,
    val totalDiscounts: Double,
    val totalRefunds: Double,
    val totalNetRevenue: Double,
    val paymentTenders: List<PaymentTenderSummary>,
    val cashDrawer: CashDrawerReconciliation,
    val hourlyActivity: List<HourlySalesBucket>,
    val fiscalSummary: MaritimeFiscalSummary? = null
)
