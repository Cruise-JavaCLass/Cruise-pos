package com.example.cruise_pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.data.repository.ShiftRepository
import com.cruise.pos.shared.data.repository.ShiftRepositoryImpl
import com.cruise.pos.shared.domain.model.CashDenomination
import com.cruise.pos.shared.domain.model.CashDropRequest
import com.cruise.pos.shared.domain.model.ShiftRecord
import com.cruise.pos.shared.domain.model.ShiftStatus
import com.example.cruise_pos.auth.AuthMethod
import com.example.cruise_pos.auth.SupervisorAuthModal
import com.example.cruise_pos.ui.shift.CashDropDeclarationDialog
import com.example.cruise_pos.ui.theme.CruiseposTheme
import kotlinx.coroutines.launch

private val CruiseNavy = Color(0xFF0A2342)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CruiseposTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    shiftRepository: ShiftRepository = remember { ShiftRepositoryImpl() }
) {
    var showAuthModal by remember { mutableStateOf(false) }
    var authResult by remember { mutableStateOf<String?>(null) }

    var showCashDropDialog by remember { mutableStateOf(false) }
    var dropResult by remember { mutableStateOf<String?>(null) }
    var currentDrawerCash by remember { mutableStateOf(1450.00) }
    var isSubmittingDrop by remember { mutableStateOf(false) }
    var dropErrorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val activeShift = remember {
        ShiftRecord(
            id = "shift-pool-101",
            cashierId = 12L,
            cashierName = "Roving Crew (Deck 9)",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 300.0,
            openedAt = "2026-10-22T08:00:00Z"
        )
    }

    LaunchedEffect(activeShift) {
        shiftRepository.setCurrentShift(activeShift)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🚢 Cruise POS Terminal",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = CruiseNavy
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Station: ${activeShift.stationId}", fontWeight = FontWeight.SemiBold)
                Text("Cashier: ${activeShift.cashierName}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Current Drawer Cash: $${"%.2f".format(currentDrawerCash)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("(Safe drop recommended when > $1000.00)", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = { showCashDropDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy)
            ) {
                Text("Perform Cash Drop")
            }

            Button(
                onClick = { showAuthModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Supervisor Auth")
            }
        }

        if (dropResult != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF27AE60).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Safe Drop Completed",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF27AE60)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = dropResult!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (authResult != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "✅ Authorized: $authResult",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF27AE60),
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }

    if (showCashDropDialog) {
        CashDropDeclarationDialog(
            shift = activeShift,
            currentDrawerCash = currentDrawerCash,
            isSubmitting = isSubmittingDrop,
            serverErrorMessage = dropErrorMessage,
            onConfirmDrop = { amount, witnessId, witnessPin, denoms, notes ->
                if (isSubmittingDrop) return@CashDropDeclarationDialog
                isSubmittingDrop = true
                dropErrorMessage = null
                coroutineScope.launch {
                    val request = CashDropRequest(
                        shiftId = activeShift.id,
                        amount = amount,
                        cashierId = activeShift.cashierId.toString(),
                        witnessId = witnessId,
                        witnessPin = witnessPin,
                        denominations = denoms,
                        notes = notes.ifBlank { null }
                    )
                    when (val result = shiftRepository.recordCashDrop(request)) {
                        is ApiResult.Success -> {
                            currentDrawerCash -= amount
                            dropResult = "Transferred $${"%.2f".format(amount)} to Ship Safe (Receipt: ${result.data.receiptNumber}, Witness: $witnessId). Notes: $notes"
                            isSubmittingDrop = false
                            dropErrorMessage = null
                            showCashDropDialog = false
                        }
                        is ApiResult.Error -> {
                            dropErrorMessage = result.message
                            // Do not report completion or deduct drawer cash on failure
                            isSubmittingDrop = false
                        }
                    }
                }
            },
            onDismiss = {
                if (!isSubmittingDrop) {
                    dropErrorMessage = null
                    showCashDropDialog = false
                }
            }
        )
    }

    if (showAuthModal) {
        SupervisorAuthModal(
            onDismiss = { showAuthModal = false },
            onAuthenticated = { managerId, method ->
                authResult = "Manager: $managerId | Method: ${method.name}"
                showAuthModal = false
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CruiseposTheme {
        MainScreen()
    }
}
