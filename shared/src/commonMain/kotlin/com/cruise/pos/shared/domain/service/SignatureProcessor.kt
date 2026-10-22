package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.Point2D
import com.cruise.pos.shared.domain.model.SignatureData
import com.cruise.pos.shared.domain.model.SignatureVerificationResult
import com.cruise.pos.shared.domain.model.Stroke
import java.security.MessageDigest

class SignatureProcessor {

    /**
     * Validates that the signature is not blank, has sufficient stroke length,
     * and meets minimum point density requirements to prevent accidental taps.
     */
    fun validateSignature(strokes: List<Stroke>): SignatureVerificationResult {
        if (strokes.isEmpty()) {
            return SignatureVerificationResult(
                isValid = false,
                isBlank = true,
                strokeCount = 0,
                totalLength = 0f,
                sha256Hash = null,
                errorMessage = "Signature canvas is empty"
            )
        }

        val totalLength = strokes.sumOf { it.totalLength.toDouble() }.toFloat()
        val totalPoints = strokes.sumOf { it.points.size }

        if (totalLength < SignatureData.MIN_SIGNATURE_LENGTH_PX || totalPoints < SignatureData.MIN_POINTS_COUNT) {
            return SignatureVerificationResult(
                isValid = false,
                isBlank = true,
                strokeCount = strokes.size,
                totalLength = totalLength,
                sha256Hash = null,
                errorMessage = "Signature is too short or blank. Please draw a full signature."
            )
        }

        return SignatureVerificationResult(
            isValid = true,
            isBlank = false,
            strokeCount = strokes.size,
            totalLength = totalLength,
            sha256Hash = null
        )
    }

    /**
     * Computes a SHA-256 non-repudiation audit hash over the signature's
     * normalized stroke points coordinates, timestamp, and optional transaction/cabin context.
     */
    fun calculateAuditHash(
        strokes: List<Stroke>,
        timestampMillis: Long,
        transactionId: String = "",
        passengerId: String = ""
    ): String {
        val sb = java.lang.StringBuilder()
        sb.append("TX:").append(transactionId).append(";")
        sb.append("PID:").append(passengerId).append(";")
        sb.append("TIME:").append(timestampMillis).append(";")
        sb.append("STROKES:[")

        for (stroke in strokes) {
            sb.append("(")
            for (pt in stroke.points) {
                // Round to 1 decimal place to normalize minor render floating point differences
                sb.append(String.format(java.util.Locale.US, "%.1f,%.1f|", pt.x, pt.y))
            }
            sb.append(")")
        }
        sb.append("]")

        val rawBytes = sb.toString().toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a compact mock PNG Base64 string from stroke data for network serialization.
     */
    fun exportToBase64Png(strokes: List<Stroke>): String {
        val strokeDataSummary = strokes.joinToString(";") { stroke ->
            stroke.points.joinToString(",") { "${it.x.toInt()}:${it.y.toInt()}" }
        }
        val mockPngHeader = "data:image/png;base64,"
        val payload = "MOCK_PNG_DATA_FOR_STROKES:[$strokeDataSummary]".toByteArray(Charsets.UTF_8)
        val base64Body = java.util.Base64.getEncoder().encodeToString(payload)
        return mockPngHeader + base64Body
    }

    /**
     * Compresses and packages the complete signature package for POS transaction archiving.
     */
    fun processSignature(
        strokes: List<Stroke>,
        timestampMillis: Long,
        transactionId: String,
        passengerId: String
    ): SignatureData {
        val validation = validateSignature(strokes)
        require(validation.isValid) { validation.errorMessage ?: "Invalid signature" }

        val hash = calculateAuditHash(strokes, timestampMillis, transactionId, passengerId)
        val base64 = exportToBase64Png(strokes)

        return SignatureData(
            strokes = strokes,
            totalStrokeCount = strokes.size,
            totalPointsCount = strokes.sumOf { it.points.size },
            cumulativeLength = strokes.sumOf { it.totalLength.toDouble() }.toFloat(),
            base64Png = base64,
            sha256Hash = hash,
            timestampMillis = timestampMillis
        )
    }
}
