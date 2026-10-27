package com.example.cruise_pos.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.cruise.pos.shared.domain.model.GuestSplitChargeBreakdown

@Composable
fun SequentialGuestTenderDialog(
    isOpen: Boolean,
    currentStepIndex: Int,
    splitBreakdowns: List<GuestSplitChargeBreakdown>,
    isCardTapped: Boolean,
    isSignatureCaptured: Boolean,
    onSimulateNfcTap: () -> Unit,
    onSignCaptured: (signatureBase64: String) -> Unit,
    onConfirmGuestTender: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen || splitBreakdowns.isEmpty() || currentStepIndex !in splitBreakdowns.indices) return

    val currentGuest = splitBreakdowns[currentStepIndex]
    val totalSteps = splitBreakdowns.size

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = modifier
            .fillMaxWidth(0.95f)
            .testTag("sequential_guest_tender_dialog"),
        confirmButton = {
            Button(
                onClick = onConfirmGuestTender,
                enabled = isCardTapped && isSignatureCaptured,
                modifier = Modifier.testTag("btn_confirm_guest_tender"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (currentStepIndex == totalSteps - 1) "Finish All Tenders" else "Next Guest (${currentStepIndex + 2}/$totalSteps)")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("btn_cancel_guest_tender")
            ) {
                Text("Cancel")
            }
        },
        title = {
            Column {
                Text(
                    text = "Guest Tender Step (${currentStepIndex + 1}/$totalSteps)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${currentGuest.guestName} • Cabin ${currentGuest.cabinNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Price Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodySmall)
                            Text("$${"%.2f".format(currentGuest.subtotal)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Maritime Tax", style = MaterialTheme.typography.bodySmall)
                            Text("$${"%.2f".format(currentGuest.taxAmount)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gratuity", style = MaterialTheme.typography.bodySmall)
                            Text("$${"%.2f".format(currentGuest.gratuityAmount)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Amount to Charge", fontWeight = FontWeight.Bold)
                            Text(
                                "$${"%.2f".format(currentGuest.totalCharge)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("guest_charge_amount")
                            )
                        }
                    }
                }

                // Step 1: NFC Cruise Card Tap
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCardTapped) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "1. Cruise Card NFC Tap",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isCardTapped) "Card Verified (Folio: ${currentGuest.folioId})" else "Hold card near back of device",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCardTapped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onSimulateNfcTap,
                            enabled = !isCardTapped,
                            modifier = Modifier.testTag("btn_tap_nfc_card")
                        ) {
                            Text(if (isCardTapped) "Tapped" else "Tap Card")
                        }
                    }
                }

                // Step 2: Signature Capture Pad Mock
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "2. Guest Signature Capture",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .testTag("signature_capture_pad"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSignatureCaptured) {
                                Text(
                                    text = "Signature Received ✓\n${currentGuest.guestName}",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Passenger signature on glass screen",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = {
                                if (com.example.cruise_pos.BuildConfig.DEBUG) {
                                    onSignCaptured("sig_${currentGuest.guestId}_${currentGuest.folioId}")
                                }
                            },
                            enabled = isCardTapped && !isSignatureCaptured,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_capture_signature")
                        ) {
                            Text(if (isSignatureCaptured) "Signed ✓" else "Sign & Authorize")
                        }
                    }
                }
            }
        }
    )
}
