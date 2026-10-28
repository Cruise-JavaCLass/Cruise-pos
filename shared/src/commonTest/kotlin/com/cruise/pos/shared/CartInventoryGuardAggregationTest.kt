package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.SseGalleyStockEventClient
import com.cruise.pos.shared.data.repository.InventoryCatalogRepository
import com.cruise.pos.shared.domain.model.CartInventoryGuard
import com.cruise.pos.shared.domain.model.CartInventoryValidationResult
import com.cruise.pos.shared.domain.model.CartItem
import com.cruise.pos.shared.domain.model.MenuItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CartInventoryGuardAggregationTest {
    @Test
    fun testCartInventoryGuardAggregatesQuantities() = runTest {
        val steak = MenuItem(
            id = "STEAK-01",
            name = "Steak",
            categoryId = "CAT-FOOD",
            basePrice = 20.0,
            isAvailable = true,
            stockQuota = 4
        )

        val sseClient = SseGalleyStockEventClient()
        val repository = InventoryCatalogRepository(
            eventClient = sseClient,
            initialItems = listOf(steak),
            scope = this
        )
        val guard = CartInventoryGuard(repository)

        val cart = listOf(
            CartItem(id = "c1", menuItem = steak, quantity = 3),
            CartItem(id = "c2", menuItem = steak, quantity = 2) // Total requested: 5, stock is 4
        )

        val validation = guard.validateCartBeforeTender(cart)
        assertIs<CartInventoryValidationResult.DepletedItemsDetected>(validation)
        val issues = validation.invalidItems
        assertEquals(1, issues.size)
        assertEquals(5, issues.first().requestedQuantity)

        repository.stopSync()
    }
}
