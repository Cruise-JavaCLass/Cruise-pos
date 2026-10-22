package com.cruise.pos.shared.domain.model

object CatalogFilterUtils {

    /**
     * Filters menu items by categoryId and query text (case-insensitive search on name and description).
     * If categoryId is null or "ALL", items across all categories are considered.
     */
    fun filterItems(
        items: List<MenuItem>,
        selectedCategoryId: String?,
        query: String
    ): List<MenuItem> {
        val trimmedQuery = query.trim().lowercase()

        return items.filter { item ->
            val matchesCategory = selectedCategoryId == null ||
                    selectedCategoryId.equals("ALL", ignoreCase = true) ||
                    item.categoryId.equals(selectedCategoryId, ignoreCase = true)

            val matchesQuery = trimmedQuery.isEmpty() ||
                    item.name.lowercase().contains(trimmedQuery) ||
                    item.description.lowercase().contains(trimmedQuery)

            matchesCategory && matchesQuery
        }
    }

    /**
     * Calculates configured item total price with base price, modifiers and quantity.
     */
    fun calculateConfiguredPrice(
        basePrice: Double,
        selectedModifiers: List<SelectedModifier>,
        quantity: Int = 1
    ): Double {
        val modifiersSum = selectedModifiers.sumOf { it.priceDelta }
        val unit = basePrice + modifiersSum
        val total = unit * quantity
        return kotlin.math.round(total * 100.0) / 100.0
    }
}
