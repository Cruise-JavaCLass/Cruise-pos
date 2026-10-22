package com.example.cruise_pos.ui.compliance

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.ComplianceCheckResult
import com.cruise.pos.shared.domain.model.PolicyViolationType

@Composable
fun SpendingLimitWarningDialog(
    guestName: String,
    cabinNumber: String,
    complianceResult: ComplianceCheckResult,
    onSupervisorOverride: () -> Unit,
    onFallbackToCash: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnderage = complianceResult.violations.contains(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT)
    val isStopCharge = complianceResult.violations.contains(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED)

    val headerColor = when {
        isUnderage -> Color(0xFFEF4444) // Bright red
        isStopCharge -> Color(0xFFF97316) // Strong orange
        else -> Color(0xFFFBBF24) // Warning amber
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(2.dp, headerColor, RoundedCornerShape(20.dp))
            .testTag("spending_limit_warning_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isUnderage) "🔞" else "⚠️",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isUnderage -> "UNDERAGE ALCOHOL RESTRICTION"
                            isStopCharge -> "CABIN FOLIO STOP-CHARGE"
                            else -> "PARENTAL SPENDING LIMIT ALERT"
                        },
                        color = headerColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guest & Cabin banner
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Guest: $guestName",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Cabin: $cabinNumber",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Violation details
            Text(
                text = complianceResult.rejectionMessage ?: "Transaction violates ship guest policy.",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.testTag("compliance_violation_message")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Options: Fallback or Override
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (complianceResult.allowsCashFallback) {
                    Button(
                        onClick = onFallbackToCash,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_fallback_button")
                    ) {
                        Text("💵 Pay with Cash / Credit Card", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (complianceResult.allowsSupervisorOverride) {
                    Button(
                        onClick = onSupervisorOverride,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supervisor_override_button")
                    ) {
                        Text("🔑 Request Supervisor / Manager Override", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_alert_button")
                ) {
                    Text(if (isUnderage) "Remove Prohibited Item" else "Cancel Item")
                }
            }
        }
    }
}
