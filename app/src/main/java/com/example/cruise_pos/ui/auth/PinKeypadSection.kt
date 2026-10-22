package com.example.cruise_pos.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinKeypadSection(
    pinLength: Int = 4,
    currentPin: String,
    errorMessage: String?,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            repeat(3) {
                shakeOffset.animateTo(20f, tween(50))
                shakeOffset.animateTo(-20f, tween(50))
            }
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.value.dp)
            .padding(16.dp)
    ) {
        // PIN Dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(bottom = 24.dp)
                .semantics {
                    stateDescription = "${currentPin.length} of $pinLength digits entered"
                }
        ) {
            for (i in 0 until pinLength) {
                val isFilled = i < currentPin.length
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !errorMessage.isNullOrBlank() -> Color(0xFFEF4444)
                                isFilled -> Color(0xFF06B6D4)
                                else -> Color(0xFF334155)
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = if (isFilled) Color(0xFF22D3EE) else Color(0xFF475569),
                            shape = CircleShape
                        )
                )
            }
        }

        // Error message feedback
        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "",
                color = Color(0xFFF87171),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Numeric Keypad Grid
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        for (row in keys) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                for (key in row) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                when (key) {
                                    "C" -> Color(0xFF451A1A)
                                    "⌫" -> Color(0xFF1E293B)
                                    else -> Color(0xFF1E293B)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (key == "C") Color(0xFFDC2626) else Color(0xFF334155),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                when (key) {
                                    "C" -> onClearClick()
                                    "⌫" -> onBackspaceClick()
                                    else -> onDigitClick(key)
                                }
                            }
                    ) {
                        Text(
                            text = key,
                            color = when (key) {
                                "C" -> Color(0xFFFCA5A5)
                                "⌫" -> Color(0xFF94A3B8)
                                else -> Color.White
                            },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
