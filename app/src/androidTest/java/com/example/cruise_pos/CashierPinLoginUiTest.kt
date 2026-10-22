package com.example.cruise_pos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cruise.pos.shared.domain.model.PosStation
import com.example.cruise_pos.ui.auth.PinKeypadSection
import com.example.cruise_pos.ui.auth.StationSelectorDropdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        assertEquals("123", currentPin)

        // Tap backspace
        composeTestRule.onNodeWithText("⌫").performClick()
        assertEquals("12", currentPin)

        // Tap clear
        composeTestRule.onNodeWithText("C").performClick()
        assertTrue(currentPin.isEmpty())
    }

    @Test
    fun testStationSelectorDisplayAndSelection() {
        var selectedStation by mutableStateOf(PosStation.DEFAULT_STATIONS[0])

        composeTestRule.setContent {
            StationSelectorDropdown(
                selectedStation = selectedStation,
                onStationSelected = { selectedStation = it }
            )
        }

        // Verify initial station display
        composeTestRule.onNodeWithText("ASSIGNED TERMINAL OUTLET").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lido Deck Pool Bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deck 9 • BAR").assertIsDisplayed()

        // Open dropdown by clicking current selection
        composeTestRule.onNodeWithText("Lido Deck Pool Bar").performClick()

        // Select a different station (e.g. Ocean View Dining Room)
        val targetStation = PosStation.DEFAULT_STATIONS[1]
        composeTestRule.onNodeWithText(targetStation.stationName).performClick()

        // Assert that state and UI updated
        assertEquals(targetStation, selectedStation)
        composeTestRule.onNodeWithText(targetStation.stationName).assertIsDisplayed()
        composeTestRule.onNodeWithText("Deck ${targetStation.deckNumber} • ${targetStation.outletCategory}").assertIsDisplayed()
    }
}
