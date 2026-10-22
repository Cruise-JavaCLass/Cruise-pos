package com.example.cruise_pos.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.HardwareScannerConfig
import com.cruise.pos.shared.domain.model.ScanTriggerMode
import com.cruise.pos.shared.domain.model.ScannerDeviceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScannerConfigScreen(
    currentConfig: HardwareScannerConfig = HardwareScannerConfig(),
    deviceType: ScannerDeviceType = ScannerDeviceType.AUTO_DETECT,
    onSaveConfig: (HardwareScannerConfig) -> Unit = {},
    onTriggerCalibration: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var selectedDeviceType by remember { mutableStateOf(deviceType) }
    var selectedTriggerMode by remember { mutableStateOf(currentConfig.triggerMode) }
    var audioFeedback by remember { mutableStateOf(currentConfig.audioFeedback) }
    var vibrationFeedback by remember { mutableStateOf(currentConfig.vibrationFeedback) }
    var laserAimIntensity by remember { mutableFloatStateOf(currentConfig.laserAimIntensity) }
    var duplicateCooldownMs by remember { mutableFloatStateOf(currentConfig.duplicateCooldownMs.toFloat()) }
    var calibrationSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Scanner Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scanner Device Type
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hardware OEM Profile",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScannerDeviceType.values().forEach { type ->
                            FilterChip(
                                selected = selectedDeviceType == type,
                                onClick = { selectedDeviceType = type },
                                label = { Text(type.name.replace("_", " ")) },
                                modifier = Modifier.testTag("chip_device_${type.name}")
                            )
                        }
                    }
                }
            }

            // Trigger Mode Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Scan Trigger Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanTriggerMode.values().forEach { mode ->
                            FilterChip(
                                selected = selectedTriggerMode == mode,
                                onClick = { selectedTriggerMode = mode },
                                label = { Text(mode.name.replace("_", " ")) },
                                modifier = Modifier.testTag("chip_mode_${mode.name}")
                            )
                        }
                    }
                }
            }

            // Audio & Haptic Feedback Toggles
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audio & Haptic Feedback",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio Beep Feedback")
                        Switch(
                            checked = audioFeedback,
                            onCheckedChange = { audioFeedback = it },
                            modifier = Modifier.testTag("switch_audio")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Vibration Feedback")
                        Switch(
                            checked = vibrationFeedback,
                            onCheckedChange = { vibrationFeedback = it },
                            modifier = Modifier.testTag("switch_vibration")
                        )
                    }
                }
            }

            // Laser Intensity & Calibration Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Illumination & Laser Aim Intensity: ${(laserAimIntensity * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = laserAimIntensity,
                        onValueChange = { laserAimIntensity = it },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.testTag("slider_laser")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Duplicate Cooldown: ${duplicateCooldownMs.toInt()} ms",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = duplicateCooldownMs,
                        onValueChange = { duplicateCooldownMs = it },
                        valueRange = 50f..1000f,
                        modifier = Modifier.testTag("slider_cooldown")
                    )
                }
            }

            // Scanner Calibration Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onTriggerCalibration()
                        calibrationSuccess = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_calibrate")
                ) {
                    Text("Calibrate Optics")
                }

                Button(
                    onClick = {
                        val newConfig = HardwareScannerConfig(
                            triggerMode = selectedTriggerMode,
                            audioFeedback = audioFeedback,
                            vibrationFeedback = vibrationFeedback,
                            laserAimIntensity = laserAimIntensity,
                            duplicateCooldownMs = duplicateCooldownMs.toLong()
                        )
                        onSaveConfig(newConfig)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_save_config")
                ) {
                    Text("Apply & Save")
                }
            }

            if (calibrationSuccess) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Hardware optics calibrated successfully.")
                }
            }
        }
    }
}
