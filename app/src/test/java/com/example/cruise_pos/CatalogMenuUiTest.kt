package com.example.cruise_pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cruise.pos.shared.domain.model.*
import com.example.cruise_pos.ui.catalog.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogMenuUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleCategories = listOf(
        MenuCategory("DRINKS", "Drinks"),
        MenuCategory("FOOD", "Food")
    )

    private val sampleItems = listOf(
        MenuItem(
            id = "item-coffee",
            name = "Americano",
            description = "Hot black coffee",
            categoryId = "DRINKS",
            basePrice = 4.50,
            isAvailable = true,
            modifierGroups = listOf(
                ModifierGroup(
                    id = "size",
                    name = "Cup Size",
                    options = listOf(
                        ModifierOption("regular", "Regular", 0.0, isDefault = true),
                        ModifierOption("large", "Large", 1.0)
                    )
                )
            )
        ),
        MenuItem(
            id = "item-cake",
            name = "Cheesecake",
            description = "New York style cheesecake",
            categoryId = "FOOD",
            basePrice = 7.00,
            isAvailable = false // 86ed
        )
    )

    @Test
    fun testMenuItemCard_regularAnd86edDisplay() {
        var clickedItem: MenuItem? = null
        composeTestRule.setContent {
            MenuItemCard(
                item = sampleItems[0],
                onClick = { clickedItem = it }
            )
        }

        composeTestRule.onNodeWithText("Americano").assertIsDisplayed()
        composeTestRule.onNodeWithText("$4.50").assertIsDisplayed()
        composeTestRule.onNodeWithText("Customize").assertIsDisplayed()
        composeTestRule.onNodeWithText("Americano").performClick()
        assert(clickedItem?.id == "item-coffee")
    }

    @Test
    fun testMenuItemCard_86edDisabledClick() {
        var clicked = false
        composeTestRule.setContent {
            MenuItemCard(
                item = sampleItems[1],
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("86'd • SOLD OUT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cheesecake").performClick()
        assert(!clicked) // Disabled interaction
    }

    @Test
    fun testCatalogSearchBarAndFilterTabs() {
        var search = ""
        var selectedCat: String? = null

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
        assert(selectedCat == "FOOD")
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

        assert(selectedCartItem != null)
        assert(selectedCartItem?.quantity == 2)
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
