package com.cruise.pos.shared.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

@Serializable
enum class StockUpdateType {
    ITEM_86ED,          // Kitchen marked item as 86'd / sold out
    ITEM_RESTOCKED,     // Kitchen replenished stock / removed 86'd status
    QUOTA_COUNTDOWN     // Inventory quota decremented or set (e.g. 5 left)
}

@Serializable
data class StockUpdateEvent(
    val menuItemId: String,
    val updateType: StockUpdateType,
    val remainingQuota: Int? = null,
    val isAvailable: Boolean = true,
    val timestampEpochMs: Long = 0L,
    val kitchenStation: String = "Galley-Main"
)

interface GalleyStockEventClient {
    val stockEvents: Flow<StockUpdateEvent>
    fun connect()
    fun disconnect()
    suspend fun emitEvent(event: StockUpdateEvent)
}

class SseGalleyStockEventClient : GalleyStockEventClient {

    private val _stockEvents = MutableSharedFlow<StockUpdateEvent>(replay = 1)
    override val stockEvents: Flow<StockUpdateEvent> = _stockEvents.asSharedFlow()

    private var isConnected: Boolean = false

    override fun connect() {
        isConnected = true
    }

    override fun disconnect() {
        isConnected = false
    }

    override suspend fun emitEvent(event: StockUpdateEvent) {
        if (isConnected) {
            _stockEvents.emit(event)
        }
    }
}
