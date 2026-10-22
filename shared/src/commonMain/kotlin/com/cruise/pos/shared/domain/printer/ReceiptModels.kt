package com.cruise.pos.shared.domain.printer

enum class ReceiptPaperWidth(val dotWidth: Int, val charPerLine: Int) {
    WIDTH_58MM(dotWidth = 384, charPerLine = 32),
    WIDTH_80MM(dotWidth = 576, charPerLine = 48)
}

enum class EscPosAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class EscPosTextSize {
    NORMAL,
    DOUBLE_HEIGHT,
    DOUBLE_WIDTH,
    DOUBLE_SIZE
}

data class ReceiptLineItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

data class ReceiptTaxItem(
    val label: String,
    val amount: Double
)

data class ReceiptData(
    val receiptId: String,
    val outletName: String,
    val stationId: String,
    val cashierName: String,
    val guestFolioId: String,
    val cabinNumber: String,
    val items: List<ReceiptLineItem>,
    val subtotal: Double,
    val taxes: List<ReceiptTaxItem> = emptyList(),
    val gratuity: Double = 0.0,
    val tip: Double = 0.0,
    val total: Double,
    val qrCodePayload: String? = null,
    val digitalSignatureBmp: ByteArray? = null,
    val timestamp: String,
    val paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM
)
