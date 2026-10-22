package com.example.cruise_pos.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.ShiftRecord
import com.cruise.pos.shared.domain.model.ShiftStatus

@Composable
fun ActiveShiftStateBanner(
    currentShift: ShiftRecord?,
    onOpenShiftClick: () -> Unit,
    onCloseShiftClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openShift = currentShift?.takeIf { it.status == ShiftStatus.OPEN }
    val isSuspended = currentShift?.status == ShiftStatus.SUSPENDED

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                openShift != null -> MaterialTheme.colorScheme.primaryContainer
                isSuspended -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when {
                                    openShift != null -> Color(0xFF2E7D32)
                                    isSuspended -> Color(0xFFE65100)
                                    else -> Color(0xFFC62828)
                                },
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            openShift != null -> "ACTIVE SHIFT"
                            isSuspended -> "SHIFT SUSPENDED"
                            else -> "NO ACTIVE SHIFT"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            openShift != null -> MaterialTheme.colorScheme.onPrimaryContainer
                            isSuspended -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (openShift != null) {
                    Text(
                        text = "Cashier: ${openShift.cashierName} • Station: ${openShift.stationId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Float: $${"%.2f".format(openShift.startingFloat)} • Opened at ${openShift.openedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isSuspended) {
                    Text(
                        text = "Shift is suspended for station audit or cashier break. Resume before processing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    Text(
                        text = "Shift is required before processing transactions & card charges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (openShift != null) {
                Button(
                    onClick = onCloseShiftClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Close Shift")
                }
            } else if (!isSuspended) {
                Button(
                    onClick = onOpenShiftClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Open Shift")
                }
            }
        }
    }
}
