package com.example.cruise_pos.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun CameraXScanningViewfinder(
    isScanningActive: Boolean,
    onQrDetected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scanLineOffset by remember { mutableFloatStateOf(0f) }
    var mockScannedToken by remember { mutableStateOf("") }

    LaunchedEffect(isScanningActive) {
        if (isScanningActive) {
            while (true) {
                for (i in 0..100) {
                    scanLineOffset = i / 100f
                    delay(16)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GUEST CARD SCANNER",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDismiss) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Align cruise card QR barcode or cabin pass inside viewfinder",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF06B6D4), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Viewfinder Target Frame
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                )

                // Laser scan line indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(2.dp)
                        .offset(y = ((scanLineOffset - 0.5f) * 200).dp)
                        .background(Color(0xFF22D3EE))
                )

                Text(
                    text = "📷 CameraX Active",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Sim Buttons for Manual/Test Input
            Text(
                text = "Simulate Hardware Barcode Trigger:",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onQrDetected("""{"pid":"P-1001","card":"RFID9021A"}""") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scan Adult", fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = { onQrDetected("""{"pid":"P-1002","card":"RFID9022B"}""") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scan Minor", fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = { onQrDetected("""{"pid":"P-1003","card":"RFID9023C"}""") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lost Card", fontSize = 11.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Text Code Input
            OutlinedTextField(
                value = mockScannedToken,
                onValueChange = { mockScannedToken = it },
                label = { Text("Manual QR or Barcode String", color = Color(0xFF94A3B8)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF06B6D4),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (mockScannedToken.isNotBlank()) {
                        onQrDetected(mockScannedToken)
                    }
                },
                enabled = mockScannedToken.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Process Code", fontWeight = FontWeight.Bold)
            }
        }
    }
}
