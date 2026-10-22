package com.cruise.pos.shared.domain.model

import kotlin.math.round

object MaritimeTaxCalculator {

    const val PORT_VAT_RATE = 0.10 // 10% VAT when docked or in port territorial waters
    const val INTERNATIONAL_WATERS_TAX_RATE = 0.0 // 0% tax in international waters
    const val TERRITORIAL_WATERS_TAX_RATE = 0.10 // 10% tax in territorial waters

    /**
     * Resolves applicable maritime tax rate based on navigation mode / coordinates.
     */
    fun resolveTaxRate(position: VesselPosition): Double {
        return when (position.navigationMode) {
            VesselNavigationMode.PORT_DOCKED -> PORT_VAT_RATE
            VesselNavigationMode.TERRITORIAL_WATERS -> TERRITORIAL_WATERS_TAX_RATE
            VesselNavigationMode.INTERNATIONAL_WATERS -> INTERNATIONAL_WATERS_TAX_RATE
        }
    }

    /**
     * Calculates tax, gratuity, and totals with exact currency rounding to cents (2 decimal places).
     */
    fun calculateBreakdown(
        items: List<CartItem>,
        position: VesselPosition,
        gratuityRate: Double = 0.18
    ): CartTaxBreakdown {
        val subtotalBeforeDiscount = items.sumOf { it.regularUnitPrice * it.quantity }
        val totalDiscount = items.sumOf { it.discountPerUnit * it.quantity }
        val taxableSubtotal = items.sumOf { it.itemSubtotal }

        val taxRate = resolveTaxRate(position)
        val taxAmount = roundToCents(taxableSubtotal * taxRate)
        val gratuityAmount = roundToCents(taxableSubtotal * gratuityRate)
        val grandTotal = roundToCents(taxableSubtotal + taxAmount + gratuityAmount)

        return CartTaxBreakdown(
            subtotalBeforeDiscount = roundToCents(subtotalBeforeDiscount),
            totalDiscount = roundToCents(totalDiscount),
            taxableSubtotal = roundToCents(taxableSubtotal),
            taxRate = taxRate,
            taxAmount = taxAmount,
            gratuityRate = gratuityRate,
            gratuityAmount = gratuityAmount,
            grandTotal = grandTotal
        )
    }

    private fun roundToCents(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}
