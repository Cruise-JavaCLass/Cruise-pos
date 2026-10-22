package com.example.cruise_pos.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cruise_pos.ui.theme.CruiseposTheme

private val CruiseNavy = Color(0xFF0A2342)
private val CruiseRed = Color(0xFFC0392B)

@Composable
fun SupervisorAuthModal(
    onDismiss: () -> Unit,
    onAuthenticated: (managerId: String, authMethod: AuthMethod) -> Unit,
    viewModel: SupervisorAuthViewModel = remember { SupervisorAuthViewModel() }
) {
    val state by viewModel.state.collectAsState()

    // When authentication succeeds, notify caller and dismiss
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onAuthenticated(state.managerId, state.authMethod)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = {
        viewModel.reset()
        onDismiss()
    }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {

                // ── Header ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🚢  Supervisor Authorization",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CruiseNavy,
                            fontSize = 18.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // ── Tab Row ───────────────────────────────────────────────
                val tabIndex = if (state.authMethod == AuthMethod.PIN) 0 else 1
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { viewModel.onAuthMethodChange(AuthMethod.PIN) },
                        text = { Text("Manager PIN") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { viewModel.onAuthMethodChange(AuthMethod.BIOMETRIC) },
                        text = { Text("Biometric") }
                    )
                }

                // ── Tab Content ───────────────────────────────────────────
                when (state.authMethod) {
                    AuthMethod.PIN -> PinTabContent(
                        state = state,
                        onManagerIdChange = viewModel::onManagerIdChange,
                        onDigit = viewModel::onPinDigitEntered,
                        onBackspace = viewModel::onPinBackspace
                    )
                    AuthMethod.BIOMETRIC -> BiometricTabContent(
                        onAuthenticate = viewModel::onBiometricAuth
                    )
                }

                // ── Error ─────────────────────────────────────────────────
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ── Bottom Actions ────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        viewModel.reset()
                        onDismiss()
                    }) {
                        Text("Cancel", color = CruiseRed)
                    }
                    if (state.authMethod == AuthMethod.PIN) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.verifyPin() },
                            enabled = state.pinInput.length == 4 && state.managerId.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy)
                        ) {
                            Text("Submit")
                        }
                    }
                }
            }
        }
    }
}

// ── PIN Tab ───────────────────────────────────────────────────────────────────

@Composable
private fun PinTabContent(
    state: SupervisorAuthState,
    onManagerIdChange: (String) -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Manager ID input
        OutlinedTextField(
            value = state.managerId,
            onValueChange = onManagerIdChange,
            label = { Text("Manager ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. MGR-01") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PIN dot display
        PinDotRow(filledCount = state.pinInput.length)

        Spacer(modifier = Modifier.height(16.dp))

        // Numeric keypad
        NumPad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PinDotRow(filledCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            Text(
                text = if (index < filledCount) "●" else "○",
                fontSize = 28.sp,
                color = if (index < filledCount) CruiseNavy else Color.LightGray
            )
        }
    }
}

// ── Biometric Tab ─────────────────────────────────────────────────────────────

@Composable
private fun BiometricTabContent(onAuthenticate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "☝",
            fontSize = 72.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Tap to authenticate with biometrics",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onAuthenticate,
            colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Authenticate", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SupervisorAuthModalPinPreview() {
    CruiseposTheme {
        SupervisorAuthModal(
            onDismiss = {},
            onAuthenticated = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SupervisorAuthModalBiometricPreview() {
    CruiseposTheme {
        val vm = remember {
            SupervisorAuthViewModel().apply {
                onAuthMethodChange(AuthMethod.BIOMETRIC)
            }
        }
        SupervisorAuthModal(
            onDismiss = {},
            onAuthenticated = { _, _ -> },
            viewModel = vm
        )
    }
}
