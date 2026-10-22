package com.example.cruise_pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cruise.pos.shared.domain.model.ShiftRecord
import com.cruise.pos.shared.domain.model.ShiftStatus
import com.example.cruise_pos.ui.shift.ActiveShiftStateBanner
import com.example.cruise_pos.ui.shift.CloseShiftDialog
import com.example.cruise_pos.ui.shift.OpenShiftDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying shift UI lifecycle transitions on device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class ShiftLifecycleInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.cruise_pos", appContext.packageName)
    }

    @Test
    fun testShiftLifecycleFlow() {
        var openShiftTriggered = false
        var closeShiftTriggered = false

        // Phase 1: Verify Initial state banner with no active shift
        composeTestRule.setContent {
            ActiveShiftStateBanner(
                currentShift = null,
                onOpenShiftClick = { openShiftTriggered = true },
                onCloseShiftClick = {}
            )
        }
        composeTestRule.onNodeWithText("NO ACTIVE SHIFT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Shift").performClick()
        assertTrue(openShiftTriggered)

        // Phase 2: Active shift state banner
        val activeShift = ShiftRecord(
            id = "shift-device-99",
            cashierId = 5,
            cashierName = "Test Cashier",
            stationId = "STATION_DECK_4",
            status = ShiftStatus.OPEN,
            startingFloat = 300.0,
            openedAt = "2026-10-22T08:00:00Z"
        )

        composeTestRule.setContent {
            ActiveShiftStateBanner(
                currentShift = activeShift,
                onOpenShiftClick = {},
                onCloseShiftClick = { closeShiftTriggered = true }
            )
        }
        composeTestRule.onNodeWithText("ACTIVE SHIFT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close Shift").performClick()
        assertTrue(closeShiftTriggered)
    }
}
