package com.cruise.pos.shared

import com.cruise.pos.shared.data.repository.BatchCabinChargeResult
import com.cruise.pos.shared.data.repository.SplitFolioBillingRepository
import com.cruise.pos.shared.domain.model.CartItem
import com.cruise.pos.shared.domain.model.GuestFolioProfile
import com.cruise.pos.shared.domain.model.GuestSplitAssignment
import com.cruise.pos.shared.domain.model.MenuItem
import com.cruise.pos.shared.domain.model.MultiGuestSplitOrder
import com.cruise.pos.shared.domain.model.SplitFolioCalculator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SplitBillingPennyRoundingAndTenderTest {

    private val guest1 = GuestFolioProfile(
        guestId = "GUEST-01",
        guestName = "Alice Morgan",
        cabinNumber = "8124",
        folioId = "FOLIO-8124-A"
    )

    private val guest2 = GuestFolioProfile(
        guestId = "GUEST-02",
        guestName = "Bob Vance",
        cabinNumber = "9102",
        folioId = "FOLIO-9102-B"
    )

    private val guest3 = GuestFolioProfile(
        guestId = "GUEST-03",
        guestName = "Charlie Day",
        cabinNumber = "7050",
        folioId = "FOLIO-7050-C"
    )

    private val pizzaItem = MenuItem(
        id = "ITEM-PIZZA",
        name = "Truffle Pizza",
        categoryId = "CAT-FOOD",
        basePrice = 25.50
    )

    private val cocktailItem = MenuItem(
        id = "ITEM-COCKTAIL",
        name = "Mai Tai",
        categoryId = "CAT-BAR",
        basePrice = 14.75
    )

    private val dessertItem = MenuItem(
        id = "ITEM-DESSERT",
        name = "Tiramisu",
        categoryId = "CAT-DESSERT",
        basePrice = 9.35
    )

    @Test
    fun testPennyAccurateProportionalSplitMatchesTotalOrderAmount() {
        val cartItems = listOf(
            CartItem(id = "c1", menuItem = pizzaItem, quantity = 1),       // 25.50
            CartItem(id = "c2", menuItem = cocktailItem, quantity = 1),    // 14.75
            CartItem(id = "c3", menuItem = dessertItem, quantity = 1)      // 9.35
        )
        // Subtotal = 25.50 + 14.75 + 9.35 = 49.60
        // 10% Tax = 4.96
        // 18% Gratuity = 8.928 -> 8.93
        // Order Grand Total = 49.60 + 4.96 + 8.93 = 63.49

        val assignments = listOf(
            GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1")), // Alice: 25.50
            GuestSplitAssignment(guest = guest2, assignedItemIds = listOf("c2")), // Bob: 14.75
            GuestSplitAssignment(guest = guest3, assignedItemIds = listOf("c3"))  // Charlie: 9.35
        )

        val splitOrder = MultiGuestSplitOrder(
            orderId = "ORDER-POOL-991",
            originalItems = cartItems,
            guestAssignments = assignments,
            taxRate = 0.10,
            gratuityRate = 0.18
        )

        val breakdowns = SplitFolioCalculator.calculateSplitBreakdown(splitOrder)
        assertEquals(3, breakdowns.size)

        val sumSubtotals = breakdowns.sumOf { it.subtotal }
        val sumTaxes = breakdowns.sumOf { it.taxAmount }
        val sumGratuities = breakdowns.sumOf { it.gratuityAmount }
        val sumGrandTotals = breakdowns.sumOf { it.totalCharge }

        val roundedSubtotal = kotlin.math.round(sumSubtotals * 100.0) / 100.0
        val roundedTax = kotlin.math.round(sumTaxes * 100.0) / 100.0
        val roundedGratuity = kotlin.math.round(sumGratuities * 100.0) / 100.0
        val roundedGrandTotal = kotlin.math.round(sumGrandTotals * 100.0) / 100.0

        assertEquals(49.60, roundedSubtotal)
        assertEquals(4.96, roundedTax)
        assertEquals(8.93, roundedGratuity)
        assertEquals(63.49, roundedGrandTotal, "Sum of individual split charges must match total order to exact cent")
    }

    @Test
    fun testBatchCabinChargeSubmissionWithUniqueIdempotencyKeys() = runTest {
        val repo = SplitFolioBillingRepository()

        val cartItems = listOf(
            CartItem(id = "c1", menuItem = pizzaItem, quantity = 1),
            CartItem(id = "c2", menuItem = cocktailItem, quantity = 1)
        )
        val assignments = listOf(
            GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1"), customTipAmount = 2.0),
            GuestSplitAssignment(guest = guest2, assignedItemIds = listOf("c2"), customTipAmount = 1.5)
        )

        val splitOrder = MultiGuestSplitOrder(
            orderId = "ORD-SPLIT-402",
            originalItems = cartItems,
            guestAssignments = assignments,
            taxRate = 0.10,
            gratuityRate = 0.18
        )

        val breakdowns = SplitFolioCalculator.calculateSplitBreakdown(splitOrder)

        val signatureMap = mapOf(
            guest1.guestId to "svg_sig_alice",
            guest2.guestId to "svg_sig_bob"
        )

        val batchPayload = repo.buildBatchPayload("ORD-SPLIT-402", breakdowns, signatureMap, 1774268000000L)
        assertEquals("ORD-SPLIT-402", batchPayload.batchOrderId)
        assertEquals(2, batchPayload.charges.size)

        // Ensure distinct idempotency keys
        val key1 = batchPayload.charges[0].idempotencyKey
        val key2 = batchPayload.charges[1].idempotencyKey
        assertTrue(key1.contains("FOLIO-8124-A"))
        assertTrue(key2.contains("FOLIO-9102-B"))
        assertTrue(key1 != key2)

        val result = repo.submitBatchCabinCharges(batchPayload)
        assertIs<BatchCabinChargeResult.Success>(result)
        val success = result as BatchCabinChargeResult.Success
        assertEquals(2, success.processedChargesCount)
        assertTrue(success.totalAmountBilled > 0)
    }

    @Test
    fun testSplitFolioValidationAndEdgeCases() {
        val repo = SplitFolioBillingRepository()
        val cartItems = listOf(CartItem(id = "c1", menuItem = pizzaItem, quantity = 1))

        // Unknown item ID throws
        val invalidOrder = MultiGuestSplitOrder(
            orderId = "ORD-ERR-1",
            originalItems = cartItems,
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("unknown_id"))
            )
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            SplitFolioCalculator.calculateSplitBreakdown(invalidOrder)
        }

        // Negative custom tip throws
        val negativeTipOrder = MultiGuestSplitOrder(
            orderId = "ORD-ERR-2",
            originalItems = cartItems,
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1"), customTipAmount = -5.0)
            )
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            SplitFolioCalculator.calculateSplitBreakdown(negativeTipOrder)
        }

        // Missing signature throws in buildBatchPayload
        val validOrder = MultiGuestSplitOrder(
            orderId = "ORD-OK-1",
            originalItems = cartItems,
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1"), customTipAmount = 0.0)
            )
        )
        val breakdowns = SplitFolioCalculator.calculateSplitBreakdown(validOrder)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repo.buildBatchPayload("ORD-OK-1", breakdowns, emptyMap(), 1000L)
        }

        // Duplicate guestId throws
        val duplicateGuestOrder = MultiGuestSplitOrder(
            orderId = "ORD-ERR-GUEST",
            originalItems = listOf(
                CartItem(id = "c1", menuItem = pizzaItem, quantity = 1),
                CartItem(id = "c2", menuItem = cocktailItem, quantity = 1)
            ),
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1")),
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c2"))
            )
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            SplitFolioCalculator.calculateSplitBreakdown(duplicateGuestOrder)
        }

        // Duplicate item assignment throws
        val duplicateOrder = MultiGuestSplitOrder(
            orderId = "ORD-ERR-DUP",
            originalItems = cartItems,
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1")),
                GuestSplitAssignment(guest = guest2, assignedItemIds = listOf("c1"))
            )
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            SplitFolioCalculator.calculateSplitBreakdown(duplicateOrder)
        }

        // Unassigned item throws
        val unassignedOrder = MultiGuestSplitOrder(
            orderId = "ORD-ERR-MISSING",
            originalItems = listOf(
                CartItem(id = "c1", menuItem = pizzaItem, quantity = 1),
                CartItem(id = "c2", menuItem = cocktailItem, quantity = 1)
            ),
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1"))
            )
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            SplitFolioCalculator.calculateSplitBreakdown(unassignedOrder)
        }

        // Mixed order: 2 guests with assigned items, 1 guest with no items and no tip (omitted)
        val mixedOrder = MultiGuestSplitOrder(
            orderId = "ORD-MIXED",
            originalItems = listOf(
                CartItem(id = "c1", menuItem = pizzaItem, quantity = 1),
                CartItem(id = "c2", menuItem = cocktailItem, quantity = 1)
            ),
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = listOf("c1"), customTipAmount = 2.0),
                GuestSplitAssignment(guest = guest2, assignedItemIds = listOf("c2"), customTipAmount = 0.0),
                GuestSplitAssignment(guest = guest3, assignedItemIds = emptyList(), customTipAmount = 0.0)
            )
        )
        val mixedBreakdowns = SplitFolioCalculator.calculateSplitBreakdown(mixedOrder)
        assertEquals(2, mixedBreakdowns.size)
        assertTrue(mixedBreakdowns.any { it.guestId == guest1.guestId })
        assertTrue(mixedBreakdowns.any { it.guestId == guest2.guestId })
        assertTrue(mixedBreakdowns.none { it.guestId == guest3.guestId })

        // Zero total subtotal with zero tip omits zero-charge guests
        val zeroSubtotalOrder = MultiGuestSplitOrder(
            orderId = "ORD-ZERO",
            originalItems = emptyList(),
            guestAssignments = listOf(
                GuestSplitAssignment(guest = guest1, assignedItemIds = emptyList(), customTipAmount = 0.0),
                GuestSplitAssignment(guest = guest2, assignedItemIds = emptyList(), customTipAmount = 5.0)
            )
        )
        val zeroBreakdowns = SplitFolioCalculator.calculateSplitBreakdown(zeroSubtotalOrder)
        assertEquals(1, zeroBreakdowns.size)
        assertEquals(guest2.guestId, zeroBreakdowns.first().guestId)
    }
}
