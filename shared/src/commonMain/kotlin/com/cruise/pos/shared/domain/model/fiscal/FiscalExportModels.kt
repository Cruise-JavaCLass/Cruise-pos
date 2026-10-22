package com.cruise.pos.shared.domain.model.fiscal

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptAuditEntry(
    val sequenceNumber: Long,
    val transactionId: String,
    val guestFolioId: String,
    val cabinNumber: String,
    val timestampEpochMs: Long,
    val totalAmount: Double,
    val taxAmount: Double,
    val tenderType: String,
    val signatureHashSha256: String,
    val receiptPayloadHashSha256: String
)

@Serializable
data class FiscalShiftLedger(
    val exportId: String,
    val shiftId: String,
    val terminalId: String,
    val outletId: String,
    val cashierId: String,
    val openedAtEpochMs: Long,
    val closedAtEpochMs: Long,
    val initialCashFloat: Double,
    val totalCashSales: Double,
    val totalFolioSales: Double,
    val totalCardSales: Double,
    val grossTotalSales: Double,
    val netTotalSales: Double,
    val totalTaxCollected: Double,
    val receiptEntries: List<ReceiptAuditEntry>,
    val cumulativeMerkleRootSha256: String
)
