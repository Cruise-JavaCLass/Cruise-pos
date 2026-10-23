package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ModifierOption(
    val id: String,
    val name: String,
    val priceDelta: Double = 0.0,
    val isDefault: Boolean = false
)

@Serializable
data class ModifierGroup(
    val id: String,
    val name: String,
    val minSelections: Int = 0,
    val maxSelections: Int = 1, // 1 for single-choice (e.g. Size, Ice, Sugar), >1 for multi-choice (Toppings)
    val options: List<ModifierOption> = emptyList()
)

@Serializable
data class MenuItem(
    val id: String,
    val name: String,
    val description: String = "",
    val categoryId: String,
    val basePrice: Double,
    val imageUrl: String? = null,
    val isAvailable: Boolean = true, // If false, item is 86'd / out-of-stock
    val modifierGroups: List<ModifierGroup> = emptyList()
) {
    val is86ed: Boolean get() = !isAvailable
}

@Serializable
data class MenuCategory(
    val id: String,
    val name: String,
    val iconName: String? = null,
    val displayOrder: Int = 0
)

@Serializable
data class SelectedModifier(
    val groupId: String,
    val groupName: String,
    val optionId: String,
    val optionName: String,
    val priceDelta: Double
)

@Serializable
data class ConfiguredCartItem(
    val menuItem: MenuItem,
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val quantity: Int = 1
) {
    val unitPrice: Double
        get() = menuItem.basePrice + selectedModifiers.sumOf { it.priceDelta }

    val totalPrice: Double
        get() = unitPrice * quantity
}
