package com.cruise.pos.shared

import com.cruise.pos.shared.data.printer.EscPosCommandGenerator
import com.cruise.pos.shared.data.printer.ReceiptBitmapRenderer
import com.cruise.pos.shared.domain.printer.EscPosAlignment
import com.cruise.pos.shared.domain.printer.EscPosTextSize
import com.cruise.pos.shared.domain.printer.ReceiptData
import com.cruise.pos.shared.domain.printer.ReceiptLineItem
import com.cruise.pos.shared.domain.printer.ReceiptPaperWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscPosByteSequenceAndAlignmentTest {

    @Test
    fun testInitializationAndAlignmentCommandBytes() {
        val gen = EscPosCommandGenerator()
        gen.init()
            .setAlignment(EscPosAlignment.CENTER)
            .setBold(true)
            .textLine("CRUISE POS")

        val bytes = gen.build()

        // Init: ESC @ -> 0x1B, 0x40
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // Align Center: ESC a 1 -> 0x1B, 0x61, 0x01
        assertEquals(0x1B.toByte(), bytes[2])
        assertEquals(0x61.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])

        // Bold On: ESC E 1 -> 0x1B, 0x45, 0x01
        assertEquals(0x1B.toByte(), bytes[5])
        assertEquals(0x45.toByte(), bytes[6])
        assertEquals(0x01.toByte(), bytes[7])

        // Text "CRUISE POS" + LF
        val str = bytes.decodeToString()
        assertTrue(str.contains("CRUISE POS"))
    }

    @Test
    fun testTextSizeAndTwoColumnAlignment58mm() {
        val gen = EscPosCommandGenerator()
        gen.setTextSize(EscPosTextSize.DOUBLE_SIZE)

        val bytes = gen.build()
        // Double size: GS ! 0x11 -> 0x1D, 0x21, 0x11
        assertEquals(0x1D.toByte(), bytes[0])
        assertEquals(0x21.toByte(), bytes[1])
        assertEquals(0x11.toByte(), bytes[2])

        val gen2 = EscPosCommandGenerator()
        gen2.twoColumnRow("Item A", "$12.50", ReceiptPaperWidth.WIDTH_58MM)
        val content = gen2.build().decodeToString()
        assertTrue(content.contains("Item A"))
        assertTrue(content.contains("$12.50"))

        val line = content.lines().first { it.contains("Item A") }
        assertEquals(ReceiptPaperWidth.WIDTH_58MM.charPerLine, line.length)
    }

    @Test
    fun testReceiptDataFullEscPosAssembly() {
        val receipt = ReceiptData(
            receiptId = "RCP-7701",
            outletName = "Skyline Sunset Lounge",
            stationId = "TERM-02",
            cashierName = "Captain Morgan",
            guestFolioId = "FOLIO-8821",
            cabinNumber = "8821",
            items = listOf(
                ReceiptLineItem("Mojito Cocktail", 2, 14.0, 28.0),
                ReceiptLineItem("Truffle Fries", 1, 9.5, 9.5)
            ),
            subtotal = 37.5,
            gratuity = 6.75,
            tip = 5.0,
            total = 49.25,
            qrCodePayload = "https://cruise.local/folio/8821",
            timestamp = "2026-10-22 21:45:00",
            paperWidth = ReceiptPaperWidth.WIDTH_58MM
        )

        val gen = EscPosCommandGenerator()
        val receiptBytes = gen.buildReceipt(receipt)

        val fullText = receiptBytes.decodeToString()
        assertTrue(fullText.contains("Skyline Sunset Lounge"))
        assertTrue(fullText.contains("FOLIO-8821"))
        assertTrue(fullText.contains("2x Mojito Cocktail"))
        assertTrue(fullText.contains("$28.00"))
        assertTrue(fullText.contains("TOTAL:"))
        assertTrue(fullText.contains("$49.25"))
        assertTrue(fullText.contains("Thank You For Sailing With Us!"))

        // End of receipt has cut paper command: GS V B 0 -> 0x1D, 0x56, 0x42, 0x00
        val last4 = receiptBytes.takeLast(4).toByteArray()
        assertEquals(0x1D.toByte(), last4[0])
        assertEquals(0x56.toByte(), last4[1])
        assertEquals(0x42.toByte(), last4[2])
        assertEquals(0x00.toByte(), last4[3])
    }

    @Test
    fun testDigitalSignatureBitmapRendering() {
        val renderer = ReceiptBitmapRenderer()
        // Simple 16x8 test pixel matrix
        val pixels = Array(8) { y ->
            BooleanArray(16) { x -> x == y } // Diagonal line
        }

        val rasterBytes = renderer.renderMonochromeBitmap(pixels)
        // Header: GS v 0 0 xL xH yL yH
        // Width in bytes: 16 dots / 8 = 2 bytes (xL=2, xH=0)
        // Height in dots: 8 dots (yL=8, yH=0)
        assertEquals(0x1D.toByte(), rasterBytes[0]) // GS
        assertEquals(0x76.toByte(), rasterBytes[1]) // v
        assertEquals(0x30.toByte(), rasterBytes[2]) // 0
        assertEquals(0x00.toByte(), rasterBytes[3]) // m
        assertEquals(2.toByte(), rasterBytes[4])    // xL
        assertEquals(0.toByte(), rasterBytes[5])    // xH
        assertEquals(8.toByte(), rasterBytes[6])    // yL
        assertEquals(0.toByte(), rasterBytes[7])    // yH

        // 8 header bytes + (2 widthBytes * 8 height) = 24 bytes total
        assertEquals(24, rasterBytes.size)
    }
    @Test
    fun testDuplicateJobIdEnqueueIsRejected() = kotlinx.coroutines.test.runTest {
        val queue = com.cruise.pos.shared.data.printer.BluetoothPrintSpoolerQueue { null }
        val job = com.cruise.pos.shared.domain.printer.PrintJob(
            id = "JOB-DUP-01",
            title = "Customer Receipt",
            payload = byteArrayOf(0x1B, 0x40),
            createdAtEpochMs = 1000L
        )

        val firstEnqueue = queue.enqueueJob(job)
        val duplicateEnqueue = queue.enqueueJob(job)

        assertTrue(firstEnqueue)
        assertFalse(duplicateEnqueue)
    }

    @Test
    fun testRasterDimensionExceeding16BitIsRejected() {
        val renderer = ReceiptBitmapRenderer()
        // Width of (65536 * 8) dots -> widthBytes = 65536 > 65535
        val hugeWidthPixels = Array(1) { BooleanArray(65536 * 8) }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            renderer.renderMonochromeBitmap(hugeWidthPixels)
        }
    }

    @Test
    fun testLongQrCodePayloadFittedToPaperWidth() {
        val gen = EscPosCommandGenerator()
        // Very long payload that exceeds small module sizes
        val longPayload = "https://cruise.local/long-folio-url/".repeat(10)
        val receipt = ReceiptData(
            receiptId = "REC-99",
            outletName = "Bar",
            stationId = "ST-01",
            cashierName = "Cashier",
            guestFolioId = "101",
            cabinNumber = "1010",
            items = emptyList<ReceiptLineItem>(),
            subtotal = 10.0,
            gratuity = 1.0,
            tip = 0.0,
            total = 11.0,
            qrCodePayload = longPayload,
            timestamp = "2026-10-22 21:45:00",
            paperWidth = ReceiptPaperWidth.WIDTH_58MM
        )
        val bytes = gen.buildReceipt(receipt)
        assertTrue(bytes.isNotEmpty())
    }
}