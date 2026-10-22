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

class GuestComplianceService(
    private val auditLogger: GuestSecurityAuditLogger = InMemoryGuestSecurityAuditLogger()
) {

    /**
     * Calculates passenger age against ship reference time and timezone.
     */
    fun calculateAge(
        dobIso: String,
        currentTimestampMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val dob = LocalDate.parse(dobIso)
        val shipInstant = Instant.ofEpochMilli(currentTimestampMillis)
        val shipDate = shipInstant.atZone(zoneId).toLocalDate()

        val period = Period.between(dob, shipDate)
        return period.years.coerceAtLeast(0)
    }

    /**
     * Flags whether an item category or name indicates alcohol / liquor / wine / beer.
     */
    fun isAlcoholItem(categoryId: String, itemName: String): Boolean {
        val combined = "${categoryId.lowercase()} ${itemName.lowercase()}"
        return combined.contains("alcohol") ||
                combined.contains("liquor") ||
                combined.contains("cocktail") ||
                combined.contains("wine") ||
                combined.contains("beer") ||
                combined.contains("spirits") ||
                combined.contains("whiskey") ||
                combined.contains("vodka") ||
                combined.contains("gin") ||
                combined.contains("rum") ||
                combined.contains("tequila") ||
                combined.contains("champagne") ||
                combined.contains("bar_beverages")
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
        zoneId: ZoneId = ZoneId.systemDefault(),
        stationId: String = "POS-MAIN",
        cashierId: String = "CASHIER-01"
    ): ComplianceCheckResult {
        val violations = mutableListOf<PolicyViolationType>()
        val reasons = mutableListOf<String>()

        // 1. Age Verification & Alcohol Guard
        val calculatedAge = calculateAge(passenger.dateOfBirthIso, currentTimestampMillis, zoneId)
        val isItemAlcohol = isAlcoholItem(categoryId, itemName)

        if (isItemAlcohol && (calculatedAge < LEGAL_DRINKING_AGE || passenger.parentalAlcoholLock)) {
            violations.add(PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT)
            reasons.add("AGE VIOLATION: Guest is $calculatedAge y/o (Legal Drinking Age is $LEGAL_DRINKING_AGE). Alcohol purchase prohibited by Maritime SOLAS.")

            auditLogger.logViolation(
                SecurityAuditRecord(
                    auditId = "AUDIT-${passenger.passengerId}-${currentTimestampMillis}",
                    passengerId = passenger.passengerId,
                    passengerName = passenger.fullName,
                    cabinNumber = passenger.cabinNumber,
                    violationType = PolicyViolationType.UNDERAGE_ALCOHOL_ATTEMPT,
                    attemptedAmount = purchaseAmount,
                    itemName = itemName,
                    itemCategory = categoryId,
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

        // 2. Cabin Folio Stop-Charge Check
        if (cabinCredit.status == FolioAccountStatus.STOP_CHARGE || cabinCredit.status == FolioAccountStatus.DELINQUENT) {
            violations.add(PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED)
            reasons.add("FOLIO LOCKED: Cabin ${cabinCredit.cabinNumber} account is on ${cabinCredit.status}. Reason: ${cabinCredit.stopChargeReason ?: "Purser lock"}.")

            auditLogger.logViolation(
                SecurityAuditRecord(
                    auditId = "AUDIT-${passenger.passengerId}-${currentTimestampMillis}",
                    passengerId = passenger.passengerId,
                    passengerName = passenger.fullName,
                    cabinNumber = passenger.cabinNumber,
                    violationType = PolicyViolationType.FOLIO_STOP_CHARGE_FLAGGED,
                    attemptedAmount = purchaseAmount,
                    itemName = itemName,
                    itemCategory = categoryId,
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

        // 3. Daily Allowance Limit Check (e.g. Parental controls)
        if (passenger.dailySpentToday + purchaseAmount > passenger.dailyAllowanceLimit) {
            violations.add(PolicyViolationType.DAILY_ALLOWANCE_EXCEEDED)
            val availableDaily = (passenger.dailyAllowanceLimit - passenger.dailySpentToday).coerceAtLeast(0.0)
            reasons.add("PARENTAL LIMIT: Daily allowance limit exceeded. Daily Limit: \$${passenger.dailyAllowanceLimit}, Spent: \$${passenger.dailySpentToday}, Available: \$$availableDaily.")
        }

        // 4. Cabin Credit Limit Ceiling
        if (cabinCredit.outstandingUnbilledBalance + purchaseAmount > cabinCredit.creditLimit) {
            violations.add(PolicyViolationType.CABIN_CREDIT_CEILING_REACHED)
            reasons.add("CREDIT CEILING: Cabin credit limit exceeded. Limit: \$${cabinCredit.creditLimit}, Unbilled: \$${cabinCredit.outstandingUnbilledBalance}, Available: \$${cabinCredit.availableCredit}.")
        }

        if (violations.isNotEmpty()) {
            auditLogger.logViolation(
                SecurityAuditRecord(
                    auditId = "AUDIT-${passenger.passengerId}-${currentTimestampMillis}",
                    passengerId = passenger.passengerId,
                    passengerName = passenger.fullName,
                    cabinNumber = passenger.cabinNumber,
                    violationType = violations.first(),
                    attemptedAmount = purchaseAmount,
                    itemName = itemName,
                    itemCategory = categoryId,
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

    companion object {
        const val LEGAL_DRINKING_AGE = 18
    }
}

interface GuestSecurityAuditLogger {
    fun logViolation(record: SecurityAuditRecord)
    fun getAuditLogs(): List<SecurityAuditRecord>
}

class InMemoryGuestSecurityAuditLogger : GuestSecurityAuditLogger {
    private val records = mutableListOf<SecurityAuditRecord>()

    override fun logViolation(record: SecurityAuditRecord) {
        records.add(record)
    }

    override fun getAuditLogs(): List<SecurityAuditRecord> {
        return records.toList()
    }
}
