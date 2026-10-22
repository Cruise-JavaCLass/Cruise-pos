package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.CabinCreditStatus
import com.cruise.pos.shared.domain.model.FolioAccountStatus
import com.cruise.pos.shared.domain.model.PassengerComplianceProfile
import com.cruise.pos.shared.domain.model.PolicyViolationType
import com.cruise.pos.shared.domain.service.GuestComplianceService
import com.cruise.pos.shared.domain.service.InMemoryGuestSecurityAuditLogger
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuestComplianceAndSpendingLimitTest {

    private val auditLogger = InMemoryGuestSecurityAuditLogger()
    private val complianceService = GuestComplianceService(auditLogger)

    // Fixed ship test time: 2026-10-22 14:00:00 UTC (1792677600000L)
    private val testTimestampMillis = 1792677600000L
    private val utcZone = ZoneId.of("UTC")
    private val tokyoZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun testAgeCalculationAcrossTimeZonesAndBirthdays() {
        // Born 2008-10-23 -> On 2026-10-22 is 17 years old (turns 18 tomorrow)
        val age17 = complianceService.calculateAge("2008-10-23", testTimestampMillis, utcZone)
        assertEquals(17, age17)

        // Born 2008-10-21 -> On 2026-10-22 is 18 years old (turned 18 yesterday)
        val age18 = complianceService.calculateAge("2008-10-21", testTimestampMillis, utcZone)
        assertEquals(18, age18)

        // International date line test (Tokyo UTC+9 vs UTC)
        val ageAcrossDateLine = complianceService.calculateAge("2008-10-23", testTimestampMillis, tokyoZone)
        // In Tokyo it's already 2026-10-22 23:00 -> still 17
        assertEquals(17, ageAcrossDateLine)
    }

    @Test
    fun testUnderageAlcoholPurchaseStrictlyBlockedNoOverride() {
        val minorGuest = PassengerComplianceProfile(
            passengerId = "P-1002",
            fullName = "Emily Johnson",
            cabinNumber = "CAB-8012",
            dateOfBirthIso = "2009-05-15",
            isMinor = true,
            ageAtCurrentVoyage = 17
        )
        val cabinCredit = CabinCreditStatus("CAB-8012", 2000.0, 100.0)

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = minorGuest,
            cabinCredit = cabinCredit,
            purchaseAmount = 16.50,
            itemName = "Mojito Cocktail",
            categoryId = "bar_beverages",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )

        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT))
        assertFalse(result.allowsSupervisorOverride) // Underage alcohol CANNOT be overridden
        assertFalse(result.allowsCashFallback)

        // Audit log record must be stored
        val logs = auditLogger.getAuditLogs()
        assertEquals(1, logs.size)
        assertEquals(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT, logs.first().violationType)
        assertEquals("P-1002", logs.first().passengerId)
    }

    @Test
    fun testAdultAlcoholPurchasePermitted() {
        val adultGuest = PassengerComplianceProfile(
            passengerId = "P-1001",
            fullName = "Alexander Wright",
            cabinNumber = "CAB-8012",
            dateOfBirthIso = "1990-08-12",
            isMinor = false,
            ageAtCurrentVoyage = 36
        )
        val cabinCredit = CabinCreditStatus("CAB-8012", 2000.0, 100.0)

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = adultGuest,
            cabinCredit = cabinCredit,
            purchaseAmount = 16.50,
            itemName = "Mojito Cocktail",
            categoryId = "bar_beverages",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )

        assertTrue(result.isPermitted)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun testDailyAllowanceExceededAllowsOverrideOrCash() {
        val minorWithAllowance = PassengerComplianceProfile(
            passengerId = "P-1005",
            fullName = "Timmy Smith",
            cabinNumber = "CAB-3011",
            dateOfBirthIso = "2012-04-10",
            isMinor = true,
            ageAtCurrentVoyage = 14,
            dailyAllowanceLimit = 50.0,
            dailySpentToday = 45.0 // only $5 remaining
        )
        val cabinCredit = CabinCreditStatus("CAB-3011", 1000.0, 200.0)

        // Attempt buying $15 souvenir
        val result = complianceService.evaluatePurchaseCompliance(
            passenger = minorWithAllowance,
            cabinCredit = cabinCredit,
            purchaseAmount = 15.00,
            itemName = "Cruise Ship Model Toy",
            categoryId = "souvenirs",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )

        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.DAILY_ALLOWANCE_EXCEEDED))
        assertTrue(result.allowsSupervisorOverride) // Parent or supervisor can override
        assertTrue(result.allowsCashFallback)
    }

    @Test
    fun testCabinStopChargeFlagBlocksPurchaseWithAlert() {
        val guest = PassengerComplianceProfile(
            passengerId = "P-2001",
            fullName = "Robert Langdon",
            cabinNumber = "CAB-4105",
            dateOfBirthIso = "1980-01-01",
            isMinor = false,
            ageAtCurrentVoyage = 46
        )
        val frozenCabin = CabinCreditStatus(
            cabinNumber = "CAB-4105",
            creditLimit = 1000.0,
            outstandingUnbilledBalance = 950.0,
            status = FolioAccountStatus.STOP_CHARGE,
            stopChargeReason = "Credit card decline on file"
        )

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = guest,
            cabinCredit = frozenCabin,
            purchaseAmount = 25.00,
            itemName = "Specialty Dinner",
            categoryId = "dining",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )

        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED))
        assertTrue(result.rejectionMessage?.contains("Credit card decline") == true)
        assertTrue(result.allowsSupervisorOverride)
        assertTrue(result.allowsCashFallback)
    }
}
