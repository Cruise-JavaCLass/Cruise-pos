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
        submissionTimestampEpochMs: Long = System.currentTimeMillis()
    ): Result<FiscalReportSubmissionResponse> {
        val payloadJson = reportBuilder.exportToJson(ledger)
        val payloadBytes = payloadJson.encodeToByteArray()

        val signResult = signer.signPayload(keyAlias, payloadBytes)
        if (signResult.isFailure) {
            return Result.failure(signResult.exceptionOrNull() ?: RuntimeException("Digital signature failure"))
        }

        val signatureHex = signResult.getOrThrow()
        val certFingerprint = signer.getCertificateFingerprint(keyAlias)

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

        return apiClient.submitSignedFiscalReport(submission)
    }
}
