package com.cruise.pos.shared.domain.service.fiscal

import com.cruise.pos.shared.domain.model.fiscal.FiscalItemCategory
import com.cruise.pos.shared.domain.model.fiscal.MaritimeFiscalSummary
import com.cruise.pos.shared.domain.model.fiscal.MaritimeTaxBreakdown
import com.cruise.pos.shared.domain.model.fiscal.MaritimeTaxItem
import com.cruise.pos.shared.domain.model.fiscal.MaritimeTerritorialWaters
import kotlin.math.round

/**
 * Calculates duty-free exemptions and VAT/sales tax rates depending on the vessel's geographic coordinates
 * and maritime territorial waters status according to international maritime regulations (UNCLOS).
 */
class MaritimeDutyFreeTaxCalculator(
    private val standardPortTaxRates: Map<FiscalItemCategory, Double> = defaultPortTaxRates()
) {

    companion object {
        fun defaultPortTaxRates(): Map<FiscalItemCategory, Double> = mapOf(
            FiscalItemCategory.ALCOHOL to 20.0,
            FiscalItemCategory.TOBACCO to 25.0,
            FiscalItemCategory.LUXURY_GOODS to 15.0,
            FiscalItemCategory.GENERAL_MERCHANDISE to 10.0,
            FiscalItemCategory.FOOD_AND_BEVERAGE to 8.0,
            FiscalItemCategory.SERVICES to 5.0
        )
    }

    /**
     * Determines territorial status based on distance from nearest national coastline (nautical miles).
     * Nautical boundary rule:
     * - > 12.0 NM: International Waters (Duty-free zero tax)
     * - > 0.0 and <= 12.0 NM: Territorial Waters (Partial tax / reduced rate)
     * - 0.0 or in port: In-Port (Full standard port tax applied)
     */
    fun determineTerritorialStatus(
        nauticalMilesFromCoast: Double,
        isDockedAtPort: Boolean
    ): MaritimeTerritorialWaters {
        return when {
            isDockedAtPort -> MaritimeTerritorialWaters.IN_PORT
            nauticalMilesFromCoast > 12.0 -> MaritimeTerritorialWaters.INTERNATIONAL_WATERS
            else -> MaritimeTerritorialWaters.TERRITORIAL_WATERS
        }
    }

    /**
     * Computes the complete fiscal duty-free breakdown for a collection of purchased items.
     */
    fun calculateFiscalSummary(
        items: List<MaritimeTaxItem>,
        waters: MaritimeTerritorialWaters,
        portName: String? = null,
        countryCode: String? = null
    ): MaritimeFiscalSummary {
        val groupedByCategory = items.groupBy { it.category }
        val breakdowns = mutableListOf<MaritimeTaxBreakdown>()

        for (category in FiscalItemCategory.entries) {
            val categoryItems = groupedByCategory[category] ?: emptyList()
            val gross = roundTwoDecimals(categoryItems.sumOf { it.grossAmount })

            val (exempt, taxable, rate, taxCollected) = when (waters) {
                MaritimeTerritorialWaters.INTERNATIONAL_WATERS -> {
                    // 100% duty-free exemption in international waters
                    Quadruple(gross, 0.0, 0.0, 0.0)
                }
                MaritimeTerritorialWaters.TERRITORIAL_WATERS -> {
                    // Territorial waters: 50% duty-free quota, reduced tax rate
                    val standardRate = standardPortTaxRates[category] ?: 10.0
                    val reducedRate = roundTwoDecimals(standardRate * 0.5)
                    val exemptPortion = roundTwoDecimals(gross * 0.5)
                    val taxablePortion = roundTwoDecimals(gross - exemptPortion)
                    val tax = roundTwoDecimals(taxablePortion * (reducedRate / 100.0))
                    Quadruple(exemptPortion, taxablePortion, reducedRate, tax)
                }
                MaritimeTerritorialWaters.IN_PORT -> {
                    // In-Port: 0% exemption, full local VAT / port taxes
                    val rate = standardPortTaxRates[category] ?: 10.0
                    val tax = roundTwoDecimals(gross * (rate / 100.0))
                    Quadruple(0.0, gross, rate, tax)
                }
            }

            breakdowns.add(
                MaritimeTaxBreakdown(
                    category = category,
                    grossSales = gross,
                    exemptAmount = exempt,
                    taxableAmount = taxable,
                    taxRatePercent = rate,
                    taxCollected = taxCollected
                )
            )
        }

        val totalGross = roundTwoDecimals(breakdowns.sumOf { it.grossSales })
        val totalExempt = roundTwoDecimals(breakdowns.sumOf { it.exemptAmount })
        val totalTaxable = roundTwoDecimals(breakdowns.sumOf { it.taxableAmount })
        val totalTax = roundTwoDecimals(breakdowns.sumOf { it.taxCollected })
        val netPayable = roundTwoDecimals(totalGross + totalTax)

        return MaritimeFiscalSummary(
            territorialStatus = waters,
            portName = portName,
            countryCode = countryCode,
            totalGrossSales = totalGross,
            totalExemptDutyFreeSales = totalExempt,
            totalTaxableSales = totalTaxable,
            totalTaxCollected = totalTax,
            netPayableAmount = netPayable,
            breakdownsByCategory = breakdowns
        )
    }

    private fun roundTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
