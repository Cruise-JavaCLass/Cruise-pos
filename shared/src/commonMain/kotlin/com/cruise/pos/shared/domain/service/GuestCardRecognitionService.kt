package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.CardStatus
import com.cruise.pos.shared.domain.model.GuestIdentity
import com.cruise.pos.shared.domain.model.PassengerType
import com.cruise.pos.shared.domain.model.PurchaseAuthorizationResult
import com.cruise.pos.shared.domain.model.ScanRecognitionResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GuestCardRecognitionService(
    private val guestRepository: GuestDirectoryRepository = InMemoryGuestDirectoryRepository()
) {

    /**
     * Parses QR Payload or encrypted passenger barcode string.
     * Accepted QR Formats:
     * 1. JSON string: `{"pid":"P-101","card":"RFID-9021","v":1}`
     * 2. URI / token: `cruise://guest?pid=P-101&card=RFID-9021`
     * 3. Plain token: `CRUISE:P-101:RFID-9021`
     */
    fun parseQrPayload(rawPayload: String): ScanRecognitionResult {
        if (rawPayload.isBlank()) {
            return ScanRecognitionResult.InvalidPayload("Empty or blank QR code payload")
        }

        val trimmed = rawPayload.trim()

        return try {
            when {
                trimmed.startsWith("{") && trimmed.endsWith("}") -> {
                    parseJsonPayload(trimmed)
                }
                trimmed.startsWith("cruise://guest") -> {
                    parseUriPayload(trimmed)
                }
                trimmed.startsWith("CRUISE:") -> {
                    parseTokenPayload(trimmed)
                }
                else -> {
                    // Fallback direct card/passenger lookup
                    resolveGuestByIdentifier(trimmed)
                }
            }
        } catch (t: Throwable) {
            ScanRecognitionResult.InvalidPayload("Failed to decode guest token: ${t.message}")
        }
    }

    /**
     * Processes NFC tag read (RFID card UID or NDEF text).
     */
    fun processNfcCardTap(nfcCardUid: String): ScanRecognitionResult {
        if (nfcCardUid.isBlank()) {
            return ScanRecognitionResult.InvalidPayload("Invalid empty NFC card UID")
        }
        val normalizedUid = nfcCardUid.trim().replace(":", "").uppercase()
        return resolveGuestByIdentifier(normalizedUid)
    }

    private fun parseJsonPayload(jsonStr: String): ScanRecognitionResult {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        val pid = root["pid"]?.jsonPrimitive?.content
        val card = root["card"]?.jsonPrimitive?.content

        val identifier = pid ?: card ?: return ScanRecognitionResult.InvalidPayload("QR missing passenger identifier")
        return resolveGuestByIdentifier(identifier)
    }

    private fun parseUriPayload(uriStr: String): ScanRecognitionResult {
        val queryPart = uriStr.substringAfter("?", "")
        val params = queryPart.split("&").associate {
            val parts = it.split("=")
            parts[0] to (parts.getOrNull(1) ?: "")
        }
        val pid = params["pid"]
        val card = params["card"]
        val identifier = pid?.takeIf { it.isNotBlank() } ?: card ?: return ScanRecognitionResult.InvalidPayload("Invalid guest URI parameters")
        return resolveGuestByIdentifier(identifier)
    }

    private fun parseTokenPayload(tokenStr: String): ScanRecognitionResult {
        val segments = tokenStr.split(":")
        if (segments.size < 2) {
            return ScanRecognitionResult.InvalidPayload("Malformed CRUISE token format")
        }
        val pid = segments[1]
        return resolveGuestByIdentifier(pid)
    }

    private fun resolveGuestByIdentifier(identifier: String): ScanRecognitionResult {
        val guest = guestRepository.findByIdentifier(identifier)
            ?: return ScanRecognitionResult.InvalidPayload("Guest card not recognized in ship manifest")

        return when (guest.cardStatus) {
            CardStatus.ACTIVE -> ScanRecognitionResult.Success(guest)
            CardStatus.REPORTED_LOST -> ScanRecognitionResult.CardBlocked("Card reported lost/stolen", guest.rfidCardNumber)
            CardStatus.BLOCKED -> ScanRecognitionResult.CardBlocked("Folio account frozen by purser", guest.rfidCardNumber)
            CardStatus.EXPIRED -> ScanRecognitionResult.Expired("Voyage ended. Card has expired.")
        }
    }

    /**
     * Validates whether a transaction can be charged against the scanned guest.
     * Enforces Minor / Alcohol restriction policies.
     */
    fun authorizePurchase(
        guest: GuestIdentity,
        purchaseAmount: Double,
        containsAlcohol: Boolean
    ): PurchaseAuthorizationResult {
        if (guest.cardStatus != CardStatus.ACTIVE) {
            return PurchaseAuthorizationResult(
                isAuthorized = false,
                guest = guest,
                rejectionReason = "Card is ${guest.cardStatus.name}"
            )
        }

        // Alcohol lock for minors (< 18)
        if (containsAlcohol && (guest.isMinor || guest.isAlcoholRestricted)) {
            return PurchaseAuthorizationResult(
                isAuthorized = false,
                guest = guest,
                isAlcoholBlocked = true,
                rejectionReason = "ALCOHOL RESTRICTION: Guest is not permitted to buy alcohol (${guest.age} y/o)."
            )
        }

        if (!purchaseAmount.isFinite() || purchaseAmount <= 0.0) {
            return PurchaseAuthorizationResult(
                isAuthorized = false,
                guest = guest,
                rejectionReason = "INVALID AMOUNT: Purchase amount must be a positive number"
            )
        }

        // Folio limit check
        if (guest.availableSpendingLimit < purchaseAmount) {
            return PurchaseAuthorizationResult(
                isAuthorized = false,
                guest = guest,
                isLimitExceeded = true,
                rejectionReason = "EXCEEDED LIMIT: Available limit \$${guest.availableSpendingLimit} is less than required \$${purchaseAmount}"
            )
        }

        return PurchaseAuthorizationResult(
            isAuthorized = true,
            guest = guest
        )
    }
}

interface GuestDirectoryRepository {
    fun findByIdentifier(identifier: String): GuestIdentity?
}

class InMemoryGuestDirectoryRepository(
    initialGuests: List<GuestIdentity> = defaultShipGuestManifest()
) : GuestDirectoryRepository {
    private val directory = initialGuests.toMutableList()

    override fun findByIdentifier(identifier: String): GuestIdentity? {
        val normalized = identifier.trim().replace(":", "").uppercase()
        return directory.firstOrNull {
            it.passengerId.equals(identifier, ignoreCase = true) ||
            it.rfidCardNumber.replace(":", "").equals(normalized, ignoreCase = true)
        }
    }

    companion object {
        fun defaultShipGuestManifest(): List<GuestIdentity> = listOf(
            GuestIdentity(
                passengerId = "P-1001",
                rfidCardNumber = "RFID9021A",
                fullName = "Alexander Wright",
                cabinNumber = "CAB-8012",
                photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
                age = 34,
                passengerType = PassengerType.VIP,
                cardStatus = CardStatus.ACTIVE,
                folioBalance = 120.0,
                creditLimit = 1500.0,
                packageTier = "DELUXE_BEVERAGE"
            ),
            GuestIdentity(
                passengerId = "P-1002",
                rfidCardNumber = "RFID9022B",
                fullName = "Emily Johnson",
                cabinNumber = "CAB-8012",
                photoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9",
                age = 16, // Minor!
                passengerType = PassengerType.MINOR,
                cardStatus = CardStatus.ACTIVE,
                folioBalance = 15.0,
                creditLimit = 200.0,
                packageTier = "SODA_PACKAGE"
            ),
            GuestIdentity(
                passengerId = "P-1003",
                rfidCardNumber = "RFID9023C",
                fullName = "Robert Langdon",
                cabinNumber = "CAB-4105",
                age = 45,
                passengerType = PassengerType.ADULT,
                cardStatus = CardStatus.REPORTED_LOST,
                folioBalance = 300.0,
                creditLimit = 500.0
            ),
            GuestIdentity(
                passengerId = "P-1004",
                rfidCardNumber = "RFID9024D",
                fullName = "Sophia Martinez",
                cabinNumber = "CAB-6201",
                age = 29,
                passengerType = PassengerType.ADULT,
                cardStatus = CardStatus.ACTIVE,
                folioBalance = 490.0,
                creditLimit = 500.0 // only $10 available!
            )
        )
    }
}
