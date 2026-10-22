package com.example.cruise_pos.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.CashCalculationUtils
import com.cruise.pos.shared.domain.model.CashDenomination
import com.cruise.pos.shared.domain.model.ShiftRecord

@Composable
fun CloseShiftDialog(
    shift: ShiftRecord,
    expectedCash: Double,
    onConfirmClose: (actualCounted: Double, denominations: List<CashDenomination>, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val standardDenoms = CashCalculationUtils.STANDARD_USD_DENOMINATIONS
    val countMap = remember { mutableStateMapOf<Double, Int>() }
    var closingNote by remember { mutableStateOf("") }
    var useLumpSum by remember { mutableStateOf(false) }
    var lumpSumText by remember { mutableStateOf("") }

    val actualTotal = remember {
        derivedStateOf {
            if (useLumpSum) {
                lumpSumText.toDoubleOrNull() ?: 0.0
            } else {
                val list = countMap.map { CashDenomination(it.key, it.value) }
                CashCalculationUtils.calculateTotal(list)
            }
        }
    }

    val discrepancy = remember {
        derivedStateOf {
            CashCalculationUtils.calculateDiscrepancy(actualTotal.value, expectedCash)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("End of Shift Cash Reconciliation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Shift #${shift.id} • Cashier: ${shift.cashierName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Expected Cash Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("System Expected Cash:", style = MaterialTheme.typography.bodySmall)
                            Text("(Float $${"%.2f".format(shift.startingFloat)} + Cash Sales)", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            "$${"%.2f".format(expectedCash)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recount Cash Drawer", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { useLumpSum = !useLumpSum }) {
                        Text(if (useLumpSum) "Use Bill Breakdown" else "Enter Lump Sum")
                    }
                }

                if (useLumpSum) {
                    OutlinedTextField(
                        value = lumpSumText,
                        onValueChange = { lumpSumText = it },
                        label = { Text("Counted Cash Total ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                    ) {
                        items(standardDenoms) { denom ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (denom >= 1.0) "$${denom.toInt()}" else "${(denom * 100).toInt()}¢",
                                    modifier = Modifier.width(60.dp),
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedTextField(
                                    value = countMap[denom]?.toString() ?: "0",
                                    onValueChange = { newStr ->
                                        val count = newStr.filter { it.isDigit() }.toIntOrNull() ?: 0
                                        countMap[denom] = count
                                    },
                                    modifier = Modifier.width(90.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Text(
                                    text = "$${"%.2f".format((countMap[denom] ?: 0) * denom)}",
                                    modifier = Modifier.width(70.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = closingNote,
                    onValueChange = { closingNote = it },
                    label = { Text("Closing Note (Required if discrepancy)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Discrepancy indicator
                val diff = discrepancy.value
                val diffColor = when {
                    diff == 0.0 -> Color(0xFF2E7D32)
                    diff > 0.0 -> Color(0xFF1565C0)
                    else -> Color(0xFFC62828)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(diffColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Discrepancy (Over / Short):", fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            diff == 0.0 -> "$0.00 (Balanced)"
                            diff > 0.0 -> "+$${"%.2f".format(diff)} (Over)"
                            else -> "-$${"%.2f".format(-diff)} (Short)"
                        },
                        fontWeight = FontWeight.Bold,
                        color = diffColor
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val denominations = if (useLumpSum) {
                        emptyList()
                    } else {
                        countMap.filter { it.value > 0 }.map { CashDenomination(it.key, it.value) }
                    }
                    onConfirmClose(actualTotal.value, denominations, closingNote)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (discrepancy.value != 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Confirm & Close Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
