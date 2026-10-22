package com.example.cruise_pos.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.*

@Composable
fun CatalogMenuScreen(
    categories: List<MenuCategory>,
    menuItems: List<MenuItem>,
    onAddToCart: (ConfiguredCartItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var itemForCustomizationId by rememberSaveable { mutableStateOf<String?>(null) }

    val itemForCustomization = remember(itemForCustomizationId, menuItems) {
        itemForCustomizationId?.let { id ->
            menuItems.firstOrNull { it.id == id && it.isAvailable }
        }
    }

    val filteredItems = remember(menuItems, selectedCategoryId, searchQuery) {
        CatalogFilterUtils.filterItems(menuItems, selectedCategoryId, searchQuery)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search & Category Filters
        CatalogSearchBarAndCategoryTabs(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { selectedCategoryId = it }
        )

        // Catalog Grid
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No menu items found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("catalog_menu_grid")
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    MenuItemCard(
                        item = item,
                        onClick = {
                            if (item.modifierGroups.isNotEmpty()) {
                                itemForCustomizationId = item.id
                            } else {
                                onAddToCart(ConfiguredCartItem(menuItem = item, quantity = 1))
                            }
                        }
                    )
                }
            }
        }
    }

    // Modifier Customization BottomSheet
    itemForCustomization?.let { item ->
        key(item.id) {
            ModifierSelectionBottomSheet(
                menuItem = item,
                onConfirmSelection = { configuredItem ->
                    onAddToCart(configuredItem)
                    itemForCustomizationId = null
                },
                onDismiss = { itemForCustomizationId = null }
            )
        }
    }
}
