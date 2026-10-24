package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PolicyViolationType {
    UNDERAGE_ALCOHOL_ATTEMPT,
    DAILY_ALLOWANCE_EXCEEDED,
    CABIN_CREDIT_CEILING_REACHED,
    FOLIO_STOP_CHARGE_FLAGGED,
    PARENTAL_ARCADE_CASINO_LOCK
}

@Serializable
enum class FolioAccountStatus {
    ACTIVE,
    STOP_CHARGE,
    DELINQUENT,
    CLOSED
}

@Serializable
data class PassengerComplianceProfile(
    val passengerId: String,
    val fullName: String,
    val cabinNumber: String,
    val dateOfBirthIso: String, // "YYYY-MM-DD"
    val isMinor: Boolean,
    val ageAtCurrentVoyage: Int,
    val dailyAllowanceLimit: Double? = null,
    val dailySpentToday: Double = 0.0,
    val parentalAlcoholLock: Boolean = isMinor,
    val parentalArcadeLock: Boolean = false
)

@Serializable
data class CabinCreditStatus(
    val cabinNumber: String,
    val creditLimit: Double = 1500.0,
    val outstandingUnbilledBalance: Double = 0.0,
    val status: FolioAccountStatus = FolioAccountStatus.ACTIVE,
    val stopChargeReason: String? = null
) {
    val availableCredit: Double
        get() = (creditLimit - outstandingUnbilledBalance).coerceAtLeast(0.0)
}

@Serializable
data class ComplianceCheckResult(
    val isPermitted: Boolean,
    val violations: List<PolicyViolationType> = emptyList(),
    val rejectionMessage: String? = null,
    val allowsSupervisorOverride: Boolean = false,
    val allowsCashFallback: Boolean = false
)

@Serializable
data class SecurityAuditRecord(
    val auditId: String,
    val passengerId: String,
    val passengerName: String,
    val cabinNumber: String,
    val violationType: PolicyViolationType,
    val attemptedAmount: Double,
    val itemName: String,
    val itemCategory: String,
    val stationId: String,
    val cashierId: String,
    val timestampMillis: Long,
    val notes: String
)
