package com.cruise.pos.shared.data.printer

import com.cruise.pos.shared.domain.printer.EscPosAlignment
import com.cruise.pos.shared.domain.printer.EscPosTextSize
import com.cruise.pos.shared.domain.printer.ReceiptData
import com.cruise.pos.shared.domain.printer.ReceiptPaperWidth

class EscPosCommandGenerator {

    companion object {
        // ESC/POS Command Constants
        val ESC: Byte = 0x1B
        val FS: Byte = 0x1C
        val GS: Byte = 0x1D
        val LF: Byte = 0x0A

        val CMD_INIT: ByteArray = byteArrayOf(ESC, 0x40)
        val CMD_ALIGN_LEFT: ByteArray = byteArrayOf(ESC, 0x61, 0x00)
        val CMD_ALIGN_CENTER: ByteArray = byteArrayOf(ESC, 0x61, 0x01)
        val CMD_ALIGN_RIGHT: ByteArray = byteArrayOf(ESC, 0x61, 0x02)

        val CMD_EMPHASIZE_ON: ByteArray = byteArrayOf(ESC, 0x45, 0x01)
        val CMD_EMPHASIZE_OFF: ByteArray = byteArrayOf(ESC, 0x45, 0x00)

        val CMD_UNDERLINE_ON: ByteArray = byteArrayOf(ESC, 0x2D, 0x01)
        val CMD_UNDERLINE_OFF: ByteArray = byteArrayOf(ESC, 0x2D, 0x00)

        val CMD_SIZE_NORMAL: ByteArray = byteArrayOf(GS, 0x21, 0x00)
        val CMD_SIZE_DOUBLE_HEIGHT: ByteArray = byteArrayOf(GS, 0x21, 0x01)
        val CMD_SIZE_DOUBLE_WIDTH: ByteArray = byteArrayOf(GS, 0x21, 0x10)
        val CMD_SIZE_DOUBLE: ByteArray = byteArrayOf(GS, 0x21, 0x11)

        val CMD_FEED_AND_CUT: ByteArray = byteArrayOf(GS, 0x56, 0x42, 0x00)
        const val MAX_QR_BYTE_CAPACITY_M: Int = 2331
    }

    private val buffer = mutableListOf<Byte>()

    fun init(): EscPosCommandGenerator {
        buffer.addAll(CMD_INIT.toList())
        return this
    }

    fun setAlignment(alignment: EscPosAlignment): EscPosCommandGenerator {
        val cmd = when (alignment) {
            EscPosAlignment.LEFT -> CMD_ALIGN_LEFT
            EscPosAlignment.CENTER -> CMD_ALIGN_CENTER
            EscPosAlignment.RIGHT -> CMD_ALIGN_RIGHT
        }
        buffer.addAll(cmd.toList())
        return this
    }

    fun setTextSize(size: EscPosTextSize): EscPosCommandGenerator {
        val cmd = when (size) {
            EscPosTextSize.NORMAL -> CMD_SIZE_NORMAL
            EscPosTextSize.DOUBLE_HEIGHT -> CMD_SIZE_DOUBLE_HEIGHT
            EscPosTextSize.DOUBLE_WIDTH -> CMD_SIZE_DOUBLE_WIDTH
            EscPosTextSize.DOUBLE_SIZE -> CMD_SIZE_DOUBLE
        }
        buffer.addAll(cmd.toList())
        return this
    }

    fun setBold(isBold: Boolean): EscPosCommandGenerator {
        buffer.addAll(if (isBold) CMD_EMPHASIZE_ON.toList() else CMD_EMPHASIZE_OFF.toList())
        return this
    }

    fun text(text: String): EscPosCommandGenerator {
        val sanitized = text.map { ch ->
            when (ch) {
                'é', 'è', 'ê', 'ë' -> 'e'
                'É', 'È', 'Ê', 'Ë' -> 'E'
                'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
                'Á', 'À', 'Â', 'Ä', 'Ã', 'Å' -> 'A'
                'í', 'ì', 'î', 'ï' -> 'i'
                'Í', 'Ì', 'Î', 'Ï' -> 'I'
                'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
                'Ó', 'Ò', 'Ô', 'Ö', 'Õ' -> 'O'
                'ú', 'ù', 'û', 'ü' -> 'u'
                'Ú', 'Ù', 'Û', 'Ü' -> 'U'
                'ñ' -> 'n'
                'Ñ' -> 'N'
                'ç' -> 'c'
                'Ç' -> 'C'
                else -> if (ch.code in 32..126 || ch == '\n' || ch == '\r' || ch == '\t') ch else '?'
            }
        }.joinToString("")
        val bytes = sanitized.encodeToByteArray()
        buffer.addAll(bytes.toList())
        return this
    }

    fun textLine(line: String = ""): EscPosCommandGenerator {
        text(line)
        buffer.add(LF)
        return this
    }

    fun feedLines(count: Int = 1): EscPosCommandGenerator {
        repeat(count) { buffer.add(LF) }
        return this
    }

    fun horizontalRule(char: Char = '-', width: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM): EscPosCommandGenerator {
        val rule = char.toString().repeat(width.charPerLine)
        textLine(rule)
        return this
    }

    fun twoColumnRow(
        left: String,
        right: String,
        width: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM
    ): EscPosCommandGenerator {
        val maxChars = width.charPerLine
        val spaceNeeded = maxChars - left.length - right.length
        if (spaceNeeded > 0) {
            textLine(left + " ".repeat(spaceNeeded) + right)
        } else if (right.isEmpty()) {
            textLine(left)
        } else {
            textLine(left)
            val rightPadding = (maxChars - right.length).coerceAtLeast(0)
            textLine(" ".repeat(rightPadding) + right)
        }
        return this
    }

    fun cutPaper(): EscPosCommandGenerator {
        buffer.addAll(CMD_FEED_AND_CUT.toList())
        return this
    }

    /**
     * Standard ESC/POS QR code printing (model 2)
     */
    fun qrCode(data: String, moduleSize: Int = 6): EscPosCommandGenerator {
        val dataBytes = data.encodeToByteArray()
        val len = dataBytes.size + 3
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()

        // 1. Set model 2: GS ( k 4 0 49 65 50 0
        buffer.addAll(byteArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00).toList())
        // 2. Set module size: GS ( k 3 0 49 67 size
        buffer.addAll(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleSize.toByte()).toList())
        // 3. Set error correction level M: GS ( k 3 0 49 69 49
        buffer.addAll(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x49, 0x31).toList())
        // 4. Store data: GS ( k pL pH 49 80 48 <data>
        buffer.addAll(byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30).toList())
        buffer.addAll(dataBytes.toList())
        // 5. Print QR: GS ( k 3 0 49 81 48
        buffer.addAll(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30).toList())
        return this
    }

    fun rawBytes(bytes: ByteArray): EscPosCommandGenerator {
        buffer.addAll(bytes.toList())
        return this
    }

    fun build(): ByteArray {
        return buffer.toByteArray()
    }

    fun buildReceipt(receipt: ReceiptData): ByteArray {
        buffer.clear()
        val width = receipt.paperWidth

        init()
        setAlignment(EscPosAlignment.CENTER)
        setTextSize(EscPosTextSize.DOUBLE_SIZE)
        setBold(true)
        textLine("CRUISE POS")

        setTextSize(EscPosTextSize.NORMAL)
        setBold(false)
        textLine(receipt.outletName)
        textLine("Station: ${receipt.stationId} | Cashier: ${receipt.cashierName}")
        textLine(receipt.timestamp)
        horizontalRule('=', width)

        setAlignment(EscPosAlignment.LEFT)
        twoColumnRow("Folio: ${receipt.guestFolioId}", "Cabin: ${receipt.cabinNumber}", width)
        twoColumnRow("Receipt: ${receipt.receiptId}", "", width)
        horizontalRule('-', width)

        twoColumnRow("ITEM", "AMOUNT", width)
        horizontalRule('-', width)

        for (item in receipt.items) {
            val itemDesc = "${item.quantity}x ${item.name}"
            val priceStr = formatCurrency(item.totalPrice)
            twoColumnRow(itemDesc, priceStr, width)
        }

        horizontalRule('-', width)
        twoColumnRow("Subtotal:", formatCurrency(receipt.subtotal), width)

        for (tax in receipt.taxes) {
            twoColumnRow(tax.label, formatCurrency(tax.amount), width)
        }

        if (receipt.gratuity > 0.0) {
            twoColumnRow("Auto Gratuity:", formatCurrency(receipt.gratuity), width)
        }
        if (receipt.tip > 0.0) {
            twoColumnRow("Tip:", formatCurrency(receipt.tip), width)
        }

        horizontalRule('=', width)
        setBold(true)
        setTextSize(EscPosTextSize.DOUBLE_HEIGHT)
        twoColumnRow("TOTAL:", formatCurrency(receipt.total), width)
        setTextSize(EscPosTextSize.NORMAL)
        setBold(false)

        receipt.digitalSignatureBmp?.let { bmpBytes ->
            feedLines(1)
            setAlignment(EscPosAlignment.CENTER)
            textLine("Guest Digital Signature:")
            rawBytes(bmpBytes)
            feedLines(1)
        }

        receipt.qrCodePayload?.let { qr ->
            val (fittedPayload, moduleSize) = fitQrToPaperWidth(qr, width)
            feedLines(1)
            setAlignment(EscPosAlignment.CENTER)
            qrCode(fittedPayload, moduleSize = moduleSize)
            feedLines(1)
            textLine("Scan to view e-Folio")
        }

        feedLines(1)
        setAlignment(EscPosAlignment.CENTER)
        textLine("Thank You For Sailing With Us!")
        feedLines(3)
        cutPaper()

        return build()
    }

    
    private fun estimateQrModules(dataLength: Int): Int {
        val version = when {
            dataLength <= 14 -> 1
            dataLength <= 26 -> 2
            dataLength <= 42 -> 3
            dataLength <= 62 -> 4
            dataLength <= 84 -> 5
            dataLength <= 106 -> 6
            dataLength <= 122 -> 7
            dataLength <= 152 -> 8
            dataLength <= 180 -> 9
            dataLength <= 213 -> 10
            dataLength <= 287 -> 14
            dataLength <= 412 -> 20
            dataLength <= 858 -> 30
            else -> 40
        }
        val modules = 21 + 4 * (version - 1)
        val quietZone = 8
        return modules + quietZone
    }

    private fun fitQrToPaperWidth(payload: String, paperWidth: ReceiptPaperWidth): Pair<String, Int> {
        val payloadBytes = payload.encodeToByteArray()
        require(payloadBytes.size <= MAX_QR_BYTE_CAPACITY_M) {
            "QR payload size (${payloadBytes.size} bytes) exceeds maximum supported capacity of $MAX_QR_BYTE_CAPACITY_M bytes at Error Correction Level M"
        }
        val totalModules = estimateQrModules(payloadBytes.size)
        val maxTargetModule = if (paperWidth == ReceiptPaperWidth.WIDTH_58MM) 5 else 7
        val calculatedSize = paperWidth.dotWidth / totalModules
        val moduleSize = maxOf(1, minOf(calculatedSize, maxTargetModule))
        return Pair(payload, moduleSize)
    }

    private fun formatCurrency(amount: Double): String {
        val isNegative = amount < 0
        val absAmount = if (isNegative) -amount else amount
        val totalCents = kotlin.math.round(absAmount * 100.0).toLong()
        val dollars = totalCents / 100
        val cents = (totalCents % 100).toString().padStart(2, '0')
        val prefix = if (isNegative) "-$" else "$"
        return "$prefix$dollars.$cents"
    }
}
