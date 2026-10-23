package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShiftStatus {
    OPEN,
    CLOSED,
    SUSPENDED
}

@Serializable
data class CashDenomination(
    val value: Double,
    val count: Int
) {
    val total: Double get() = value * count
}

@Serializable
data class CashDropRecord(
    val id: String,
    val shiftId: String,
    val amount: Double,
    val cashierId: String,
    val witnessId: String,
    val receiptNumber: String,
    val safeBalanceAfter: Double,
    val denominations: List<CashDenomination> = emptyList(),
    val droppedAt: String,
    val notes: String? = null
)

@Serializable
data class ShiftRecord(
    val id: String,
    val cashierId: Long,
    val cashierName: String,
    val stationId: String,
    val status: ShiftStatus,
    val startingFloat: Double,
    val startingDenominations: List<CashDenomination> = emptyList(),
    val openedAt: String,
    val closedAt: String? = null,
    val totalCashDrops: Double = 0.0,
    val cashDropHistory: List<CashDropRecord> = emptyList(),
    val expectedCash: Double? = null,
    val actualCountedCash: Double? = null,
    val cashDifference: Double? = null,
    val closingDenominations: List<CashDenomination> = emptyList(),
    val closingNote: String? = null
)

@Serializable
data class OpenShiftRequest(
    val cashierId: Long,
    val stationId: String,
    val startingFloat: Double,
    val denominations: List<CashDenomination> = emptyList()
)

@Serializable
data class CloseShiftRequest(
    val shiftId: String,
    val actualCountedCash: Double,
    val denominations: List<CashDenomination> = emptyList(),
    val closingNote: String? = null
)

@Serializable
data class CashDropRequest(
    val shiftId: String,
    val amount: Double,
    val cashierId: String,
    val witnessId: String,
    val witnessPin: String,
    val denominations: List<CashDenomination> = emptyList(),
    val notes: String? = null
)