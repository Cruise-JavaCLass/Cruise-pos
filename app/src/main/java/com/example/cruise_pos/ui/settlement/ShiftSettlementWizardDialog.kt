package com.example.cruise_pos.ui.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.BlindDropDeclaration
import com.cruise.pos.shared.domain.model.ShiftXReport
import com.cruise.pos.shared.domain.model.ShiftZReport
import com.cruise.pos.shared.domain.service.ShiftSettlementService

@Composable
fun ShiftSettlementWizardDialog(
    xReport: ShiftXReport,
    settlementService: ShiftSettlementService = remember { ShiftSettlementService() },
    onZReportFinalized: (ShiftZReport) -> Unit,
    onPrintReceipt: (ShiftZReport) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableIntStateOf(1) } // Step 1: X-Report Review, Step 2: Blind Drop Input, Step 3: Z-Report Final
    var countedCashText by rememberSaveable { mutableStateOf("") }
    var witnessName by rememberSaveable { mutableStateOf("") }
    var supervisorPinText by rememberSaveable { mutableStateOf("") }
    var finalizedZReport by remember { mutableStateOf<ShiftZReport?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            .testTag("settlement_wizard_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (step) {
                            1 -> "SHIFT SUMMARY (X-REPORT)"
                            2 -> "BLIND DROP CASH AUDIT"
                            else -> "FINAL SHIFT SETTLEMENT (Z-REPORT)"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Station: ${xReport.stationId} • Cashier: ${xReport.cashierName}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (step) {
                1 -> {
                    // STEP 1: X-Report Live Breakdown
                    Text("Interim Revenue by Payment Method:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    for (pb in xReport.paymentBreakdowns) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${pb.method.name} (${pb.transactionCount})", color = Color.White, fontSize = 13.sp)
                                Text("$${"%.2f".format(pb.netSales)}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Summary box
                    Surface(
                        color = Color(0xFF0369A1).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Net Revenue:", color = Color.White, fontSize = 13.sp)
                                Text("$${"%.2f".format(xReport.totalNetRevenue)}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Starting Float:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text("$${"%.2f".format(xReport.startingCashFloat)}", color = Color.White, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mid-Shift Drops:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text("-$${"%.2f".format(xReport.totalCashDrops)}", color = Color(0xFFF87171), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { step = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proceed_to_blind_drop_button")
                    ) {
                        Text("Proceed to Blind Cash Drop (Close Shift)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                2 -> {
                    // STEP 2: Blind Drop Cash Input
                    Text(
                        text = "Blind Count: Enter actual physical cash in drawer without system prompting.",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = countedCashText,
                        onValueChange = { countedCashText = it },
                        label = { Text("Actual Counted Cash ($)", color = Color(0xFF94A3B8)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("counted_cash_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = witnessName,
                        onValueChange = { witnessName = it },
                        label = { Text("Witness Crew Name (Optional)", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage ?: "", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val parsed = countedCashText.toDoubleOrNull()
                            if (parsed == null || parsed < 0.0) {
                                errorMessage = "Please enter a valid cash amount"
                                return@Button
                            }

                            val drop = BlindDropDeclaration(parsed, witnessName.trim().takeIf { it.isNotBlank() })
                            val z = settlementService.generateFinalZReport(xReport, drop)
                            finalizedZReport = z
                            step = 3
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_blind_drop_button")
                    ) {
                        Text("Finalize & Compare Drawer Balance", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                3 -> {
                    // STEP 3: Z-Report Results & Receipt Printing
                    val z = finalizedZReport
                    if (z != null) {
                        val isOverShort = z.requiresSupervisorApproval

                        Surface(
                            color = if (z.isBalanced) Color(0xFF065F46) else Color(0xFF7F1D1D),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (z.isBalanced) "DRAWER BALANCED ✅" else "DISCREPANCY DETECTED ⚠️",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Expected: $${"%.2f".format(z.expectedCashInDrawer)}  •  Counted: $${"%.2f".format(z.actualCountedCash)}", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                                Text("Variance: $${"%.2f".format(z.cashDiscrepancy)}", color = if (z.isBalanced) Color(0xFF34D399) else Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        if (z.requiresSupervisorApproval && !z.isOverShortApproved) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Discrepancy exceeds threshold. Supervisor PIN required to approve closure.", color = Color(0xFFFBBF24), fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = supervisorPinText,
                                onValueChange = { supervisorPinText = it },
                                label = { Text("Supervisor PIN", color = Color(0xFF94A3B8)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { onPrintReceipt(z) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("print_z_report_button")
                        ) {
                            Text("🖨️ Print Settlement Slip (ESC/POS)", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onZReportFinalized(z.copy(supervisorOverridePin = supervisorPinText.takeIf { it.isNotBlank() }))
                            },
                            enabled = !z.requiresSupervisorApproval || z.isOverShortApproved || supervisorPinText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("close_shift_complete_button")
                        ) {
                            Text("Close Shift & Sync Ledger", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
