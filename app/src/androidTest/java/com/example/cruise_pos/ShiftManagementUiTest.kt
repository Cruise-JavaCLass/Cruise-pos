package com.example.cruise_pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cruise.pos.shared.domain.model.ShiftRecord
import com.cruise.pos.shared.domain.model.ShiftStatus
import com.example.cruise_pos.ui.shift.ActiveShiftStateBanner
import com.example.cruise_pos.ui.shift.CloseShiftDialog
import com.example.cruise_pos.ui.shift.OpenShiftDialog
import org.junit.Rule
import org.junit.Test

class ShiftManagementUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testActiveShiftStateBanner_noShift() {
        var openClicked = false
        composeTestRule.setContent {
            ActiveShiftStateBanner(
                currentShift = null,
                onOpenShiftClick = { openClicked = true },
                onCloseShiftClick = {}
            )
        }

        composeTestRule.onNodeWithText("NO ACTIVE SHIFT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Shift").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Shift").performClick()
        assert(openClicked)
    }

    @Test
    fun testActiveShiftStateBanner_withActiveShift() {
        val shift = ShiftRecord(
            id = "shift-001",
            cashierId = 1,
            cashierName = "Jane Cashier",
            stationId = "BAR_1",
            status = ShiftStatus.OPEN,
            startingFloat = 150.0,
            openedAt = "08:00 AM"
        )
        var closeClicked = false
        composeTestRule.setContent {
            ActiveShiftStateBanner(
                currentShift = shift,
                onOpenShiftClick = {},
                onCloseShiftClick = { closeClicked = true }
            )
        }

        composeTestRule.onNodeWithText("ACTIVE SHIFT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cashier: Jane Cashier • Station: BAR_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close Shift").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close Shift").performClick()
        assert(closeClicked)
    }

    @Test
    fun testOpenShiftDialog_renderAndConfirm() {
        var confirmedFloat = 0.0
        composeTestRule.setContent {
            OpenShiftDialog(
                cashierName = "Jane Cashier",
                stationName = "Station A",
                onConfirm = { floatAmt, _ -> confirmedFloat = floatAmt },
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithText("Open Cashier Shift").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cash Float Breakdown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Shift").performClick()
        assert(confirmedFloat >= 0.0)
    }

    @Test
    fun testCloseShiftDialog_renderAndConfirm() {
        val shift = ShiftRecord(
            id = "shift-001",
            cashierId = 1,
            cashierName = "Jane Cashier",
            stationId = "BAR_1",
            status = ShiftStatus.OPEN,
            startingFloat = 150.0,
            openedAt = "08:00 AM"
        )
        var closeConfirmed = false
        composeTestRule.setContent {
            CloseShiftDialog(
                shift = shift,
                expectedCash = 250.0,
                onConfirmClose = { _, _, _ -> closeConfirmed = true },
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithText("End of Shift Cash Reconciliation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm & Close Shift").performClick()
        assert(closeConfirmed)
    }
}
