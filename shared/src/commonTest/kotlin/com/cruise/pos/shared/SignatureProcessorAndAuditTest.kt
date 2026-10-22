package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.Point2D
import com.cruise.pos.shared.domain.model.SignatureData
import com.cruise.pos.shared.domain.model.Stroke
import com.cruise.pos.shared.domain.service.SignatureProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SignatureProcessorAndAuditTest {

    private val processor = SignatureProcessor()

    @Test
    fun testEmptyStrokesIsBlank() {
        val result = processor.validateSignature(emptyList())
        assertFalse(result.isValid)
        assertTrue(result.isBlank)
        assertEquals(0, result.strokeCount)
    }

    @Test
    fun testSinglePointTapIsRejectedAsBlank() {
        // Accidental finger tap without drawing
        val strokes = listOf(
            Stroke(listOf(Point2D(10f, 10f), Point2D(11f, 10f)))
        )
        val result = processor.validateSignature(strokes)
        assertFalse(result.isValid)
        assertTrue(result.isBlank)
        assertTrue(result.errorMessage?.contains("too short") == true)
    }

    @Test
    fun testValidMultiStrokeSignaturePasses() {
        val stroke1 = Stroke(
            listOf(
                Point2D(10f, 10f),
                Point2D(30f, 40f),
                Point2D(50f, 80f),
                Point2D(80f, 120f)
            )
        )
        val stroke2 = Stroke(
            listOf(
                Point2D(100f, 50f),
                Point2D(120f, 70f),
                Point2D(150f, 100f)
            )
        )
        val strokes = listOf(stroke1, stroke2)

        val result = processor.validateSignature(strokes)
        assertTrue(result.isValid)
        assertFalse(result.isBlank)
        assertEquals(2, result.strokeCount)
        assertTrue(result.totalLength > SignatureData.MIN_SIGNATURE_LENGTH_PX)
    }

    @Test
    fun testSha256AuditHashDeterministicAndNonRepudiation() {
        val stroke = Stroke(
            listOf(
                Point2D(10f, 10f),
                Point2D(50f, 50f),
                Point2D(100f, 100f)
            )
        )
        val hash1 = processor.calculateAuditHash(
            strokes = listOf(stroke),
            timestampMillis = 1718900000000L,
            transactionId = "TX-8821",
            passengerId = "P-1001"
        )
        val hash2 = processor.calculateAuditHash(
            strokes = listOf(stroke),
            timestampMillis = 1718900000000L,
            transactionId = "TX-8821",
            passengerId = "P-1001"
        )

        assertNotNull(hash1)
        assertEquals(64, hash1.length) // SHA-256 hex string length
        assertEquals(hash1, hash2) // Deterministic

        // Modifying transaction ID must change the hash
        val hashDifferentTx = processor.calculateAuditHash(
            strokes = listOf(stroke),
            timestampMillis = 1718900000000L,
            transactionId = "TX-9999",
            passengerId = "P-1001"
        )
        assertFalse(hash1 == hashDifferentTx)
    }

    @Test
    fun testBase64PngExporter() {
        val stroke = Stroke(
            listOf(
                Point2D(15f, 25f),
                Point2D(65f, 75f)
            )
        )
        val base64 = processor.exportToBase64Png(listOf(stroke))
        assertTrue(base64.startsWith("data:image/png;base64,"))
        assertTrue(base64.length > 30)
    }

    @Test
    fun testProcessSignatureCompleteWorkflow() {
        val strokes = listOf(
            Stroke(
                listOf(
                    Point2D(0f, 0f),
                    Point2D(20f, 25f),
                    Point2D(40f, 50f),
                    Point2D(60f, 75f),
                    Point2D(80f, 100f)
                )
            )
        )
        val data = processor.processSignature(
            strokes = strokes,
            timestampMillis = 1718900000000L,
            transactionId = "TX-01",
            passengerId = "P-01"
        )

        assertFalse(data.isBlank)
        assertEquals(1, data.totalStrokeCount)
        assertEquals(5, data.totalPointsCount)
        assertNotNull(data.base64Png)
        assertNotNull(data.sha256Hash)
    }

    @Test
    fun testProcessSignatureThrowsOnBlank() {
        assertFailsWith<IllegalArgumentException> {
            processor.processSignature(
                strokes = emptyList(),
                timestampMillis = 1000L,
                transactionId = "TX-01",
                passengerId = "P-01"
            )
        }
    }
}
