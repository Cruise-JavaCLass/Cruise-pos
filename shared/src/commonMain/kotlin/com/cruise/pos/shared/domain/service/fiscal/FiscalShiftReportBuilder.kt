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
        val totalCash = receiptEntries.filter { it.tenderType.uppercase() == "CASH" }.sumOf { it.totalAmount }
        val totalFolio = receiptEntries.filter { it.tenderType.uppercase().contains("FOLIO") }.sumOf { it.totalAmount }
        val totalCard = receiptEntries.filter { it.tenderType.uppercase().contains("CARD") }.sumOf { it.totalAmount }
        val gross = receiptEntries.sumOf { it.totalAmount }
        val totalTax = receiptEntries.sumOf { it.taxAmount }
        val merkleRoot = computeCumulativeHashRoot(receiptEntries)

        return FiscalShiftLedger(
            exportId = exportId,
            shiftId = shiftId,
            terminalId = terminalId,
            outletId = outletId,
            cashierId = cashierId,
            openedAtEpochMs = openedAtEpochMs,
            closedAtEpochMs = closedAtEpochMs,
            initialCashFloat = initialCashFloat,
            totalCashSales = totalCash,
            totalFolioSales = totalFolio,
            totalCardSales = totalCard,
            grossTotalSales = gross,
            netTotalSales = gross - totalTax,
            totalTaxCollected = totalTax,
            receiptEntries = receiptEntries,
            cumulativeMerkleRootSha256 = merkleRoot
        )
    }

    companion object {
        /**
         * Serializes ledger to JSON.
         */
        fun exportToJson(ledger: FiscalShiftLedger): String {
            return Json { prettyPrint = true }.encodeToString(ledger)
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

    fun exportToJson(ledger: FiscalShiftLedger): String = Companion.exportToJson(ledger)
    fun exportToCsv(ledger: FiscalShiftLedger): String = Companion.exportToCsv(ledger)
}
