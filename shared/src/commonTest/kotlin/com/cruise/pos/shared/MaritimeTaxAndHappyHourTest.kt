package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.CartItem
import com.cruise.pos.shared.domain.model.DiscountType
import com.cruise.pos.shared.domain.model.HappyHourEngine
import com.cruise.pos.shared.domain.model.HappyHourRule
import com.cruise.pos.shared.domain.model.MaritimeTaxCalculator
import com.cruise.pos.shared.domain.model.MenuItem
import com.cruise.pos.shared.domain.model.SelectedModifier
import com.cruise.pos.shared.domain.model.VesselNavigationMode
import com.cruise.pos.shared.domain.model.VesselPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaritimeTaxAndHappyHourTest {

    private val cocktailItem = MenuItem(
        id = "ITEM-001",
        name = "Mojito",
        categoryId = "CAT-COCKTAILS",
        basePrice = 12.0
    )

    private val foodItem = MenuItem(
        id = "ITEM-002",
        name = "Burger",
        categoryId = "CAT-FOOD",
        basePrice = 18.0
    )

    @Test
    fun testPortDockedAppliesTenPercentTax() {
        val cartItem = CartItem(
            id = "c1",
            menuItem = cocktailItem,
            quantity = 2 // $24.00
        )
        val portPosition = VesselPosition(
            latitude = 25.7617,
            longitude = -80.1918,
            navigationMode = VesselNavigationMode.PORT_DOCKED,
            currentPortName = "PortMiami"
        )

        val breakdown = MaritimeTaxCalculator.calculateBreakdown(
            items = listOf(cartItem),
            position = portPosition,
            gratuityRate = 0.18
        )

        assertEquals(24.00, breakdown.taxableSubtotal)
        assertEquals(0.10, breakdown.taxRate)
        assertEquals(2.40, breakdown.taxAmount) // 10% of 24
        assertEquals(4.32, breakdown.gratuityAmount) // 18% of 24
        assertEquals(30.72, breakdown.grandTotal) // 24 + 2.40 + 4.32
    }

    @Test
    fun testInternationalWatersAppliesZeroTax() {
        val cartItem = CartItem(
            id = "c1",
            menuItem = foodItem,
            quantity = 1 // $18.00
        )
        val seaPosition = VesselPosition(
            latitude = 24.0,
            longitude = -75.0,
            navigationMode = VesselNavigationMode.INTERNATIONAL_WATERS
        )

        val breakdown = MaritimeTaxCalculator.calculateBreakdown(
            items = listOf(cartItem),
            position = seaPosition,
            gratuityRate = 0.18
        )

        assertEquals(18.00, breakdown.taxableSubtotal)
        assertEquals(0.0, breakdown.taxRate)
        assertEquals(0.00, breakdown.taxAmount)
        assertEquals(3.24, breakdown.gratuityAmount) // 18% of 18
        assertEquals(21.24, breakdown.grandTotal) // 18 + 0 + 3.24
    }

    @Test
    fun testHappyHourPercentageDiscountEngine() {
        val rules = listOf(
            HappyHourRule(
                id = "HH-1",
                name = "Cocktail Happy Hour 50% Off",
                eligibleCategoryIds = setOf("CAT-COCKTAILS"),
                discountType = DiscountType.PERCENTAGE,
                discountValue = 0.50, // 50%
                startHour24 = 16,
                endHour24 = 18
            )
        )

        val items = listOf(
            CartItem(id = "c1", menuItem = cocktailItem, quantity = 2),
            CartItem(id = "c2", menuItem = foodItem, quantity = 1)
        )

        // Inside Happy Hour (17:00)
        val discounted = HappyHourEngine.applyPromotions(items, rules, currentHour24 = 17)
        val discountedCocktail = discounted.first { it.id == "c1" }
        val regularFood = discounted.first { it.id == "c2" }

        assertTrue(discountedCocktail.isHappyHourEligible)
        assertEquals(6.0, discountedCocktail.discountPerUnit)
        assertEquals(6.0, discountedCocktail.effectiveUnitPrice)
        assertEquals(12.0, discountedCocktail.itemSubtotal)

        assertFalse(regularFood.isHappyHourEligible)
        assertEquals(0.0, regularFood.discountPerUnit)
        assertEquals(18.0, regularFood.effectiveUnitPrice)

        // Outside Happy Hour (20:00)
        val afterHh = HappyHourEngine.applyPromotions(items, rules, currentHour24 = 20)
        assertFalse(afterHh.first { it.id == "c1" }.isHappyHourEligible)
        assertEquals(0.0, afterHh.first { it.id == "c1" }.discountPerUnit)
    }

    @Test
    fun testHappyHourFixedAmountDiscountAndModifiersMath() {
        val modifierExtraShot = SelectedModifier(
            groupId = "g1",
            groupName = "Shot",
            optionId = "o1",
            optionName = "Extra Rum",
            priceDelta = 3.0
        )
        val modifiedCocktail = CartItem(
            id = "c1",
            menuItem = cocktailItem, // 12.0 + 3.0 = 15.0 regular
            selectedModifiers = listOf(modifierExtraShot),
            quantity = 1
        )

        val rules = listOf(
            HappyHourRule(
                id = "HH-FIXED",
                name = "$4 Off Mojitos",
                eligibleMenuItemIds = setOf("ITEM-001"),
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 4.0,
                startHour24 = 14,
                endHour24 = 16
            )
        )

        val discounted = HappyHourEngine.applyPromotions(listOf(modifiedCocktail), rules, currentHour24 = 15)
        val item = discounted.first()

        assertEquals(15.0, item.regularUnitPrice)
        assertEquals(4.0, item.discountPerUnit)
        assertEquals(11.0, item.effectiveUnitPrice)
        assertEquals(11.0, item.itemSubtotal)
    }

    @Test
    fun testRejectInvalidGratuityRates() {
        val cartItem = CartItem(id = "c1", menuItem = cocktailItem, quantity = 1)
        val portPosition = VesselPosition(
            latitude = 25.7617,
            longitude = -80.1918,
            navigationMode = VesselNavigationMode.PORT_DOCKED
        )

        // Negative gratuity rate
        try {
            MaritimeTaxCalculator.calculateBreakdown(listOf(cartItem), portPosition, gratuityRate = -0.05)
            kotlin.test.fail("Expected IllegalArgumentException for negative gratuity rate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative") == true)
        }

        // Non-finite gratuity rate (Double.NaN)
        try {
            MaritimeTaxCalculator.calculateBreakdown(listOf(cartItem), portPosition, gratuityRate = Double.NaN)
            kotlin.test.fail("Expected IllegalArgumentException for NaN gratuity rate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("finite") == true)
        }

        // Non-finite gratuity rate (Double.POSITIVE_INFINITY)
        try {
            MaritimeTaxCalculator.calculateBreakdown(listOf(cartItem), portPosition, gratuityRate = Double.POSITIVE_INFINITY)
            kotlin.test.fail("Expected IllegalArgumentException for infinite gratuity rate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("finite") == true)
        }
    }
}
