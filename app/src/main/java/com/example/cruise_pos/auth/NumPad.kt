package com.example.cruise_pos.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cruise_pos.ui.theme.CruiseposTheme

private val NUM_PAD_KEYS = listOf(
    "1", "2", "3",
    "4", "5", "6",
    "7", "8", "9",
    "*", "0", "⌫"
)

@Composable
fun NumPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(NUM_PAD_KEYS) { key ->
            ElevatedButton(
                onClick = {
                    when (key) {
                        "⌫" -> onBackspace()
                        "*" -> { /* star key — no-op in PIN mode */ }
                        else -> onDigit(key)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
            ) {
                Text(
                    text = key,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NumPadPreview() {
    CruiseposTheme {
        NumPad(
            onDigit = {},
            onBackspace = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
