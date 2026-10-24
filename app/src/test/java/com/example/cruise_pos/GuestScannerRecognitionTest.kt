package com.example.cruise_pos

import com.cruise.pos.shared.domain.model.CardStatus
import com.cruise.pos.shared.domain.model.GuestIdentity
import com.cruise.pos.shared.domain.model.PassengerType
import com.cruise.pos.shared.domain.model.ScanRecognitionResult
import com.cruise.pos.shared.domain.service.GuestCardRecognitionService
import com.cruise.pos.shared.domain.service.InMemoryGuestDirectoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuestScannerRecognitionTest {

    private lateinit var recognitionService: GuestCardRecognitionService

    @Before
    fun setUp() {
        recognitionService = GuestCardRecognitionService(
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
                        folioBalance = 20.0,
                        creditLimit = 200.0
                    ),
                    GuestIdentity(
                        passengerId = "P-1003",
                        rfidCardNumber = "RFID9023C",
                        fullName = "Robert Langdon",
                        cabinNumber = "CAB-4105",
                        age = 45,
                        passengerType = PassengerType.ADULT,
                        cardStatus = CardStatus.REPORTED_LOST
                    )
                )
            )
        )
    }

    @Test
    fun testCameraXQrJsonScanningRecognition() {
        val jsonPayload = """{"pid":"P-1001","card":"RFID9021A","v":1}"""
        val result = recognitionService.parseQrPayload(jsonPayload)

        assertTrue(result is ScanRecognitionResult.Success)
        val guest = (result as ScanRecognitionResult.Success).guest
        assertEquals("Alexander Wright", guest.fullName)
        assertEquals("CAB-8012", guest.cabinNumber)
        assertFalse(guest.isMinor)
    }

    @Test
    fun testNfcCardTapRecognitionAndMinorEntitlement() {
        val tapResult = recognitionService.processNfcCardTap("RFID9022B")
        assertTrue(tapResult is ScanRecognitionResult.Success)
        val guest = (tapResult as ScanRecognitionResult.Success).guest

        assertEquals("Emily Johnson", guest.fullName)
        assertTrue(guest.isMinor)

        // Verify alcohol block
        val auth = recognitionService.authorizePurchase(
            guest = guest,
            purchaseAmount = 18.0,
            containsAlcohol = true
        )
        assertFalse(auth.isAuthorized)
        assertTrue(auth.isAlcoholBlocked)
    }

    @Test
    fun testReportedLostCardRejection() {
        val result = recognitionService.processNfcCardTap("RFID9023C")
        assertTrue(result is ScanRecognitionResult.CardBlocked)
        assertEquals("RFID9023C", (result as ScanRecognitionResult.CardBlocked).rfidCardNumber)
    }
}
