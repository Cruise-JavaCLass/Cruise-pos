package com.example.cruise_pos

import com.cruise.pos.shared.domain.model.Point2D
import com.cruise.pos.shared.domain.model.SignatureData
import com.cruise.pos.shared.domain.model.Stroke
import com.cruise.pos.shared.domain.service.SignatureProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DigitalSignatureWorkflowTest {

    private lateinit var processor: SignatureProcessor

    @Before
    fun setUp() {
        processor = SignatureProcessor()
    }

    @Test
    fun testBlankCanvasDetectionAndGuard() {
        // Empty strokes
        val emptyResult = processor.validateSignature(emptyList())
        assertFalse(emptyResult.isValid)
        assertTrue(emptyResult.isBlank)

        // Micro tap (dot) with insufficient length
        val dotStroke = listOf(
            Stroke(listOf(Point2D(10f, 10f), Point2D(12f, 12f)))
        )
        val dotResult = processor.validateSignature(dotStroke)
        assertFalse(dotResult.isValid)
        assertTrue(dotResult.isBlank)
    }

    @Test
    fun testDrawSignatureWorkflowSuccess() {
        val validStroke = Stroke(
            listOf(
                Point2D(10f, 20f),
                Point2D(30f, 40f),
                Point2D(50f, 60f),
                Point2D(80f, 100f),
                Point2D(120f, 150f)
            )
        )
        val result = processor.validateSignature(listOf(validStroke))
        assertTrue(result.isValid)
        assertFalse(result.isBlank)
        assertTrue(result.totalLength > SignatureData.MIN_SIGNATURE_LENGTH_PX)

        val processed = processor.processSignature(
            strokes = listOf(validStroke),
            timestampMillis = 1718900000000L,
            transactionId = "TX-POS-1001",
            passengerId = "P-1001"
        )

        assertNotNull(processed.base64Png)
        assertTrue(processed.base64Png!!.startsWith("data:image/png;base64,"))
        assertNotNull(processed.sha256Hash)
        assertEquals(64, processed.sha256Hash!!.length)
    }

    @Test
    fun testClearCanvasWorkflow() {
        val strokes = mutableListOf<Stroke>()
        strokes.add(
            Stroke(
                listOf(
                    Point2D(10f, 20f),
                    Point2D(30f, 40f),
                    Point2D(50f, 60f),
                    Point2D(80f, 100f),
                    Point2D(120f, 150f)
                )
            )
        )
        assertEquals(1, strokes.size)

        // User taps "Clear Canvas"
        strokes.clear()
        val afterClearResult = processor.validateSignature(strokes)
        assertFalse(afterClearResult.isValid)
        assertTrue(afterClearResult.isBlank)
    }
}
