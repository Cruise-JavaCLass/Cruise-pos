package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.CartHoldManager
import com.cruise.pos.shared.domain.model.CartItem
import com.cruise.pos.shared.domain.model.MaritimeTaxCalculator
import com.cruise.pos.shared.domain.model.MenuItem
import com.cruise.pos.shared.domain.model.VesselNavigationMode
import com.cruise.pos.shared.domain.model.VesselPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartHoldAndCheckoutTransitionTest {

    private val sampleItem1 = MenuItem(
        id = "ITEM-101",
        name = "Sea Bass Dinner",
        categoryId = "CAT-MAINS",
        basePrice = 28.00
    )

    private val sampleItem2 = MenuItem(
        id = "ITEM-102",
        name = "Sparkling Water",
        categoryId = "CAT-BEVERAGE",
        basePrice = 4.50
    )

    @Test
    fun testAddToCartHoldAndRecallFlow() {
        val holdManager = CartHoldManager()

        // 1. Guest A adds items to cart
        val cartItemsGuestA = listOf(
            CartItem(id = "item-1", menuItem = sampleItem1, quantity = 2),
            CartItem(id = "item-2", menuItem = sampleItem2, quantity = 1)
        )

        // 2. Park / Hold order for Guest A
        val heldTab = holdManager.holdOrder(
            guestIdentifier = "Cabin-7024",
            items = cartItemsGuestA,
            note = "Window table pending dessert order"
        )

        assertEquals("Cabin-7024", heldTab.guestIdentifier)
        assertEquals(2, heldTab.items.size)
        assertEquals(1, holdManager.getHeldTabs().size)

        // 3. Recall order for Guest A
        val recalledTab = holdManager.recallOrder(heldTab.tabId)
        assertNotNull(recalledTab)
        assertEquals("Cabin-7024", recalledTab.guestIdentifier)
        assertEquals(2, recalledTab.items.size)
        assertTrue(holdManager.getHeldTabs().isEmpty(), "Held tab list should be empty after recall")

        // 4. Proceed to checkout calculation
        val seaPosition = VesselPosition(
            latitude = 20.0,
            longitude = -70.0,
            navigationMode = VesselNavigationMode.INTERNATIONAL_WATERS
        )
        val breakdown = MaritimeTaxCalculator.calculateBreakdown(
            items = recalledTab.items,
            position = seaPosition,
            gratuityRate = 0.18
        )

        // Total: 2 * 28 + 1 * 4.50 = 60.50. Tax = 0. Gratuity 18% = 10.89. GrandTotal = 71.39
        assertEquals(60.50, breakdown.taxableSubtotal)
        assertEquals(0.00, breakdown.taxAmount)
        assertEquals(10.89, breakdown.gratuityAmount)
        assertEquals(71.39, breakdown.grandTotal)
    }

    @Test
    fun testDiscardHeldOrder() {
        val holdManager = CartHoldManager()
        val cartItems = listOf(CartItem(id = "item-1", menuItem = sampleItem1, quantity = 1))

        val tab = holdManager.holdOrder(
            guestIdentifier = "Deck-Lounge-04",
            items = cartItems
        )

        val discarded = holdManager.discardOrder(tab.tabId)
        assertTrue(discarded)
        assertNull(holdManager.recallOrder(tab.tabId))
    }

    @Test
    fun testHoldOrderUsesRealClockByDefault() {
        val holdManager = CartHoldManager()
        val cartItems = listOf(CartItem(id = "item-1", menuItem = sampleItem1, quantity = 1))

        val tab = holdManager.holdOrder(
            guestIdentifier = "Cabin-101",
            items = cartItems
        )

        // Epoch ms should be non-zero and reasonable (greater than year 2024 timestamp 1700000000000L)
        assertTrue(tab.createdAtEpochMs > 1700000000000L, "Default clock should generate real epoch ms, got ${tab.createdAtEpochMs}")
    }

    @Test
    fun testMutatingOriginalListDoesNotAffectHeldTab() {
        val holdManager = CartHoldManager()
        val mutableItems = mutableListOf(CartItem(id = "item-1", menuItem = sampleItem1, quantity = 1))

        val tab = holdManager.holdOrder(
            guestIdentifier = "Cabin-102",
            items = mutableItems
        )

        // Caller mutates their list after parking
        mutableItems.clear()

        assertEquals(1, tab.items.size)
        val recalled = holdManager.recallOrder(tab.tabId)
        assertNotNull(recalled)
        assertEquals(1, recalled.items.size)
    }
}
