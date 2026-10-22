package com.example.cruise_pos.ui.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.MenuCategory

@Composable
fun CatalogSearchBarAndCategoryTabs(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categories: List<MenuCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Quick Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search catalog items, cocktails, food...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Category Filter Carousel / Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAllSelected = selectedCategoryId == null || selectedCategoryId.equals("ALL", ignoreCase = true)
            FilterChip(
                selected = isAllSelected,
                onClick = { onCategorySelected(null) },
                label = { Text("All Items") }
            )

            categories.forEach { category ->
                val isSelected = category.id.equals(selectedCategoryId, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category.id) },
                    label = { Text(category.name) }
                )
            }
        }
    }
}
