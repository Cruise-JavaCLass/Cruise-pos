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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentShift?.status == ShiftStatus.OPEN) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
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
                                color = if (currentShift?.status == ShiftStatus.OPEN) Color(0xFF2E7D32) else Color(0xFFC62828),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentShift?.status == ShiftStatus.OPEN) "ACTIVE SHIFT" else "NO ACTIVE SHIFT",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (currentShift?.status == ShiftStatus.OPEN) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (currentShift?.status == ShiftStatus.OPEN) {
                    Text(
                        text = "Cashier: ${currentShift.cashierName} • Station: ${currentShift.stationId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Float: $${"%.2f".format(currentShift.startingFloat)} • Opened at ${currentShift.openedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (currentShift?.status == ShiftStatus.OPEN) {
                Button(
                    onClick = onCloseShiftClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Close Shift")
                }
            } else {
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
