package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.GalleyStockEventClient
import com.cruise.pos.shared.core.network.StockUpdateEvent
import com.cruise.pos.shared.data.repository.InventoryCatalogRepository
import com.cruise.pos.shared.domain.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DummyGalleyStockEventClient : GalleyStockEventClient {
    private val _events = MutableSharedFlow<StockUpdateEvent>()
    override val stockEvents: SharedFlow<StockUpdateEvent> = _events
    override fun connect() {}
    override fun disconnect() {}
    override suspend fun emitEvent(event: StockUpdateEvent) {
        _events.emit(event)
    }
}

class CartInventoryGuardTest {
    @Test
    fun testValidateCartBeforeTender_aggregatesQuantitiesForSameMenuItem() = runTest {
        val menuItem = MenuItem(
            id = "ITEM-001",
            name = "Burger",
            categoryId = "FOOD",
            basePrice = 10.0,
            isAvailable = true,
            stockQuota = 3
        )

        val eventClient = DummyGalleyStockEventClient()
        val repo = InventoryCatalogRepository(eventClient, listOf(menuItem))
        val guard = CartInventoryGuard(repo)

        val cartItems = listOf(
            CartItem(id = "c1", menuItem = menuItem, quantity = 2),
            CartItem(id = "c2", menuItem = menuItem, quantity = 2) // Total requested: 4 > 3
        )

        val result = guard.validateCartBeforeTender(cartItems)

        assertTrue(result is CartInventoryValidationResult.DepletedItemsDetected, "Expected DepletedItemsDetected")

        val issues = (result as CartInventoryValidationResult.DepletedItemsDetected).invalidItems
        assertEquals(1, issues.size)
        assertEquals("ITEM-001", issues[0].menuItemId)
        assertEquals(4, issues[0].requestedQuantity)
        assertEquals(3, issues[0].remainingStock)
        assertTrue(issues[0].reason.contains("Requested 4 units, but only 3 remain in stock"))
    }
}
