package com.example.cruise_pos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cruise.pos.shared.domain.model.PosStation
import com.example.cruise_pos.ui.auth.PinKeypadSection
import com.example.cruise_pos.ui.auth.StationSelectorDropdown
import org.junit.Rule
import org.junit.Test

class CashierPinLoginUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPinKeypadEntryAndBackspace() {
        var currentPin by mutableStateOf("")

        composeTestRule.setContent {
            PinKeypadSection(
                currentPin = currentPin,
                errorMessage = null,
                onDigitClick = { currentPin += it },
                onBackspaceClick = { if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1) },
                onClearClick = { currentPin = "" }
            )
        }

        // Tap digits 1, 2, 3
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()

        assert(currentPin == "123")

        // Tap backspace
        composeTestRule.onNodeWithText("⌫").performClick()
        assert(currentPin == "12")

        // Tap clear
        composeTestRule.onNodeWithText("C").performClick()
        assert(currentPin.isEmpty())
    }

    @Test
    fun testStationSelectorDisplay() {
        var selectedStation by mutableStateOf(PosStation.DEFAULT_STATIONS[0])

        composeTestRule.setContent {
            StationSelectorDropdown(
                selectedStation = selectedStation,
                onStationSelected = { selectedStation = it }
            )
        }

        composeTestRule.onNodeWithText("ASSIGNED TERMINAL OUTLET").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lido Deck Pool Bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deck 9 • BAR").assertIsDisplayed()
    }
}
