package com.cruise.pos.shared.data.printer

/**
 * Renders monochrome 1-bit bitmap images into ESC/POS raster bit image commands (GS v 0).
 * Specifically converts 2D digital signature stroke coordinates or pixel grids into
 * ESC/POS printable bytes.
 */
class ReceiptBitmapRenderer {

    companion object {
        val GS: Byte = 0x1D
    }

    /**
     * Converts a 2D boolean pixel matrix (true = black dot, false = white dot)
     * into ESC/POS GS v 0 raster bit image bytes.
     *
     * @param width Width in dots (must be divisible by 8, padded if needed)
     * @param height Height in dots
     * @param pixels 2D array [height][width] where true is a printed dot
     */
    fun renderMonochromeBitmap(pixels: Array<BooleanArray>): ByteArray {
        val height = pixels.size
        if (height == 0) return byteArrayOf()
        val originalWidth = pixels[0].size
        if (originalWidth == 0) return byteArrayOf()

        // Width in bytes (padded to multiple of 8)
        val widthBytes = (originalWidth + 7) / 8
        val effectiveWidthDots = widthBytes * 8

        val xL = (widthBytes and 0xFF).toByte()
        val xH = ((widthBytes shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        // GS v 0 m xL xH yL yH (m = 0: normal mode)
        val header = byteArrayOf(GS, 0x76, 0x30, 0x00, xL, xH, yL, yH)
        val result = mutableListOf<Byte>()
        result.addAll(header.toList())

        for (y in 0 until height) {
            for (byteIdx in 0 until widthBytes) {
                var currentByte = 0
                for (bit in 0..7) {
                    val x = byteIdx * 8 + bit
                    val isBlack = if (x < originalWidth) pixels[y][x] else false
                    if (isBlack) {
                        currentByte = currentByte or (1 shl (7 - bit))
                    }
                }
                result.add(currentByte.toByte())
            }
        }

        return result.toByteArray()
    }

    /**
     * Renders signature path points (normalized 0.0 .. 1.0 or pixel coordinates)
     * into a monochrome bitmap raster for receipt printing.
     */
    fun renderSignatureStrokeToBitmap(
        strokes: List<List<Pair<Float, Float>>>,
        canvasWidthDots: Int = 384,
        canvasHeightDots: Int = 120,
        strokeThickness: Int = 2
    ): ByteArray {
        val grid = Array(canvasHeightDots) { BooleanArray(canvasWidthDots) }

        val isNormalized = strokes.flatten().all { it.first <= 1.0f && it.second <= 1.0f }
        fun mapX(x: Float): Int = if (isNormalized) {
            (x.coerceIn(0f, 1f) * (canvasWidthDots - 1)).toInt()
        } else {
            x.coerceIn(0f, (canvasWidthDots - 1).toFloat()).toInt()
        }
        fun mapY(y: Float): Int = if (isNormalized) {
            (y.coerceIn(0f, 1f) * (canvasHeightDots - 1)).toInt()
        } else {
            y.coerceIn(0f, (canvasHeightDots - 1).toFloat()).toInt()
        }

        for (stroke in strokes) {
            if (stroke.size == 1) {
                val p = stroke[0]
                val x = mapX(p.first)
                val y = mapY(p.second)
                drawLine(grid, x0 = x, y0 = y, x1 = x, y1 = y, thickness = strokeThickness)
            } else {
                for (i in 0 until stroke.size - 1) {
                    val p1 = stroke[i]
                    val p2 = stroke[i + 1]
                    drawLine(
                        grid,
                        x0 = mapX(p1.first),
                        y0 = mapY(p1.second),
                        x1 = mapX(p2.first),
                        y1 = mapY(p2.second),
                        thickness = strokeThickness
                    )
                }
            }
        }

        return renderMonochromeBitmap(grid)
    }

    /**
     * Bresenham's line algorithm with thickness
     */
    private fun drawLine(
        grid: Array<BooleanArray>,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        thickness: Int
    ) {
        val height = grid.size
        val width = grid[0].size

        var cx0 = x0
        var cy0 = y0
        val dx = kotlin.math.abs(x1 - cx0)
        val dy = kotlin.math.abs(y1 - cy0)
        val sx = if (cx0 < x1) 1 else -1
        val sy = if (cy0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            // Apply thickness
            for (tx in -thickness / 2..thickness / 2) {
                for (ty in -thickness / 2..thickness / 2) {
                    val px = cx0 + tx
                    val py = cy0 + ty
                    if (px in 0 until width && py in 0 until height) {
                        grid[py][px] = true
                    }
                }
            }

            if (cx0 == x1 && cy0 == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx0 += sx
            }
            if (e2 < dx) {
                err += dx
                cy0 += sy
            }
        }
    }
}
