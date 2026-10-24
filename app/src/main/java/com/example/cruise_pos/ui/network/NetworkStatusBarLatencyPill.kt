package com.example.cruise_pos.ui.network

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.NetworkHealthTier
import com.cruise.pos.shared.domain.model.NetworkQualitySnapshot
import com.cruise.pos.shared.domain.model.SyncGatewayType

internal fun resolvePillBgColor(tier: NetworkHealthTier): Color = when (tier) {
    NetworkHealthTier.EXCELLENT_5GHZ_DECK -> Color(0xFF065F46) // Green
    NetworkHealthTier.DEGRADED_SATELLITE -> Color(0xFF854D0E)  // Yellow/Amber
    NetworkHealthTier.HIGH_LATENCY_SAT -> Color(0xFF9A3412)    // Orange
    NetworkHealthTier.OFFLINE_DEAD_ZONE -> Color(0xFF991B1B)   // Red
}

internal fun resolveDotColor(tier: NetworkHealthTier): Color = when (tier) {
    NetworkHealthTier.EXCELLENT_5GHZ_DECK -> Color(0xFF34D399)
    NetworkHealthTier.DEGRADED_SATELLITE -> Color(0xFFFBBF24)
    NetworkHealthTier.HIGH_LATENCY_SAT -> Color(0xFFFB923C)
    NetworkHealthTier.OFFLINE_DEAD_ZONE -> Color(0xFFF87171)
}

internal fun resolveLabelText(snapshot: NetworkQualitySnapshot): String = when (snapshot.tier) {
    NetworkHealthTier.EXCELLENT_5GHZ_DECK -> "${snapshot.latencyMillis}ms • 5GHz"
    NetworkHealthTier.DEGRADED_SATELLITE -> "${snapshot.latencyMillis}ms • SAT"
    NetworkHealthTier.HIGH_LATENCY_SAT -> when (snapshot.activeGatewayType) {
        SyncGatewayType.SHIP_EDGE_MESH_GATEWAY -> "EDGE MESH • ${snapshot.edgeGatewayHost ?: "LOCAL"}"
        else -> "${snapshot.latencyMillis}ms • SLOW SAT"
    }
    NetworkHealthTier.OFFLINE_DEAD_ZONE -> when (snapshot.activeGatewayType) {
        SyncGatewayType.SHIP_EDGE_MESH_GATEWAY -> "EDGE MESH • ${snapshot.edgeGatewayHost ?: "LOCAL"}"
        else -> "OFFLINE • DEAD ZONE"
    }
}

internal fun resolveBarsFilled(snapshot: NetworkQualitySnapshot): Int = when {
    snapshot.tier == NetworkHealthTier.OFFLINE_DEAD_ZONE && snapshot.activeGatewayType != SyncGatewayType.SHIP_EDGE_MESH_GATEWAY -> 0
    snapshot.rssiDbm >= -65 -> 3
    snapshot.rssiDbm >= -78 -> 2
    snapshot.rssiDbm >= -90 -> 1
    else -> 0
}

@Composable
fun NetworkStatusBarLatencyPill(
    snapshot: NetworkQualitySnapshot,
    modifier: Modifier = Modifier,
    onPillClick: (() -> Unit)? = null
) {
    val pillBgColor by animateColorAsState(
        targetValue = resolvePillBgColor(snapshot.tier),
        label = "pill_color"
    )

    val dotColor by animateColorAsState(
        targetValue = resolveDotColor(snapshot.tier),
        label = "dot_color"
    )

    val labelText = resolveLabelText(snapshot)
    val barsFilled = resolveBarsFilled(snapshot)

    Surface(
        color = pillBgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onPillClick != null) Modifier.clickable(onClick = onPillClick) else Modifier)
            .border(1.dp, dotColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("network_latency_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // Signal Dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Signal Meter bars (3-bar meter derived from RSSI with tier fallback)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(4.dp)
                        .background(if (barsFilled >= 1) dotColor else Color(0xFF475569))
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(7.dp)
                        .background(if (barsFilled >= 2) dotColor else Color(0xFF475569))
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(10.dp)
                        .background(if (barsFilled >= 3) dotColor else Color(0xFF475569))
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = labelText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("network_latency_text")
            )
        }
    }
}
