package com.cruise.pos.shared.domain.service.fiscal

import com.cruise.pos.shared.domain.model.fiscal.DigitalCertificateSigner
import com.cruise.pos.shared.domain.model.fiscal.FiscalAuditApiClient
import com.cruise.pos.shared.domain.model.fiscal.FiscalReportSubmissionResponse
import com.cruise.pos.shared.domain.model.fiscal.FiscalShiftLedger
import com.cruise.pos.shared.domain.model.fiscal.SignedFiscalReportSubmission

class FiscalAuditSubmissionService(
    private val signer: DigitalCertificateSigner,
    private val apiClient: FiscalAuditApiClient,
    private val reportBuilder: FiscalShiftReportBuilder = FiscalShiftReportBuilder()
) {

    /**
     * Signs and submits fiscal shift ledger to POST /api/v1/pos/fiscal-reports
     */
    suspend fun signAndSubmitFiscalReport(
        keyAlias: String,
        ledger: FiscalShiftLedger,
        submissionTimestampEpochMs: Long = ledger.closedAtEpochMs
    ): Result<FiscalReportSubmissionResponse> {
        val payloadJson = reportBuilder.exportToJson(ledger)
        val payloadBytes = payloadJson.encodeToByteArray()

        val signatureHex = signer.signPayload(keyAlias, payloadBytes).getOrElse { e ->
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            return Result.failure(e)
        }
        val certFingerprint = try {
            signer.getCertificateFingerprint(keyAlias)
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            return Result.failure(e)
        }

        val submission = SignedFiscalReportSubmission(
            reportId = ledger.exportId,
            shiftId = ledger.shiftId,
            terminalId = ledger.terminalId,
            submissionTimestampEpochMs = submissionTimestampEpochMs,
            digitalCertificateFingerprint = certFingerprint,
            keyStoreAlias = keyAlias,
            signatureHex = signatureHex,
            payloadJson = payloadJson
        )

        return apiClient.submitSignedFiscalReport(submission).mapCatching { response ->
            when (response.status.uppercase()) {
                "ACCEPTED", "QUEUED" -> response
                else -> throw IllegalStateException(
                    "Fiscal report ${submission.reportId} not accepted (status=${response.status}): ${response.message}"
                )
            }
        }
    }
}
