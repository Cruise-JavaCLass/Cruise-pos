package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

@Serializable
data class Point2D(
    val x: Float,
    val y: Float
) {
    fun distanceTo(other: Point2D): Float {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}

@Serializable
data class Stroke(
    val points: List<Point2D> = emptyList()
) {
    val totalLength: Float
        get() {
            if (points.size < 2) return 0f
            var length = 0f
            for (i in 0 until points.size - 1) {
                length += points[i].distanceTo(points[i + 1])
            }
            return length
        }
}

@Serializable
data class SignatureData(
    val strokes: List<Stroke> = emptyList(),
    val base64Png: String? = null,
    val sha256Hash: String? = null,
    val timestampMillis: Long = 0L
) {
    val totalStrokeCount: Int
        get() = strokes.size

    val totalPointsCount: Int
        get() = strokes.sumOf { it.points.size }

    val cumulativeLength: Float
        get() = strokes.sumOf { it.totalLength.toDouble() }.toFloat()

    val isBlank: Boolean
        get() = cumulativeLength < MIN_SIGNATURE_LENGTH_PX || totalPointsCount < MIN_POINTS_COUNT

    companion object {
        const val MIN_SIGNATURE_LENGTH_PX = 40.0f
        const val MIN_POINTS_COUNT = 5
    }
}

@Serializable
data class SignatureVerificationResult(
    val isValid: Boolean,
    val isBlank: Boolean,
    val strokeCount: Int,
    val totalLength: Float,
    val sha256Hash: String?,
    val errorMessage: String? = null
)
