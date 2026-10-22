package com.example.cruise_pos.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun IdleSecurityLockOverlay(
    isLocked: Boolean,
    idleSecondsRemaining: Int,
    onUnlockRequest: () -> Unit
) {
    if (!isLocked) return

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0020617)) // Dark privacy blanking overlay
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🔒",
                fontSize = 56.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TERMINAL AUTO-LOCKED",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Device locked due to inactivity to protect guest folios & shift balances.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onUnlockRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "RE-ENTER CASHIER PIN",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
