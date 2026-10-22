package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardStatus {
    ACTIVE,
    REPORTED_LOST,
    BLOCKED,
    EXPIRED
}

@Serializable
enum class PassengerType {
    ADULT,
    MINOR,
    VIP,
    CREW
}

@Serializable
data class GuestIdentity(
    val passengerId: String,
    val rfidCardNumber: String,
    val fullName: String,
    val cabinNumber: String,
    val photoUrl: String? = null,
    val age: Int,
    val passengerType: PassengerType = if (age < 18) PassengerType.MINOR else PassengerType.ADULT,
    val cardStatus: CardStatus = CardStatus.ACTIVE,
    val folioBalance: Double = 0.0,
    val creditLimit: Double = 500.0,
    val availableSpendingLimit: Double = (creditLimit - folioBalance).coerceAtLeast(0.0),
    val isAlcoholRestricted: Boolean = age < 18,
    val packageTier: String = "STANDARD",
    val isAllergyWarning: Boolean = false,
    val allergyNotes: String? = null
) {
    val isMinor: Boolean get() = age < 18 || passengerType == PassengerType.MINOR
    val canChargeFolio: Boolean get() = cardStatus == CardStatus.ACTIVE && availableSpendingLimit > 0.0
}

@Serializable
sealed class ScanRecognitionResult {
    data class Success(val guest: GuestIdentity) : ScanRecognitionResult()
    data class CardBlocked(val reason: String, val rfidCardNumber: String) : ScanRecognitionResult()
    data class Expired(val message: String) : ScanRecognitionResult()
    data class InvalidPayload(val error: String) : ScanRecognitionResult()
}

@Serializable
data class PurchaseAuthorizationResult(
    val isAuthorized: Boolean,
    val guest: GuestIdentity,
    val isAlcoholBlocked: Boolean = false,
    val isLimitExceeded: Boolean = false,
    val rejectionReason: String? = null
)
