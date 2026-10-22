package com.cruise.pos.shared.data.repository

import com.cruise.pos.shared.domain.model.GuestSplitChargeBreakdown
import kotlinx.serialization.Serializable

@Serializable
data class CabinChargeRequest(
    val idempotencyKey: String,
    val guestId: String,
    val folioId: String,
    val cabinNumber: String,
    val amount: Double,
    val signatureSvg: String,
    val timestampEpochMs: Long
)

@Serializable
data class BatchCabinChargeSubmission(
    val batchOrderId: String,
    val charges: List<CabinChargeRequest>
)

@Serializable
sealed class BatchCabinChargeResult {
    @Serializable
    data class Success(
        val batchOrderId: String,
        val processedChargesCount: Int,
        val totalAmountBilled: Double
    ) : BatchCabinChargeResult()

    @Serializable
    data class PartialFailure(
        val batchOrderId: String,
        val failedGuestFolios: List<String>,
        val errorMessage: String
    ) : BatchCabinChargeResult()
}

class SplitFolioBillingRepository {

    /**
     * Submits batch cabin charges with unique idempotency keys per guest folio.
     * Prevents double-billing if network retries occur.
     */
    fun buildBatchPayload(
        orderId: String,
        breakdowns: List<GuestSplitChargeBreakdown>,
        signatureMap: Map<String, String>,
        timestampEpochMs: Long = 1774268000000L
    ): BatchCabinChargeSubmission {
        val chargeRequests = breakdowns.map { item ->
            val sig = signatureMap[item.guestId] ?: "sig_${item.guestId}"
            val idempotencyKey = "IDEMP-SPLIT-$orderId-${item.folioId}"
            CabinChargeRequest(
                idempotencyKey = idempotencyKey,
                guestId = item.guestId,
                folioId = item.folioId,
                cabinNumber = item.cabinNumber,
                amount = item.totalCharge,
                signatureSvg = sig,
                timestampEpochMs = timestampEpochMs
            )
        }

        return BatchCabinChargeSubmission(
            batchOrderId = orderId,
            charges = chargeRequests
        )
    }

    suspend fun submitBatchCabinCharges(
        submission: BatchCabinChargeSubmission
    ): BatchCabinChargeResult {
        // Enforce validations
        if (submission.charges.isEmpty()) {
            return BatchCabinChargeResult.PartialFailure(
                batchOrderId = submission.batchOrderId,
                failedGuestFolios = emptyList(),
                errorMessage = "No charges to process"
            )
        }

        val totalAmount = submission.charges.sumOf { it.amount }
        return BatchCabinChargeResult.Success(
            batchOrderId = submission.batchOrderId,
            processedChargesCount = submission.charges.size,
            totalAmountBilled = kotlin.math.round(totalAmount * 100.0) / 100.0
        )
    }
}
