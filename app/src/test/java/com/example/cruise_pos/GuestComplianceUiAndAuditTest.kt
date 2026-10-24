package com.example.cruise_pos

import com.cruise.pos.shared.domain.model.CabinCreditStatus
import com.cruise.pos.shared.domain.model.FolioAccountStatus
import com.cruise.pos.shared.domain.model.PassengerComplianceProfile
import com.cruise.pos.shared.domain.model.PolicyViolationType
import com.cruise.pos.shared.domain.service.GuestComplianceService
import com.cruise.pos.shared.domain.service.InMemoryGuestSecurityAuditLogger
import com.example.cruise_pos.ui.compliance.warningStyleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class GuestComplianceUiAndAuditTest {

    private lateinit var auditLogger: InMemoryGuestSecurityAuditLogger
    private lateinit var complianceService: GuestComplianceService

    @Before
    fun setUp() {
        auditLogger = InMemoryGuestSecurityAuditLogger()
        complianceService = GuestComplianceService(auditLogger)
    }

    @Test
    fun testWarningStyleForDifferentViolations() {
        val underageStyle = warningStyleFor(listOf(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT))
        assertEquals("UNDERAGE ALCOHOL RESTRICTION", underageStyle.title)
        assertEquals("🔞", underageStyle.icon)

        val stopChargeStyle = warningStyleFor(listOf(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED))
        assertEquals("CABIN FOLIO STOP-CHARGE", stopChargeStyle.title)

        val creditCeilingStyle = warningStyleFor(listOf(PolicyViolationType.CABIN_CREDIT_CEILING_REACHED))
        assertEquals("CABIN CREDIT LIMIT REACHED", creditCeilingStyle.title)

        val arcadeStyle = warningStyleFor(listOf(PolicyViolationType.PARENTAL_ARCADE_CASINO_LOCK))
        assertEquals("PARENTAL ACTIVITY RESTRICTION", arcadeStyle.title)

        val spendingStyle = warningStyleFor(listOf(PolicyViolationType.DAILY_ALLOWANCE_EXCEEDED))
        assertEquals("PARENTAL SPENDING LIMIT ALERT", spendingStyle.title)
    }

    @Test
    fun testUnderageAlcoholAttemptIsAuditedAndDenied() {
        val minor = PassengerComplianceProfile(
            passengerId = "P-1002",
            fullName = "Emily Johnson",
            cabinNumber = "CAB-8012",
            dateOfBirthIso = "2009-05-15",
            isMinor = true,
            ageAtCurrentVoyage = 17
        )
        val cabin = CabinCreditStatus("CAB-8012", 2000.0, 100.0)

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = minor,
            cabinCredit = cabin,
            purchaseAmount = 14.0,
            itemName = "Pina Colada (Rum)",
            categoryId = "bar_beverages",
            currentTimestampMillis = 1792677600000L,
            zoneId = ZoneId.of("UTC"),
            stationId = "STATION-LIDO",
            cashierId = "CASHIER-05"
        )

        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT))
        assertFalse(result.allowsSupervisorOverride)

        val auditLogs = auditLogger.getAuditLogs()
        assertEquals(1, auditLogs.size)
        val log = auditLogs.first()
        assertEquals("P-1002", log.passengerId)
        assertEquals("STATION-LIDO", log.stationId)
        assertEquals(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT, log.violationType)
    }

    @Test
    fun testParentalSpendingLimitWarningAllowsFallback() {
        val child = PassengerComplianceProfile(
            passengerId = "P-1009",
            fullName = "Lucas Martin",
            cabinNumber = "CAB-1022",
            dateOfBirthIso = "2014-02-18",
            isMinor = true,
            ageAtCurrentVoyage = 12,
            dailyAllowanceLimit = 30.0,
            dailySpentToday = 25.0
        )
        val cabin = CabinCreditStatus("CAB-1022", 1500.0, 300.0)

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = child,
            cabinCredit = cabin,
            purchaseAmount = 15.0,
            itemName = "Gelato & Smoothies",
            categoryId = "desserts",
            currentTimestampMillis = 1792677600000L,
            zoneId = ZoneId.of("UTC")
        )

        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.DAILY_ALLOWANCE_EXCEEDED))
        assertTrue(result.allowsSupervisorOverride)
        assertTrue(result.allowsCashFallback)
    }

    @Test
    fun testCabinStopChargeAuditLogging() {
        val guest = PassengerComplianceProfile(
            passengerId = "P-3001",
            fullName = "Diana Prince",
            cabinNumber = "CAB-9011",
            dateOfBirthIso = "1988-03-22",
            isMinor = false,
            ageAtCurrentVoyage = 38
        )
        val frozenCabin = CabinCreditStatus(
            cabinNumber = "CAB-9011",
            status = FolioAccountStatus.STOP_CHARGE,
            stopChargeReason = "Disputed card charge"
        )

        val result = complianceService.evaluatePurchaseCompliance(
            passenger = guest,
            cabinCredit = frozenCabin,
            purchaseAmount = 50.0,
            itemName = "Spa Massage Session",
            categoryId = "spa",
            currentTimestampMillis = 1792677600000L,
            zoneId = ZoneId.of("UTC")
        )

        assertFalse(result.isPermitted)
        assertTrue(result.violations.contains(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED))

        val auditLogs = auditLogger.getAuditLogs()
        assertEquals(1, auditLogs.size)
        assertEquals(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED, auditLogs.first().violationType)
    }
}
