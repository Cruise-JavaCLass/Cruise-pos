package com.cruise.pos.shared.data.repository

import com.cruise.pos.shared.core.network.GalleyStockEventClient
import com.cruise.pos.shared.core.network.StockUpdateEvent
import com.cruise.pos.shared.core.network.StockUpdateType
import com.cruise.pos.shared.domain.model.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InventoryCatalogRepository(
    private val eventClient: GalleyStockEventClient,
    initialItems: List<MenuItem> = emptyList(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val mutex = Mutex()
    private val _itemsState = MutableStateFlow(initialItems)
    val itemsState: StateFlow<List<MenuItem>> = _itemsState.asStateFlow()

    private var eventSubscriptionJob: Job? = null

    init {
        startSync()
    }

    fun startSync() {
        if (eventSubscriptionJob != null) return
        eventClient.connect()
        eventSubscriptionJob = scope.launch {
            eventClient.stockEvents.collect { event ->
                handleIncomingStockEvent(event)
            }
        }
    }

    fun stopSync() {
        eventSubscriptionJob?.cancel()
        eventSubscriptionJob = null
        eventClient.disconnect()
    }

    /**
     * Atomically decrements the item's quota when added to cart or submitted.
     * Returns true if decremented successfully, false if depleted or unavailable.
     */
    suspend fun tryDecrementQuota(itemId: String, quantity: Int = 1): Boolean = mutex.withLock {
        val currentList = _itemsState.value
        val itemIndex = currentList.indexOfFirst { it.id == itemId }
        if (itemIndex == -1) return false

        val item = currentList[itemIndex]
        if (!item.isAvailable) return false

        // Unlimited stock
        if (item.stockQuota == null) return true

        if (item.stockQuota < quantity) {
            return false
        }

        val updatedQuota = item.stockQuota - quantity
        val isDepleted = updatedQuota <= 0
        val updatedItem = item.copy(
            stockQuota = updatedQuota,
            isAvailable = !isDepleted
        )

        val newList = currentList.toMutableList()
        newList[itemIndex] = updatedItem
        _itemsState.value = newList
        return true
    }

    suspend fun handleIncomingStockEvent(event: StockUpdateEvent) = mutex.withLock {
        val currentList = _itemsState.value
        val itemIndex = currentList.indexOfFirst { it.id == event.menuItemId }
        if (itemIndex == -1) return@withLock

        val targetItem = currentList[itemIndex]
        val updatedItem = when (event.updateType) {
            StockUpdateType.ITEM_86ED -> targetItem.copy(
                isAvailable = false,
                stockQuota = 0
            )
            StockUpdateType.ITEM_RESTOCKED -> targetItem.copy(
                isAvailable = true,
                stockQuota = event.remainingQuota ?: targetItem.stockQuota
            )
            StockUpdateType.QUOTA_COUNTDOWN -> {
                val newQuota = event.remainingQuota ?: targetItem.stockQuota
                val isStillAvailable = (newQuota == null || newQuota > 0) && event.isAvailable
                targetItem.copy(
                    stockQuota = newQuota,
                    isAvailable = isStillAvailable
                )
            }
        }

        val newList = currentList.toMutableList()
        newList[itemIndex] = updatedItem
        _itemsState.value = newList
    }

    suspend fun getAvailableItems(): List<MenuItem> = mutex.withLock {
        _itemsState.value.filter { !it.is86ed }
    }

    suspend fun getItemById(itemId: String): MenuItem? = mutex.withLock {
        _itemsState.value.firstOrNull { it.id == itemId }
    }
}
