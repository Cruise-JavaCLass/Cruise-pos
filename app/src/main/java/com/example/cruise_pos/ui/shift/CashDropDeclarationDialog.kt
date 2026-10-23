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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.CashCalculationUtils
import com.cruise.pos.shared.domain.model.CashDenomination
import com.cruise.pos.shared.domain.model.ShiftRecord

/**
 * Mid-shift cash drop declaration form with currency breakdown (KAN-610).
 * Allows roving crew to transfer excess drawer cash to the ship's safe.
 */
@Composable
fun CashDropDeclarationDialog(
    shift: ShiftRecord,
    currentDrawerCash: Double,
    onConfirmDrop: (
        amount: Double,
        witnessId: String,
        witnessPin: String,
        denominations: List<CashDenomination>,
        notes: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val standardDenoms = CashCalculationUtils.STANDARD_USD_DENOMINATIONS
    val countMap = remember { mutableStateMapOf<Double, Int>() }
    var useLumpSum by remember { mutableStateOf(false) }
    var lumpSumText by remember { mutableStateOf("") }
    var witnessId by remember { mutableStateOf("") }
    var witnessPin by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    val remainingDrawerCash = remember(currentDrawerCash) {
        derivedStateOf {
            (currentDrawerCash - calculatedTotal.value).coerceAtLeast(0.0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Mid-Shift Safe Cash Drop",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
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
                    .heightIn(max = 520.dp)
            ) {
                // Drawer info card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Drawer Balance:", style = MaterialTheme.typography.bodySmall)
                            Text("Safe Drop Deductions:", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "$${"%.2f".format(currentDrawerCash)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "-$${"%.2f".format(calculatedTotal.value)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Currency Breakdown", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { useLumpSum = !useLumpSum }) {
                        Text(if (useLumpSum) "Use Bill Breakdown" else "Enter Lump Sum")
                    }
                }

                if (useLumpSum) {
                    OutlinedTextField(
                        value = lumpSumText,
                        onValueChange = { lumpSumText = it },
                        label = { Text("Drop Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                    ) {
                        items(standardDenoms) { denom ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (denom >= 1.0) "$${denom.toInt()}" else "${(denom * 100).toInt()}¢",
                                    modifier = Modifier.width(55.dp),
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedTextField(
                                    value = countMap[denom]?.toString() ?: "0",
                                    onValueChange = { newStr ->
                                        val count = newStr.filter { it.isDigit() }.toIntOrNull() ?: 0
                                        countMap[denom] = count
                                    },
                                    modifier = Modifier.width(85.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Text(
                                    text = "$${"%.2f".format((countMap[denom] ?: 0) * denom)}",
                                    modifier = Modifier.width(65.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Dual Signature Block
                Text(
                    text = "Dual Signature Authorization",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = witnessId,
                        onValueChange = { witnessId = it },
                        label = { Text("Witness/Mgr ID") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = witnessPin,
                        onValueChange = { witnessPin = it },
                        label = { Text("Witness PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g., Drawer threshold limit reached)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Remaining cash summary banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color(0xFF0A2342).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Remaining in Drawer:", fontWeight = FontWeight.Bold)
                    Text(
                        "$${"%.2f".format(remainingDrawerCash.value)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A2342)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = calculatedTotal.value
                    if (!amount.isFinite() || amount <= 0.0) {
                        errorMessage = "Amount must be greater than $0"
                        return@Button
                    }
                    if (amount > currentDrawerCash) {
                        errorMessage = "Drop amount cannot exceed drawer balance ($${currentDrawerCash})"
                        return@Button
                    }
                    if (witnessId.isBlank() || witnessPin.isBlank()) {
                        errorMessage = "Witness/Supervisor ID and PIN are required"
                        return@Button
                    }

                    val denoms = if (useLumpSum) {
                        emptyList()
                    } else {
                        countMap.filter { it.value > 0 }.map { CashDenomination(it.key, it.value) }
                    }

                    onConfirmDrop(amount, witnessId.trim(), witnessPin.trim(), denoms, notes.trim())
                }
            ) {
                Text("Confirm Safe Drop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
