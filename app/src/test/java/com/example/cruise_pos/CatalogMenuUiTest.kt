package com.example.cruise_pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cruise.pos.shared.domain.model.*
import com.example.cruise_pos.ui.catalog.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogMenuUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleCategories = listOf(
        MenuCategory("DRINKS", "Drinks", 2),
        MenuCategory("FOOD", "Food", 1)
    )

    private val sampleModifierGroup = ModifierGroup(
        id = "SIZE",
        name = "Cup Size",
        minSelections = 1,
        maxSelections = 1,
        options = listOf(
            ModifierOption("REG", "Regular", 0.0, true),
            ModifierOption("LRG", "Large", 1.0, false)
        )
    )

    private val sampleItems = listOf(
        MenuItem(
            id = "COFFEE_1",
            categoryId = "DRINKS",
            name = "Americano",
            description = "Espresso with hot water",
            basePrice = 4.50,
            isAvailable = true,
            modifierGroups = listOf(sampleModifierGroup)
        ),
        MenuItem(
            id = "CAKE_1",
            categoryId = "FOOD",
            name = "Cheesecake",
            description = "New York cheesecake",
            basePrice = 6.00,
            isAvailable = false, // 86ed
            modifierGroups = emptyList()
        )
    )

    @Test
    fun testMenuItemCard_availableRendersCorrectly() {
        var clicked = false
        composeTestRule.setContent {
            MenuItemCard(
                item = sampleItems[0],
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("Americano").assertIsDisplayed()
        composeTestRule.onNodeWithText("$4.50").assertIsDisplayed()
        composeTestRule.onNodeWithText("Espresso with hot water").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_item_card_COFFEE_1").performClick()
        assert(clicked)
    }

    @Test
    fun testMenuItemCard_outOfStockRendersDisabled() {
        var clicked = false
        composeTestRule.setContent {
            MenuItemCard(
                item = sampleItems[1],
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("Cheesecake").assertIsDisplayed()
        composeTestRule.onNodeWithText("86ed • Out of Stock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_item_card_CAKE_1").performClick()
        assert(!clicked)
    }

    @Test
    fun testCatalogSearchBarAndCategoryTabs_searchAndSelect() {
        var search = ""
        var selectedCat: String? = "ALL"

        composeTestRule.setContent {
            CatalogSearchBarAndCategoryTabs(
                searchQuery = search,
                onSearchQueryChange = { search = it },
                categories = sampleCategories,
                selectedCategoryId = selectedCat,
                onCategorySelected = { selectedCat = it }
            )
        }

        composeTestRule.onNodeWithText("All Items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drinks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food").performClick()
        assertEquals("FOOD", selectedCat)
    }

    @Test
    fun testModifierSelectionBottomSheet_selectionFlow() {
        var selectedCartItem: ConfiguredCartItem? = null

        composeTestRule.setContent {
            ModifierSelectionBottomSheet(
                menuItem = sampleItems[0],
                onConfirmSelection = { selectedCartItem = it },
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithText("Cup Size").assertIsDisplayed()
        composeTestRule.onNodeWithText("Large").performClick()
        composeTestRule.onNodeWithText("+").performClick() // Increase qty to 2
        composeTestRule.onNodeWithText("Add to Cart • $11.00").performClick() // (4.50 + 1.00) * 2 = 11.00

        assertNotNull(selectedCartItem)
        assertEquals(2, selectedCartItem?.quantity)
        assertEquals("LRG", selectedCartItem?.selectedModifiers?.firstOrNull()?.optionId)
        assertEquals(11.00, selectedCartItem?.totalPrice ?: 0.0, 0.001)
    }

    @Test
    fun testModifierSelectionBottomSheet_minSelectionsBlocksConfirmation() {
        val noDefaultGroup = ModifierGroup(
            id = "SUGAR",
            name = "Sugar Level",
            minSelections = 1,
            maxSelections = 1,
            options = listOf(
                ModifierOption("NO_SUGAR", "0% Sugar", 0.0, false),
                ModifierOption("HALF_SUGAR", "50% Sugar", 0.0, false)
            )
        )
        val itemWithMandatoryGroup = sampleItems[0].copy(
            modifierGroups = listOf(noDefaultGroup)
        )

        composeTestRule.setContent {
            ModifierSelectionBottomSheet(
                menuItem = itemWithMandatoryGroup,
                onConfirmSelection = {},
                onDismiss = {}
            )
        }

        // Button should be disabled until selection made
        composeTestRule.onNodeWithText("Add to Cart • $4.50").assertIsNotEnabled()
        composeTestRule.onNodeWithText("0% Sugar").performClick()
        composeTestRule.onNodeWithText("Add to Cart • $4.50").assertIsEnabled()
    }

    @Test
    fun testCatalogMenuScreen_fullGridDisplay() {
        var addedItem: ConfiguredCartItem? = null

        composeTestRule.setContent {
            CatalogMenuScreen(
                categories = sampleCategories,
                menuItems = sampleItems,
                onAddToCart = { addedItem = it }
            )
        }

        composeTestRule.onNodeWithTag("catalog_menu_grid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Americano").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cheesecake").assertIsDisplayed()
    }
}
