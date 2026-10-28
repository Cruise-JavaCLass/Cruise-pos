package com.cruise.pos.shared.domain.model.fiscal

import kotlinx.serialization.Serializable

@Serializable
data class SignedFiscalReportSubmission(
    val reportId: String,
    val shiftId: String,
    val terminalId: String,
    val submissionTimestampEpochMs: Long,
    val digitalCertificateFingerprint: String,
    val keyStoreAlias: String,
    val signatureHex: String,
    val payloadJson: String
)

@Serializable
data class FiscalReportSubmissionResponse(
    val submissionId: String,
    val status: String, // "ACCEPTED", "REJECTED", "QUEUED"
    val ledgerAuditHash: String,
    val ackTimestampEpochMs: Long,
    val message: String
)

interface DigitalCertificateSigner {
    suspend fun signPayload(keyAlias: String, payloadBytes: ByteArray): Result<String>
    fun getCertificateFingerprint(keyAlias: String): String
}

interface FiscalAuditApiClient {
    suspend fun submitSignedFiscalReport(submission: SignedFiscalReportSubmission): Result<FiscalReportSubmissionResponse>
}
