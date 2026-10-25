package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.CabinCreditStatus
import com.cruise.pos.shared.domain.model.ComplianceCheckResult
import com.cruise.pos.shared.domain.model.FolioAccountStatus
import com.cruise.pos.shared.domain.model.PassengerComplianceProfile
import com.cruise.pos.shared.domain.model.PolicyViolationType
import com.cruise.pos.shared.domain.model.SecurityAuditRecord
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Collections

class GuestComplianceService(
    private val auditLogger: GuestSecurityAuditLogger = InMemoryGuestSecurityAuditLogger(),
    private val defaultZoneId: ZoneId = ZoneId.systemDefault(),
    private val defaultStationId: String = "POS-MAIN",
    private val defaultCashierId: String = "CASHIER-01"
) {

    private val nonAlcoholicMarkers = listOf("non-alcoholic", "virgin mojito", "virgin colada", "virgin mary", "zero-proof")
    private val alcoholKeywords = setOf(
        "alcohol", "liquor", "cocktail", "wine", "beer", "spirit", "spirits",
        "whiskey", "whisky", "vodka", "gin", "rum", "tequila", "champagne",
        "bar_beverages"
    )

    private fun toCents(amount: Double): Long = kotlin.math.round(amount * 100.0).toLong()

    /**
     * Calculates passenger age against ship reference time and timezone.
     */
    fun calculateAge(
        dobIso: String,
        currentTimestampMillis: Long,
        zoneId: ZoneId = defaultZoneId
    ): Int {
        return try {
            val dob = LocalDate.parse(dobIso.trim())
            val shipInstant = Instant.ofEpochMilli(currentTimestampMillis)
            val shipDate = shipInstant.atZone(zoneId).toLocalDate()

            val period = Period.between(dob, shipDate)
            period.years.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Flags whether an item category or name indicates alcohol / liquor / wine / beer.
     * Uses word tokenization to prevent false positives like 'Ginger Ale'.
     */
    fun isAlcoholItem(categoryId: String, itemName: String): Boolean {
        val lowerItem = itemName.lowercase()
        val lowerCategory = categoryId.lowercase()

        val combined = "$lowerCategory $lowerItem"
        if (nonAlcoholicMarkers.any { it in combined }) return false

        val tokens = combined.split(Regex("[^a-z0-9_]+")).filter { it.isNotBlank() }.toSet()
        return tokens.any { it in alcoholKeywords || it.removeSuffix("s") in alcoholKeywords }
    }

    /**
     * Validates proposed cart line item or purchase against passenger profile and cabin credit status.
     */
    fun evaluatePurchaseCompliance(
        passenger: PassengerComplianceProfile,
        cabinCredit: CabinCreditStatus,
        purchaseAmount: Double,
        itemName: String,
        categoryId: String,
        currentTimestampMillis: Long,
        zoneId: ZoneId = defaultZoneId,
        stationId: String = defaultStationId,
        cashierId: String = defaultCashierId
    ): ComplianceCheckResult {
        val violations = mutableListOf<PolicyViolationType>()
        val reasons = mutableListOf<String>()

        // 1. Age Verification & Alcohol Guard
        val calculatedAge = calculateAge(passenger.dateOfBirthIso, currentTimestampMillis, zoneId)
        val isItemAlcohol = isAlcoholItem(categoryId, itemName)

        if (isItemAlcohol && (calculatedAge < LEGAL_DRINKING_AGE || passenger.parentalAlcoholLock)) {
            violations.add(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT)
            reasons.add("AGE VIOLATION: Guest is $calculatedAge y/o (Legal Drinking Age is $LEGAL_DRINKING_AGE). Alcohol purchase prohibited under maritime policy.")

            auditLogger.logViolation(
                createAuditRecord(
                    passenger = passenger,
                    violationType = PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT,
                    attemptedAmount = purchaseAmount,
                    itemName = itemName,
                    categoryId = categoryId,
                    stationId = stationId,
                    cashierId = cashierId,
                    timestampMillis = currentTimestampMillis,
                    notes = "Underage alcohol purchase blocked for ${passenger.fullName} (DOB: ${passenger.dateOfBirthIso}, Age: $calculatedAge)"
                )
            )

            // Strictly forbidden - NO supervisor override permitted for underage alcohol
            return ComplianceCheckResult(
                isPermitted = false,
                violations = violations,
                rejectionMessage = reasons.joinToString("\n"),
                allowsSupervisorOverride = false,
                allowsCashFallback = false
            )
        }

        // 2. Cabin Folio Stop-Charge Check (including DELINQUENT and CLOSED)
        if (cabinCredit.status == FolioAccountStatus.STOP_CHARGE ||
            cabinCredit.status == FolioAccountStatus.DELINQUENT ||
            cabinCredit.status == FolioAccountStatus.CLOSED
        ) {
            violations.add(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED)
            reasons.add("FOLIO LOCKED: Cabin ${cabinCredit.cabinNumber} account is on ${cabinCredit.status}. Reason: ${cabinCredit.stopChargeReason ?: "Purser lock"}.")

            auditLogger.logViolation(
                createAuditRecord(
                    passenger = passenger,
                    violationType = PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED,
                    attemptedAmount = purchaseAmount,
                    itemName = itemName,
                    categoryId = categoryId,
                    stationId = stationId,
                    cashierId = cashierId,
                    timestampMillis = currentTimestampMillis,
                    notes = "Stop-charge blocked on Cabin ${cabinCredit.cabinNumber} (${cabinCredit.status})"
                )
            )

            return ComplianceCheckResult(
                isPermitted = false,
                violations = violations,
                rejectionMessage = reasons.joinToString("\n"),
                allowsSupervisorOverride = true,
                allowsCashFallback = true
            )
        }

        // 3. Parental Arcade / Casino Activity Lock
        val isArcadeOrCasino = categoryId.lowercase() in listOf("arcade", "casino") ||
                itemName.lowercase().contains("arcade") ||
                itemName.lowercase().contains("casino")
        if (passenger.parentalArcadeLock && isArcadeOrCasino) {
            violations.add(PolicyViolationType.PARENTAL_ARCADE_CASINO_LOCK)
            reasons.add("PARENTAL LOCK: Arcade and casino activities are restricted for guest ${passenger.fullName}.")
        }

        // 4. Daily Allowance Limit Check (e.g. Parental controls)
        val dailyAllowanceLimitCents = passenger.dailyAllowanceLimit?.let { toCents(it) }
        if (dailyAllowanceLimitCents != null && toCents(passenger.dailySpentToday) + toCents(purchaseAmount) > dailyAllowanceLimitCents) {
            violations.add(PolicyViolationType.DAILY_ALLOWANCE_EXCEEDED)
            val availableDaily = (passenger.dailyAllowanceLimit - passenger.dailySpentToday).coerceAtLeast(0.0)
            reasons.add("PARENTAL LIMIT: Daily allowance limit exceeded. Daily Limit: \$${passenger.dailyAllowanceLimit}, Spent: \$${passenger.dailySpentToday}, Available: \$$availableDaily.")
        }

        // 5. Cabin Credit Limit Ceiling
        if (toCents(cabinCredit.outstandingUnbilledBalance) + toCents(purchaseAmount) > toCents(cabinCredit.creditLimit)) {
            violations.add(PolicyViolationType.CABIN_CREDIT_CEILING_REACHED)
            reasons.add("CREDIT CEILING: Cabin credit limit exceeded. Limit: \$${cabinCredit.creditLimit}, Unbilled: \$${cabinCredit.outstandingUnbilledBalance}, Available: \$${cabinCredit.availableCredit}.")
        }

        if (violations.isNotEmpty()) {
            auditLogger.logViolation(
                createAuditRecord(
                    passenger = passenger,
                    violationType = violations.first(),
                    attemptedAmount = purchaseAmount,
                    itemName = itemName,
                    categoryId = categoryId,
                    stationId = stationId,
                    cashierId = cashierId,
                    timestampMillis = currentTimestampMillis,
                    notes = reasons.joinToString(" | ")
                )
            )

            return ComplianceCheckResult(
                isPermitted = false,
                violations = violations,
                rejectionMessage = reasons.joinToString("\n"),
                allowsSupervisorOverride = true,
                allowsCashFallback = true
            )
        }

        return ComplianceCheckResult(
            isPermitted = true
        )
    }

    private fun createAuditRecord(
        passenger: PassengerComplianceProfile,
        violationType: PolicyViolationType,
        attemptedAmount: Double,
        itemName: String,
        categoryId: String,
        stationId: String,
        cashierId: String,
        timestampMillis: Long,
        notes: String
    ): SecurityAuditRecord {
        return SecurityAuditRecord(
            auditId = "AUDIT-${passenger.passengerId}-${timestampMillis}",
            passengerId = passenger.passengerId,
            passengerName = passenger.fullName,
            cabinNumber = passenger.cabinNumber,
            violationType = violationType,
            attemptedAmount = attemptedAmount,
            itemName = itemName,
            itemCategory = categoryId,
            stationId = stationId,
            cashierId = cashierId,
            timestampMillis = timestampMillis,
            notes = notes
        )
    }

    companion object {
        const val LEGAL_DRINKING_AGE = 18
    }
}

interface GuestSecurityAuditLogger {
    fun logViolation(record: SecurityAuditRecord)
    fun getAuditLogs(): List<SecurityAuditRecord>
}

class InMemoryGuestSecurityAuditLogger : GuestSecurityAuditLogger {
    private val records = Collections.synchronizedList(mutableListOf<SecurityAuditRecord>())

    override fun logViolation(record: SecurityAuditRecord) {
        records.add(record)
    }

    override fun getAuditLogs(): List<SecurityAuditRecord> {
        synchronized(records) {
            return records.toList()
        }
    }
}
