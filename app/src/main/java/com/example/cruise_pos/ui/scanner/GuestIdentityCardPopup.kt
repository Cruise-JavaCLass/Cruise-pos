package com.example.cruise_pos.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.CardStatus
import com.cruise.pos.shared.domain.model.GuestIdentity

@Composable
fun GuestIdentityCardPopup(
    guest: GuestIdentity,
    onChargeOrder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Card Status & Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card Status Badge
                Surface(
                    color = when (guest.cardStatus) {
                        CardStatus.ACTIVE -> Color(0xFF065F46)
                        CardStatus.REPORTED_LOST -> Color(0xFF991B1B)
                        CardStatus.BLOCKED -> Color(0xFF991B1B)
                        CardStatus.EXPIRED -> Color(0xFF78350F)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = guest.cardStatus.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Row: Avatar + Name + Cabin + Tier
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar placeholder with initials
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .border(2.dp, Color(0xFF06B6D4), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = guest.fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                        color = Color(0xFF22D3EE),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = guest.fullName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cabin: ${guest.cabinNumber}  •  ${guest.packageTier}",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "ID: ${guest.passengerId}  •  RFID: ${guest.rfidCardNumber}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Minor Warning Flag & Alcohol Lock
            if (guest.isMinor) {
                Surface(
                    color = Color(0xFF451A03),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("🔞", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MINOR GUEST DETECTED (Age: ${guest.age})",
                                color = Color(0xFFFDE68A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Alcohol purchase locked by Maritime SOLAS & Ship Policy",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Folio Financial Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Folio Balance", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(
                        text = "\$${guest.folioBalance}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Credit Limit", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(
                        text = "\$${guest.creditLimit}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Available Limit", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(
                        text = "\$${guest.availableSpendingLimit}",
                        color = if (guest.availableSpendingLimit > 0) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }

                Button(
                    onClick = onChargeOrder,
                    enabled = guest.canChargeFolio,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF06B6D4),
                        disabledContainerColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Attach Folio", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
