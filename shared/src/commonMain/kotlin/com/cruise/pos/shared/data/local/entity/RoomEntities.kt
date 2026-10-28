package com.cruise.pos.shared.data.local.entity

import kotlinx.serialization.Serializable

@Serializable
data class OutletEntity(
    val id: String,
    val name: String,
    val deckNumber: Int,
    val outletType: String, // "BAR", "RESTAURANT", "LOUNGE", "SPA"
    val isOperational: Boolean = true,
    val syncedAtEpochMs: Long = 0L
)

@Serializable
data class ProductEntity(
    val id: String,
    val outletId: String,
    val categoryId: String,
    val name: String,
    val description: String = "",
    val basePrice: Double,
    val isAvailable: Boolean = true,
    val stockQuota: Int? = null,
    val version: Int = 1,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
enum class TransactionSyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Serializable
data class TransactionEntity(
    val id: String,
    val sequenceNumber: Long, // Monotonic FIFO index for replay ordering
    val outletId: String,
    val stationId: String,
    val cashierId: String,
    val guestFolioId: String,
    val cabinNumber: String,
    val subtotal: Double,
    val taxAmount: Double,
    val gratuityAmount: Double,
    val tipAmount: Double,
    val totalAmount: Double,
    val signatureSvg: String,
    val syncStatus: TransactionSyncStatus = TransactionSyncStatus.PENDING,
    val createdAtEpochMs: Long,
    val syncedAtEpochMs: Long? = null,
    val retryCount: Int = 0,
    val lastError: String? = null
)
