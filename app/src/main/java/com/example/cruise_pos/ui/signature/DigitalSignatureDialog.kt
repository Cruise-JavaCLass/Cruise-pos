package com.example.cruise_pos.ui.signature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.Point2D
import com.cruise.pos.shared.domain.model.SignatureData
import com.cruise.pos.shared.domain.model.Stroke
import com.cruise.pos.shared.domain.service.SignatureProcessor

@Composable
fun DigitalSignatureDialog(
    transactionId: String,
    passengerName: String,
    totalAmount: Double,
    onSignatureConfirmed: (SignatureData) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    passengerId: String = passengerName
) {
    val processor = remember { SignatureProcessor() }
    val strokes = remember { mutableStateListOf<Stroke>() }
    val currentPoints = remember { mutableStateListOf<Point2D>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isSignatureValid by remember {
        derivedStateOf {
            val allStrokes = strokes.toList() + if (currentPoints.isNotEmpty()) listOf(Stroke(currentPoints.toList())) else emptyList()
            processor.validateSignature(allStrokes).isValid
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            .testTag("signature_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CABIN CHARGE AUTHORIZATION",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Guest: $passengerName • Amount: \$${"%.2f".format(totalAmount)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Signature Canvas Area with low-latency pointerInput
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(
                        width = 1.dp,
                        color = if (errorMessage != null) Color(0xFFEF4444) else Color(0xFF475569),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("signature_canvas_box")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("signature_drawing_canvas")
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    errorMessage = null
                                    currentPoints.clear()
                                    currentPoints.add(Point2D(offset.x, offset.y))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints.add(Point2D(change.position.x, change.position.y))
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) {
                                        strokes.add(Stroke(currentPoints.toList()))
                                        currentPoints.clear()
                                    }
                                },
                                onDragCancel = {
                                    currentPoints.clear()
                                }
                            )
                        }
                ) {
                    // Draw historical committed strokes
                    strokes.forEach { stroke ->
                        if (stroke.points.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF22D3EE),
                                style = CanvasStroke(
                                    width = 4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Draw in-progress current stroke
                    if (currentPoints.size > 1) {
                        val path = Path().apply {
                            moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (i in 1 until currentPoints.size) {
                                lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF38BDF8),
                            style = CanvasStroke(
                                width = 4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // Baseline signature guideline
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(1.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-36).dp)
                        .background(Color(0xFF334155))
                )

                Text(
                    text = "Sign on the line above ✍️",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 12.dp)
                )

                if (strokes.isEmpty() && currentPoints.isEmpty()) {
                    Text(
                        text = "Touch or use stylus to sign",
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Error or Warning Feedback
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFF87171),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("signature_error_text")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Non-repudiation notice
            Text(
                text = "By signing, guest authorizes this folio charge under maritime commerce regulations. A cryptographic SHA-256 audit digest is recorded.",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        strokes.clear()
                        currentPoints.clear()
                        errorMessage = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_signature_button")
                ) {
                    Text("Clear Canvas")
                }

                Button(
                    onClick = {
                        val allStrokes = strokes.toList() + if (currentPoints.isNotEmpty()) listOf(Stroke(currentPoints.toList())) else emptyList()
                        val amountCents = (totalAmount * 100).toLong()
                        val result = processor.processSignature(
                            strokes = allStrokes,
                            timestampMillis = System.currentTimeMillis(),
                            transactionId = transactionId,
                            passengerId = passengerId,
                            amountCents = amountCents
                        )
                        onSignatureConfirmed(result)
                    },
                    enabled = isSignatureValid,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF06B6D4),
                        disabledContainerColor = Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_signature_button")
                ) {
                    Text("Confirm Signature", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
