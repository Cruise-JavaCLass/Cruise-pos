package com.cruise.pos.shared.domain.service.fiscal

import com.cruise.pos.shared.domain.model.fiscal.FiscalShiftLedger
import com.cruise.pos.shared.domain.model.fiscal.ReceiptAuditEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds structured JSON and CSV fiscal shift reports with cryptographic hash chains
 * compliant with maritime financial auditing requirements.
 */
class FiscalShiftReportBuilder(
    private val jsonSerializer: Json = Json { prettyPrint = true }
) {

    /**
     * Calculates cryptographic Merkle / cumulative hash root across all signed receipt entries.
     */
    fun computeCumulativeHashRoot(entries: List<ReceiptAuditEntry>): String {
        if (entries.isEmpty()) return "0".repeat(64)
        val ordered = entries.sortedBy { it.sequenceNumber }
        var level = ordered.map {
            com.cruise.pos.shared.domain.service.Sha256Digest.digest("${it.sequenceNumber}:${it.transactionId}:${it.receiptPayloadHashSha256}".encodeToByteArray())
        }
        while (level.size > 1) {
            level = level.chunked(2).map { chunk ->
                if (chunk.size == 2) {
                    com.cruise.pos.shared.domain.service.Sha256Digest.digest((chunk[0] + chunk[1]).encodeToByteArray())
                } else {
                    chunk[0]
                }
            }
        }
        return level.single()
    }

    /**
     * Assembles a structured FiscalShiftLedger package.
     */
    fun exportToJson(ledger: FiscalShiftLedger): String {
        return jsonSerializer.encodeToString(ledger)
    }

    private fun toCents(amount: Double): Long {
        require(amount.isFinite()) { "Fiscal amount must be finite: $amount" }
        return kotlin.math.round(amount * 100.0).toLong()
    }
    private fun fromCents(cents: Long): Double = cents / 100.0

    fun assembleShiftLedger(
        exportId: String,
        shiftId: String,
        terminalId: String,
        outletId: String,
        cashierId: String,
        openedAtEpochMs: Long,
        closedAtEpochMs: Long,
        initialCashFloat: Double,
        receiptEntries: List<ReceiptAuditEntry>
    ): FiscalShiftLedger {
        require(initialCashFloat.isFinite()) { "Initial cash float must be finite: $initialCashFloat" }
        val normalizedFloat = fromCents(toCents(initialCashFloat))
        val entriesSnapshot = receiptEntries.map { entry ->
            require(entry.totalAmount.isFinite()) { "Receipt totalAmount must be finite: ${entry.transactionId}" }
            require(entry.taxAmount.isFinite()) { "Receipt taxAmount must be finite: ${entry.transactionId}" }
            val roundedTotal = fromCents(toCents(entry.totalAmount))
            val roundedTax = fromCents(toCents(entry.taxAmount))
            if (roundedTotal == entry.totalAmount && roundedTax == entry.taxAmount) {
                entry
            } else {
                entry.copy(totalAmount = roundedTotal, taxAmount = roundedTax)
            }
        }
        val totalCashCents = entriesSnapshot.filter { it.tenderType.uppercase() == "CASH" }.sumOf { toCents(it.totalAmount) }
        val totalFolioCents = entriesSnapshot.filter { it.tenderType.uppercase().contains("FOLIO") }.sumOf { toCents(it.totalAmount) }
        val totalCardCents = entriesSnapshot.filter { it.tenderType.uppercase().contains("CARD") }.sumOf { toCents(it.totalAmount) }
        val grossCents = entriesSnapshot.sumOf { toCents(it.totalAmount) }
        val totalTaxCents = entriesSnapshot.sumOf { toCents(it.taxAmount) }
        val netCents = grossCents - totalTaxCents
        val merkleRoot = computeCumulativeHashRoot(entriesSnapshot)

        return FiscalShiftLedger(
            exportId = exportId,
            shiftId = shiftId,
            terminalId = terminalId,
            outletId = outletId,
            cashierId = cashierId,
            openedAtEpochMs = openedAtEpochMs,
            closedAtEpochMs = closedAtEpochMs,
            initialCashFloat = normalizedFloat,
            totalCashSales = fromCents(totalCashCents),
            totalFolioSales = fromCents(totalFolioCents),
            totalCardSales = fromCents(totalCardCents),
            grossTotalSales = fromCents(grossCents),
            netTotalSales = fromCents(netCents),
            totalTaxCollected = fromCents(totalTaxCents),
            receiptEntries = entriesSnapshot,
            cumulativeMerkleRootSha256 = merkleRoot
        )
    }

    companion object {
        private val defaultJson = Json { prettyPrint = true }

        /**
         * Serializes ledger to JSON.
         */
        fun exportToJson(ledger: FiscalShiftLedger): String {
            return defaultJson.encodeToString(ledger)
        }

        private fun formatMoney(value: Double): String {
            val cents = kotlin.math.round(kotlin.math.abs(value) * 100.0).toLong()
            val sign = if (value < 0.0 && cents != 0L) "-" else ""
            return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
        }

        /**
         * Exports receipts audit trail as standard CSV for shipboard ERP / accounting ingestion.
         */
        fun exportToCsv(ledger: FiscalShiftLedger): String {
            val sb = StringBuilder()
            // Header
            sb.append("Seq,TxId,Folio,Cabin,Timestamp,Amount,Tax,Tender,SigHash,ReceiptHash\n")
            for (e in ledger.receiptEntries) {
                sb.append("${e.sequenceNumber},")
                sb.append("\"${e.transactionId}\",")
                sb.append("\"${e.guestFolioId}\",")
                sb.append("\"${e.cabinNumber}\",")
                sb.append("${e.timestampEpochMs},")
                sb.append(formatMoney(e.totalAmount)).append(',')
                sb.append(formatMoney(e.taxAmount)).append(',')
                sb.append("\"${e.tenderType}\",")
                sb.append("\"${e.signatureHashSha256}\",")
                sb.append("\"${e.receiptPayloadHashSha256}\"\n")
            }
            return sb.toString()
        }
    }

    fun exportToCsv(ledger: FiscalShiftLedger): String = Companion.exportToCsv(ledger)
}
