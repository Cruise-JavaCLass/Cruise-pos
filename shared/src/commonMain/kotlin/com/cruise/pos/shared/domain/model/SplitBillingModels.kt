package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GuestFolioProfile(
    val guestId: String,
    val guestName: String,
    val cabinNumber: String,
    val folioId: String,
    val cardLast4: String? = null
)

@Serializable
data class GuestSplitAssignment(
    val guest: GuestFolioProfile,
    val assignedItemIds: List<String> = emptyList(),
    val customTipAmount: Double = 0.0,
    val signatureSvg: String? = null,
    val isTenderCompleted: Boolean = false
)

@Serializable
data class GuestSplitChargeBreakdown(
    val guestId: String,
    val guestName: String,
    val cabinNumber: String,
    val folioId: String,
    val subtotal: Double,
    val taxAmount: Double,
    val gratuityAmount: Double,
    val tipAmount: Double,
    val totalCharge: Double
)

@Serializable
data class MultiGuestSplitOrder(
    val orderId: String,
    val originalItems: List<CartItem>,
    val guestAssignments: List<GuestSplitAssignment>,
    val taxRate: Double = 0.10,
    val gratuityRate: Double = 0.18
)
