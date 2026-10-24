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
        // Born 2008-10-23 -> On 2026-10-22 14:00 UTC is 17 years old (turns 18 tomorrow)
        val age17 = complianceService.calculateAge("2008-10-23", testTimestampMillis, utcZone)
        assertEquals(17, age17)

        // Born 2008-10-21 -> On 2026-10-22 is 18 years old (turned 18 yesterday)
        val age18 = complianceService.calculateAge("2008-10-21", testTimestampMillis, utcZone)
        assertEquals(18, age18)

        // International date line test: 2026-10-22 16:00 UTC is 2026-10-23 01:00 in Tokyo
        val lateUtcMillis = 1792684800000L
        assertEquals(17, complianceService.calculateAge("2008-10-23", lateUtcMillis, utcZone))
        assertEquals(18, complianceService.calculateAge("2008-10-23", lateUtcMillis, tokyoZone))
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
        assertTrue(result.allowsSupervisorOverride)
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

    @Test
    fun testCabinCreditCeilingReachedAndExactLimitBoundaries() {
        val guest = PassengerComplianceProfile(
            passengerId = "P-4001",
            fullName = "Sarah Connor",
            cabinNumber = "CAB-5020",
            dateOfBirthIso = "1985-06-15",
            isMinor = false,
            ageAtCurrentVoyage = 41
        )
        val cabin = CabinCreditStatus(
            cabinNumber = "CAB-5020",
            creditLimit = 1000.0,
            outstandingUnbilledBalance = 900.0
        )

        // Purchase exactly $100 -> outstanding + 100 = 1000 == creditLimit -> Allowed
        val exactResult = complianceService.evaluatePurchaseCompliance(
            passenger = guest,
            cabinCredit = cabin,
            purchaseAmount = 100.0,
            itemName = "Boutique Perfume",
            categoryId = "retail",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertTrue(exactResult.isPermitted)

        // Purchase $100.01 -> outstanding + 100.01 > 1000 -> Exceeded
        val overResult = complianceService.evaluatePurchaseCompliance(
            passenger = guest,
            cabinCredit = cabin,
            purchaseAmount = 100.01,
            itemName = "Boutique Perfume",
            categoryId = "retail",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertFalse(overResult.isPermitted)
        assertTrue(overResult.violations.contains(PolicyViolationType.CABIN_CREDIT_CEILING_REACHED))
    }

    @Test
    fun testDelinquentAndClosedFolioStatusesBlockPurchases() {
        val guest = PassengerComplianceProfile(
            passengerId = "P-5001",
            fullName = "John Doe",
            cabinNumber = "CAB-6010",
            dateOfBirthIso = "1975-02-10",
            isMinor = false,
            ageAtCurrentVoyage = 51
        )

        val delinquentCabin = CabinCreditStatus("CAB-6010", status = FolioAccountStatus.DELINQUENT)
        val delinquentResult = complianceService.evaluatePurchaseCompliance(
            passenger = guest,
            cabinCredit = delinquentCabin,
            purchaseAmount = 20.0,
            itemName = "Coffee",
            categoryId = "beverages",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertFalse(delinquentResult.isPermitted)
        assertTrue(delinquentResult.violations.contains(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED))

        val closedCabin = CabinCreditStatus("CAB-6010", status = FolioAccountStatus.CLOSED)
        val closedResult = complianceService.evaluatePurchaseCompliance(
            passenger = guest,
            cabinCredit = closedCabin,
            purchaseAmount = 20.0,
            itemName = "Coffee",
            categoryId = "beverages",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertFalse(closedResult.isPermitted)
        assertTrue(closedResult.violations.contains(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED))
    }

    @Test
    fun testAdultDefaultAllowanceUnlimitedLargePurchase() {
        val adultGuest = PassengerComplianceProfile(
            passengerId = "P-6001",
            fullName = "Bruce Wayne",
            cabinNumber = "CAB-1001",
            dateOfBirthIso = "1980-05-20",
            isMinor = false,
            ageAtCurrentVoyage = 46,
            dailyAllowanceLimit = null // Unlimited default
        )
        val cabin = CabinCreditStatus("CAB-1001", creditLimit = 10000.0, outstandingUnbilledBalance = 0.0)

        // Large purchase of $2500 should succeed since allowance is unlimited and credit limit is sufficient
        val result = complianceService.evaluatePurchaseCompliance(
            passenger = adultGuest,
            cabinCredit = cabin,
            purchaseAmount = 2500.0,
            itemName = "Diamond Watch",
            categoryId = "luxury_jewelry",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertTrue(result.isPermitted)
    }

    @Test
    fun testGingerAleDoesNotTriggerAlcoholFalsePositive() {
        val minor = PassengerComplianceProfile(
            passengerId = "P-7001",
            fullName = "Peter Parker",
            cabinNumber = "CAB-2002",
            dateOfBirthIso = "2010-08-10",
            isMinor = true,
            ageAtCurrentVoyage = 16
        )
        val cabin = CabinCreditStatus("CAB-2002", creditLimit = 1000.0)

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = minor,
            cabinCredit = cabin,
            purchaseAmount = 4.50,
            itemName = "Cold Ginger Ale Soda",
            categoryId = "soda_beverages",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertTrue(result.isPermitted, "Ginger Ale must not be flagged as alcohol")
    }

    @Test
    fun testAdultWithParentalAlcoholLockIsBlocked() {
        val adultWithLock = PassengerComplianceProfile(
            passengerId = "P-8001",
            fullName = "Young Adult Guest",
            cabinNumber = "CAB-3003",
            dateOfBirthIso = "2005-01-01",
            isMinor = false,
            ageAtCurrentVoyage = 21,
            parentalAlcoholLock = true
        )
        val cabin = CabinCreditStatus("CAB-3003", creditLimit = 1000.0)

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = adultWithLock,
            cabinCredit = cabin,
            purchaseAmount = 12.0,
            itemName = "Red Wine Glass",
            categoryId = "bar_beverages",
            currentTimestampMillis = testTimestampMillis,
            zoneId = utcZone
        )
        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT))
    }
}
