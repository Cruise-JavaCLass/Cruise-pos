package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class VesselNavigationMode {
    PORT_DOCKED,
    INTERNATIONAL_WATERS,
    TERRITORIAL_WATERS
}

@Serializable
data class VesselPosition(
    val latitude: Double,
    val longitude: Double,
    val navigationMode: VesselNavigationMode,
    val currentPortName: String? = null
)

@Serializable
data class CartItem(
    val id: String,
    val menuItem: MenuItem,
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val quantity: Int = 1,
    val isHappyHourEligible: Boolean = false,
    val discountPerUnit: Double = 0.0
) {
    init {
        require(quantity >= 1) { "Quantity must be at least 1" }
        require(discountPerUnit >= 0.0) { "Discount per unit cannot be negative" }
    }

    val regularUnitPrice: Double
        get() = menuItem.basePrice + selectedModifiers.sumOf { it.priceDelta }

    val effectiveUnitPrice: Double
        get() = (regularUnitPrice - discountPerUnit).coerceAtLeast(0.0)

    val itemSubtotal: Double
        get() = effectiveUnitPrice * quantity

    val totalDiscount: Double
        get() = (regularUnitPrice - effectiveUnitPrice) * quantity
}

@Serializable
data class CartTaxBreakdown(
    val subtotalBeforeDiscount: Double,
    val totalDiscount: Double,
    val taxableSubtotal: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val gratuityRate: Double,
    val gratuityAmount: Double,
    val grandTotal: Double
)

@Serializable
data class CartHoldTab(
    val tabId: String,
    val guestIdentifier: String,
    val items: List<CartItem>,
    val createdAtEpochMs: Long,
    val note: String = ""
)

@Serializable
data class ActiveCartState(
    val items: List<CartItem> = emptyList(),
    val vesselPosition: VesselPosition = VesselPosition(
        latitude = 0.0,
        longitude = 0.0,
        navigationMode = VesselNavigationMode.INTERNATIONAL_WATERS
    ),
    val isHappyHourActive: Boolean = false,
    val defaultGratuityRate: Double = 0.18, // 18% standard cruise auto-gratuity
    val heldTabs: List<CartHoldTab> = emptyList()
) {
    val totalItemCount: Int
        get() = items.sumOf { it.quantity }

    val isEmpty: Boolean
        get() = items.isEmpty()
}
