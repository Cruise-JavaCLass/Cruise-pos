package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogFilteringAndPricingTest {

    private val sampleItems = listOf(
        MenuItem(
            id = "item-1",
            name = "Mojito Classic",
            description = "White rum, fresh mint, lime juice, soda",
            categoryId = "COCKTAILS",
            basePrice = 12.50,
            isAvailable = true
        ),
        MenuItem(
            id = "item-2",
            name = "Piña Colada",
            description = "Rum, coconut cream, pineapple juice",
            categoryId = "COCKTAILS",
            basePrice = 14.00,
            isAvailable = false // 86ed out of stock
        ),
        MenuItem(
            id = "item-3",
            name = "Iced Caramel Macchiato",
            description = "Espresso, vanilla syrup, steamed milk, caramel drizzle",
            categoryId = "COFFEE",
            basePrice = 6.50,
            isAvailable = true
        ),
        MenuItem(
            id = "item-4",
            name = "Margherita Pizza Slice",
            description = "San Marzano tomatoes, fresh mozzarella, basil",
            categoryId = "SNACKS",
            basePrice = 8.00,
            isAvailable = true
        )
    )

    @Test
    fun test86edAvailabilityFlag() {
        val availableItem = sampleItems[0]
        val oosItem = sampleItems[1]

        assertFalse(availableItem.is86ed)
        assertTrue(availableItem.isAvailable)

        assertTrue(oosItem.is86ed)
        assertFalse(oosItem.isAvailable)
    }

    @Test
    fun testStockQuotaBoundaries() {
        fun withQuota(q: Int?) = sampleItems[0].copy(stockQuota = q)

        assertTrue(withQuota(0).is86ed)
        assertTrue(withQuota(-1).is86ed)
        assertFalse(withQuota(1).is86ed)
        assertTrue(withQuota(1).isLowStock)
        assertTrue(withQuota(5).isLowStock)
        assertFalse(withQuota(6).isLowStock)
        assertFalse(withQuota(null).isLowStock)
        assertFalse(sampleItems[1].copy(stockQuota = 3).isLowStock)
    }

    @Test
    fun testFilterByCategory() {
        val cocktails = CatalogFilterUtils.filterItems(sampleItems, "COCKTAILS", "")
        assertEquals(2, cocktails.size)
        assertTrue(cocktails.all { it.categoryId == "COCKTAILS" })

        val coffee = CatalogFilterUtils.filterItems(sampleItems, "COFFEE", "")
        assertEquals(1, coffee.size)
        assertEquals("Iced Caramel Macchiato", coffee[0].name)

        val all = CatalogFilterUtils.filterItems(sampleItems, "ALL", "")
        assertEquals(4, all.size)
    }

    @Test
    fun testFilterBySearchQuery() {
        val searchRum = CatalogFilterUtils.filterItems(sampleItems, null, "rum")
        assertEquals(2, searchRum.size) // Mojito and Piña Colada both have rum in desc

        val searchPizza = CatalogFilterUtils.filterItems(sampleItems, "SNACKS", "pizza")
        assertEquals(1, searchPizza.size)

        val searchNotFound = CatalogFilterUtils.filterItems(sampleItems, null, "burger")
        assertEquals(0, searchNotFound.size)
    }

    @Test
    fun testModifierPricingCalculation() {
        val basePrice = 6.50
        val modifiers = listOf(
            SelectedModifier("size", "Size", "large", "Large (16oz)", 1.50),
            SelectedModifier("syrup", "Syrup", "extra_caramel", "Extra Caramel Drizzle", 0.75)
        )

        val singleTotal = CatalogFilterUtils.calculateConfiguredPrice(basePrice, modifiers, quantity = 1)
        assertEquals(8.75, singleTotal)

        val multipleTotal = CatalogFilterUtils.calculateConfiguredPrice(basePrice, modifiers, quantity = 3)
        assertEquals(26.25, multipleTotal)

        val configuredCartItem = ConfiguredCartItem(
            menuItem = sampleItems[2],
            selectedModifiers = modifiers,
            quantity = 2
        )
        assertEquals(8.75, configuredCartItem.unitPrice)
        assertEquals(17.50, configuredCartItem.totalPrice)
    }
}
