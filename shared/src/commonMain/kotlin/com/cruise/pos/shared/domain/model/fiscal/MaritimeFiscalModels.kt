package com.cruise.pos.shared.domain.model.fiscal

import kotlinx.serialization.Serializable

@Serializable
enum class MaritimeTerritorialWaters {
    INTERNATIONAL_WATERS, // > 12 nautical miles from baseline: zero-rated / 100% duty-free
    TERRITORIAL_WATERS,    // <= 12 nautical miles: standard maritime duty rules
    IN_PORT                // Docked at port: standard local VAT / port sales tax applicable
}

@Serializable
enum class FiscalItemCategory {
    ALCOHOL,
    TOBACCO,
    LUXURY_GOODS,
    GENERAL_MERCHANDISE,
    FOOD_AND_BEVERAGE,
    SERVICES
}

@Serializable
data class MaritimeTaxItem(
    val itemId: String,
    val description: String,
    val category: FiscalItemCategory,
    val quantity: Int,
    val unitPrice: Double
) {
    val grossAmount: Double get() = quantity * unitPrice
}

@Serializable
data class MaritimeTaxBreakdown(
    val category: FiscalItemCategory,
    val grossSales: Double,
    val exemptAmount: Double,
    val taxableAmount: Double,
    val taxRatePercent: Double,
    val taxCollected: Double
)

@Serializable
data class MaritimeFiscalSummary(
    val territorialStatus: MaritimeTerritorialWaters,
    val portName: String?,
    val countryCode: String?,
    val totalGrossSales: Double,
    val totalExemptDutyFreeSales: Double,
    val totalTaxableSales: Double,
    val totalTaxCollected: Double,
    val netPayableAmount: Double,
    val breakdownsByCategory: List<MaritimeTaxBreakdown>
)
