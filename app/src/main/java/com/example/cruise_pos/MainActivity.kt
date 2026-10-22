package com.example.cruise_pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cruise_pos.auth.AuthMethod
import com.example.cruise_pos.auth.SupervisorAuthModal
import com.example.cruise_pos.ui.theme.CruiseposTheme

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
fun MainScreen(modifier: Modifier = Modifier) {
    var showAuthModal by remember { mutableStateOf(false) }
    var authResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🚢 Cruise POS",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = CruiseNavy
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { showAuthModal = true },
            colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy)
        ) {
            Text("Test Supervisor Auth")
        }

        if (authResult != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "✅ Authorized",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color(0xFF27AE60),
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = authResult!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
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
