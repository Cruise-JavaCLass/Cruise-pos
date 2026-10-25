package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.CardStatus
import com.cruise.pos.shared.domain.model.GuestIdentity
import com.cruise.pos.shared.domain.model.PassengerType
import com.cruise.pos.shared.domain.model.ScanRecognitionResult
import com.cruise.pos.shared.domain.service.GuestCardRecognitionService
import com.cruise.pos.shared.domain.service.InMemoryGuestDirectoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuestCardRecognitionAndSecurityTest {

    private val service = GuestCardRecognitionService(
        InMemoryGuestDirectoryRepository(
            listOf(
                GuestIdentity(
                    passengerId = "P-1001",
                    rfidCardNumber = "RFID9021A",
                    fullName = "Alexander Wright",
                    cabinNumber = "CAB-8012",
                    age = 34,
                    passengerType = PassengerType.ADULT,
                    cardStatus = CardStatus.ACTIVE,
                    folioBalance = 100.0,
                    creditLimit = 1000.0
                ),
                GuestIdentity(
                    passengerId = "P-1002",
                    rfidCardNumber = "RFID9022B",
                    fullName = "Emily Johnson",
                    cabinNumber = "CAB-8012",
                    age = 16,
                    passengerType = PassengerType.MINOR,
                    cardStatus = CardStatus.ACTIVE,
                    folioBalance = 50.0,
                    creditLimit = 200.0
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
                    creditLimit = 500.0
                )
            )
        )
    )

    @Test
    fun testParseQrJsonPayloadSuccess() {
        val jsonPayload = """{"pid":"P-1001","card":"RFID9021A","v":1}"""
        val result = service.parseQrPayload(jsonPayload)

        assertTrue(result is ScanRecognitionResult.Success)
        val guest = result.guest
        assertEquals("P-1001", guest.passengerId)
        assertEquals("Alexander Wright", guest.fullName)
        assertEquals("CAB-8012", guest.cabinNumber)
        assertFalse(guest.isMinor)
    }

    @Test
    fun testParseQrUriPayloadSuccess() {
        val uriPayload = "cruise://guest?pid=P-1002&card=RFID9022B"
        val result = service.parseQrPayload(uriPayload)

        assertTrue(result is ScanRecognitionResult.Success)
        val guest = result.guest
        assertEquals("Emily Johnson", guest.fullName)
        assertTrue(guest.isMinor)
        assertEquals(16, guest.age)
    }

    @Test
    fun testParseQrPlainTokenSuccess() {
        val tokenPayload = "CRUISE:P-1001:TOKEN-XYZ"
        val result = service.parseQrPayload(tokenPayload)

        assertTrue(result is ScanRecognitionResult.Success)
        assertEquals("Alexander Wright", result.guest.fullName)
    }

    @Test
    fun testParseQrInvalidOrBlankPayload() {
        val blankResult = service.parseQrPayload("   ")
        assertTrue(blankResult is ScanRecognitionResult.InvalidPayload)

        val unknownResult = service.parseQrPayload("""{"pid":"P-9999"}""")
        assertTrue(unknownResult is ScanRecognitionResult.InvalidPayload)
    }

    @Test
    fun testNfcCardTapSuccessAndNormalization() {
        // Test with colon-separated UID
        val nfcResult = service.processNfcCardTap("RFID:9021:A")
        assertTrue(nfcResult is ScanRecognitionResult.Success)
        assertEquals("Alexander Wright", nfcResult.guest.fullName)

        // Test with lowercase UID
        val nfcLower = service.processNfcCardTap("rfid9021a")
        assertTrue(nfcLower is ScanRecognitionResult.Success)
    }

    @Test
    fun testNfcCardTapBlockedOrLostCard() {
        val lostResult = service.processNfcCardTap("RFID9023C")
        assertTrue(lostResult is ScanRecognitionResult.CardBlocked)
        assertEquals("RFID9023C", lostResult.rfidCardNumber)
    }

    @Test
    fun testMinorGuestWarningAndAlcoholLock() {
        val minorResult = service.parseQrPayload("""{"pid":"P-1002"}""")
        assertTrue(minorResult is ScanRecognitionResult.Success)
        val minor = minorResult.guest
        assertTrue(minor.isMinor)

        // Attempt alcohol order -> MUST BE REJECTED
        val alcoholAuth = service.authorizePurchase(
            guest = minor,
            purchaseAmount = 14.50,
            containsAlcohol = true
        )
        assertFalse(alcoholAuth.isAuthorized)
        assertTrue(alcoholAuth.isAlcoholBlocked)
        assertNotNull(alcoholAuth.rejectionReason)
        assertTrue(alcoholAuth.rejectionReason?.contains("ALCOHOL RESTRICTION") == true)

        // Attempt non-alcoholic soda purchase -> MUST BE AUTHORIZED
        val sodaAuth = service.authorizePurchase(
            guest = minor,
            purchaseAmount = 4.00,
            containsAlcohol = false
        )
        assertTrue(sodaAuth.isAuthorized)
        assertFalse(sodaAuth.isAlcoholBlocked)
    }

    @Test
    fun testAdultAlcoholPurchaseAndFolioLimitEnforcement() {
        val adultResult = service.parseQrPayload("""{"pid":"P-1001"}""")
        assertTrue(adultResult is ScanRecognitionResult.Success)
        val adult = adultResult.guest

        // Alcohol order within limit -> Authorized
        val auth = service.authorizePurchase(
            guest = adult,
            purchaseAmount = 45.00,
            containsAlcohol = true
        )
        assertTrue(auth.isAuthorized)
        assertFalse(auth.isAlcoholBlocked)

        // Low balance guest (P-1004 has credit limit 500, balance 490 -> 10 available)
        val lowLimitGuest = service.parseQrPayload("""{"pid":"P-1004"}""").let { (it as ScanRecognitionResult.Success).guest }
        val exceedAuth = service.authorizePurchase(
            guest = lowLimitGuest,
            purchaseAmount = 25.00,
            containsAlcohol = false
        )
        assertFalse(exceedAuth.isAuthorized)
        assertTrue(exceedAuth.isLimitExceeded)
        assertTrue(exceedAuth.rejectionReason!!.contains("EXCEEDED LIMIT"))
    }
}
