package com.example.cruise_pos.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.HardwareScannerConfig
import com.cruise.pos.shared.domain.model.ScanTriggerMode
import com.cruise.pos.shared.domain.model.ScannerDeviceType

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionSelectorCard(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    testTagPrefix: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = selectedOption == option,
                        onClick = { onOptionSelected(option) },
                        label = { Text(labelProvider(option)) },
                        modifier = Modifier.testTag("${testTagPrefix}_${option.toString()}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScannerConfigScreen(
    currentConfig: HardwareScannerConfig = HardwareScannerConfig(),
    deviceType: ScannerDeviceType = currentConfig.enabledDeviceType,
    onSaveConfig: (HardwareScannerConfig) -> Unit,
    onTriggerCalibration: () -> Boolean = { true },
    onNavigateBack: () -> Unit = {}
) {
    var selectedDeviceType by remember(currentConfig) { mutableStateOf(currentConfig.enabledDeviceType) }
    var selectedTriggerMode by remember(currentConfig) { mutableStateOf(currentConfig.triggerMode) }
    var isAudioBeepEnabled by remember(currentConfig) { mutableStateOf(currentConfig.isAudioBeepEnabled) }
    var isHapticVibrationEnabled by remember(currentConfig) { mutableStateOf(currentConfig.isHapticVibrationEnabled) }
    var illuminationLaserLevel by remember(currentConfig) { mutableFloatStateOf(currentConfig.illuminationLaserLevel.toFloat()) }
    var duplicateScanCooldownMillis by remember(currentConfig) { mutableFloatStateOf(currentConfig.duplicateScanCooldownMillis.toFloat()) }
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
            OptionSelectorCard(
                title = "Hardware OEM Profile",
                options = ScannerDeviceType.entries,
                selectedOption = selectedDeviceType,
                onOptionSelected = { selectedDeviceType = it },
                labelProvider = { it.name.replace("_", " ") },
                testTagPrefix = "chip_device"
            )

            // Trigger Mode Selection
            OptionSelectorCard(
                title = "Scan Trigger Mode",
                options = ScanTriggerMode.entries,
                selectedOption = selectedTriggerMode,
                onOptionSelected = { selectedTriggerMode = it },
                labelProvider = { it.name.replace("_", " ") },
                testTagPrefix = "chip_mode"
            )

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
                            checked = isAudioBeepEnabled,
                            onCheckedChange = { isAudioBeepEnabled = it },
                            modifier = Modifier
                                .testTag("switch_audio")
                                .semantics { contentDescription = "Audio Beep Feedback" }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Vibration Feedback")
                        Switch(
                            checked = isHapticVibrationEnabled,
                            onCheckedChange = { isHapticVibrationEnabled = it },
                            modifier = Modifier
                                .testTag("switch_vibration")
                                .semantics { contentDescription = "Haptic Vibration Feedback" }
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
                        text = "Illumination Laser Level: ${illuminationLaserLevel.toInt()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = illuminationLaserLevel,
                        onValueChange = { illuminationLaserLevel = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("slider_laser")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Duplicate Cooldown: ${duplicateScanCooldownMillis.toInt()} ms",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = duplicateScanCooldownMillis,
                        onValueChange = { duplicateScanCooldownMillis = it },
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
                        val ok = onTriggerCalibration()
                        calibrationSuccess = ok
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_calibrate")
                ) {
                    Text("Calibrate Optics")
                }

                Button(
                    onClick = {
                        val newConfig = currentConfig.copy(
                            enabledDeviceType = selectedDeviceType,
                            triggerMode = selectedTriggerMode,
                            isAudioBeepEnabled = isAudioBeepEnabled,
                            isHapticVibrationEnabled = isHapticVibrationEnabled,
                            illuminationLaserLevel = illuminationLaserLevel.toInt().coerceIn(1, 10),
                            duplicateScanCooldownMillis = duplicateScanCooldownMillis.toLong()
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
