package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.SseGalleyStockEventClient
import com.cruise.pos.shared.core.network.StockUpdateEvent
import com.cruise.pos.shared.core.network.StockUpdateType
import com.cruise.pos.shared.data.repository.InventoryCatalogRepository
import com.cruise.pos.shared.domain.model.CartInventoryGuard
import com.cruise.pos.shared.domain.model.CartInventoryValidationResult
import com.cruise.pos.shared.domain.model.CartItem
import com.cruise.pos.shared.domain.model.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Realtime86AndConcurrencyTest {

    private val specialSteak = MenuItem(
        id = "STEAK-01",
        name = "Prime Rib Galley Special",
        categoryId = "CAT-CHEF-SPECIAL",
        basePrice = 45.0,
        isAvailable = true,
        stockQuota = 5 // Only 5 available
    )

    private val houseWine = MenuItem(
        id = "WINE-01",
        name = "House Pinot Noir",
        categoryId = "CAT-WINE",
        basePrice = 14.0,
        isAvailable = true,
        stockQuota = null // Unlimited
    )

    @Test
    fun testRealtimeGalleySseEventUpdatesSoldOutStatus() = runTest {
        val sseClient = SseGalleyStockEventClient()
        val repository = InventoryCatalogRepository(
            eventClient = sseClient,
            initialItems = listOf(specialSteak, houseWine),
            scope = backgroundScope
        )

        // Initial state
        var currentItems = repository.getAvailableItems()
        assertEquals(2, currentItems.size)

        // Directly handle or emit event
        repository.handleIncomingStockEvent(
            StockUpdateEvent(
                menuItemId = "STEAK-01",
                updateType = StockUpdateType.ITEM_86ED,
                isAvailable = false,
                remainingQuota = 0,
                kitchenStation = "Main Galley Hot Line"
            )
        )

        val updatedSteak = repository.getItemById("STEAK-01")
        assertFalse(updatedSteak?.isAvailable ?: true)
        assertTrue(updatedSteak?.is86ed ?: false)

        currentItems = repository.getAvailableItems()
        assertEquals(1, currentItems.size)
        assertEquals("WINE-01", currentItems.first().id)

        repository.stopSync()
    }

    @Test
    fun testCartInventoryGuardInterceptsDepletedItemBeforeTender() = runTest {
        val sseClient = SseGalleyStockEventClient()
        val repository = InventoryCatalogRepository(
            eventClient = sseClient,
            initialItems = listOf(specialSteak),
            scope = backgroundScope
        )
        val guard = CartInventoryGuard(repository)

        val cart = listOf(
            CartItem(id = "c1", menuItem = specialSteak, quantity = 2)
        )

        // Step 1: Valid when stock is 5
        val initialValidation = guard.validateCartBeforeTender(cart)
        assertIs<CartInventoryValidationResult.Valid>(initialValidation)

        // Step 2: Galley depletes stock to 1
        repository.handleIncomingStockEvent(
            StockUpdateEvent(
                menuItemId = "STEAK-01",
                updateType = StockUpdateType.QUOTA_COUNTDOWN,
                remainingQuota = 1,
                isAvailable = true
            )
        )

        // Step 3: Guard must intercept since cart has quantity 2 > remaining quota 1
        val rejectedValidation = guard.validateCartBeforeTender(cart)
        assertIs<CartInventoryValidationResult.DepletedItemsDetected>(rejectedValidation)
        val issues = (rejectedValidation as CartInventoryValidationResult.DepletedItemsDetected).invalidItems
        assertEquals(1, issues.size)
        assertEquals("STEAK-01", issues.first().menuItemId)
        assertEquals(1, issues.first().remainingStock)

        repository.stopSync()
    }

    @Test
    fun test86edItemRemainsUnavailableAfterCountdownUntilRestocked() = runTest {
        val sseClient = SseGalleyStockEventClient()
        val repository = InventoryCatalogRepository(
            eventClient = sseClient,
            initialItems = listOf(specialSteak.copy(stockQuota = 5, isAvailable = true)),
            scope = backgroundScope
        )

        // 1. Kitchen 86s the item
        repository.handleIncomingStockEvent(
            StockUpdateEvent(
                menuItemId = "STEAK-01",
                updateType = StockUpdateType.ITEM_86ED
            )
        )
        val itemAfter86 = repository.getItemById("STEAK-01")
        assertEquals(false, itemAfter86?.isAvailable)
        assertEquals(5, itemAfter86?.stockQuota)

        // 2. A subsequent countdown event without restock must NOT make the item available
        repository.handleIncomingStockEvent(
            StockUpdateEvent(
                menuItemId = "STEAK-01",
                updateType = StockUpdateType.QUOTA_COUNTDOWN,
                remainingQuota = 3,
                isAvailable = true
            )
        )
        val itemAfterCountdown = repository.getItemById("STEAK-01")
        assertEquals(false, itemAfterCountdown?.isAvailable, "Countdown must not un-86 an item")
        assertEquals(3, itemAfterCountdown?.stockQuota)

        // 3. Only ITEM_RESTOCKED clears the 86'd status
        repository.handleIncomingStockEvent(
            StockUpdateEvent(
                menuItemId = "STEAK-01",
                updateType = StockUpdateType.ITEM_RESTOCKED,
                remainingQuota = 10
            )
        )
        val itemAfterRestock = repository.getItemById("STEAK-01")
        assertEquals(true, itemAfterRestock?.isAvailable)
        assertEquals(10, itemAfterRestock?.stockQuota)

        repository.stopSync()
    }

    @Test
    fun testCartInventoryGuardAggregatesDemandAcrossMultipleCartLines() = runTest {
        val sseClient = SseGalleyStockEventClient()
        val repository = InventoryCatalogRepository(
            eventClient = sseClient,
            initialItems = listOf(specialSteak.copy(stockQuota = 5)),
            scope = backgroundScope
        )
        val guard = CartInventoryGuard(repository)

        val cart = listOf(
            CartItem(id = "c1", menuItem = specialSteak, quantity = 3),
            CartItem(id = "c2", menuItem = specialSteak, quantity = 3)
        )

        val result = guard.validateCartBeforeTender(cart)
        assertIs<CartInventoryValidationResult.DepletedItemsDetected>(result)
        val issues = (result as CartInventoryValidationResult.DepletedItemsDetected).invalidItems
        assertEquals(1, issues.size)
        assertEquals("STEAK-01", issues.first().menuItemId)
        assertEquals(6, issues.first().requestedQuantity)
        assertEquals(5, issues.first().remainingStock)

        repository.stopSync()
    }

    @Test
    fun testConcurrentStockDecrementPreventsOverAllocation() = runTest {
        val sseClient = SseGalleyStockEventClient()
        val repository = InventoryCatalogRepository(
            eventClient = sseClient,
            initialItems = listOf(specialSteak.copy(stockQuota = 5)),
            scope = backgroundScope
        )

        // 10 concurrent requests trying to claim 1 unit each when only 5 units exist
        val concurrentAttempts = (1..10).map {
            async(Dispatchers.Default) {
                repository.tryDecrementQuota("STEAK-01", 1)
            }
        }.awaitAll()

        val successCount = concurrentAttempts.count { it }
        val failureCount = concurrentAttempts.count { !it }

        assertEquals(5, successCount, "Exactly 5 purchases must succeed")
        assertEquals(5, failureCount, "Excess 5 attempts must fail")

        val finalItem = repository.getItemById("STEAK-01")
        assertEquals(0, finalItem?.stockQuota)
        assertTrue(finalItem?.is86ed ?: false)

        repository.stopSync()
    }
}
