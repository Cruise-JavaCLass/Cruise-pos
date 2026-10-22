package com.example.cruise_pos.ui.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun OpenShiftDialog(
    cashierName: String,
    stationName: String,
    onConfirm: (startingFloat: Double, denominations: List<CashDenomination>) -> Unit,
    onDismiss: () -> Unit
) {
    // State of count per denomination
    val standardDenoms = CashCalculationUtils.STANDARD_USD_DENOMINATIONS
    val countMap = remember { mutableStateMapOf<Double, Int>() }
    
    // Quick lump sum entry option vs denomination breakdown
    var useLumpSum by remember { mutableStateOf(false) }
    var lumpSumText by remember { mutableStateOf("200.00") }

    val calculatedTotal = remember {
        derivedStateOf {
            if (useLumpSum) {
                lumpSumText.toDoubleOrNull() ?: 0.0
            } else {
                val list = countMap.map { CashDenomination(it.key, it.value) }
                CashCalculationUtils.calculateTotal(list)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Open Cashier Shift", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Cashier: $cashierName • Station: $stationName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cash Float Breakdown", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { useLumpSum = !useLumpSum }) {
                        Text(if (useLumpSum) "Breakdown by Bill" else "Enter Lump Sum")
                    }
                }

                if (useLumpSum) {
                    OutlinedTextField(
                        value = lumpSumText,
                        onValueChange = { lumpSumText = it },
                        label = { Text("Starting Cash Float ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    ) {
                        items(standardDenoms) { denom ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
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
                                    modifier = Modifier.width(100.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Text(
                                    text = "$${"%.2f".format((countMap[denom] ?: 0) * denom)}",
                                    modifier = Modifier.width(80.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Starting Float:", fontWeight = FontWeight.Bold)
                    Text(
                        "$${"%.2f".format(calculatedTotal.value)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                    onConfirm(calculatedTotal.value, denominations)
                },
                enabled = calculatedTotal.value >= 0.0
            ) {
                Text("Open Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
