package com.example.cruise_pos.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.PosStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSelectorDropdown(
    stations: List<PosStation> = PosStation.DEFAULT_STATIONS,
    selectedStation: PosStation,
    onStationSelected: (PosStation) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "ASSIGNED TERMINAL OUTLET",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = selectedStation.stationName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deck ${selectedStation.deckNumber} • ${selectedStation.outletCategory}",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF0F172A))
        ) {
            stations.forEach { station ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = station.stationName,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Deck ${station.deckNumber} • ${station.outletCategory}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    },
                    onClick = {
                        onStationSelected(station)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (station.stationId == selectedStation.stationId) Color(0xFF1E293B) else Color.Transparent
                    )
                )
            }
        }
    }
}
