package com.cruise.pos.shared.domain.model

import kotlin.math.floor

object SplitFolioCalculator {

    /**
     * Calculates itemized subtotal, proportional maritime tax, gratuity, and custom tips per guest.
     * Guarantees that the sum of each guest's charge breakdown exactly matches the grand total order amount to the cent.
     */
    fun calculateSplitBreakdown(
        order: MultiGuestSplitOrder
    ): List<GuestSplitChargeBreakdown> {
        if (order.guestAssignments.isEmpty()) return emptyList()

        val originalItemIds = order.originalItems.map { it.id }.toSet()
        val allAssignedIds = order.guestAssignments.flatMap { it.assignedItemIds }

        require(order.guestAssignments.all { it.customTipAmount >= 0.0 }) {
            "Custom tip amount must be non-negative"
        }

        val unknownItemIds = allAssignedIds.toSet() - originalItemIds
        require(unknownItemIds.isEmpty()) {
            "Assigned items contain unknown item IDs: $unknownItemIds"
        }

        // Map item subtotals
        val itemSubtotalMap = order.originalItems.associate { it.id to it.itemSubtotal }

        // Compute base subtotal per guest
        val guestSubtotals = order.guestAssignments.associate { assignment ->
            val subtotal = assignment.assignedItemIds.sumOf { itemId ->
                itemSubtotalMap[itemId] ?: 0.0
            }
            assignment.guest.guestId to roundToCents(subtotal)
        }

        val totalSubtotal = roundToCents(guestSubtotals.values.sum())
        if (totalSubtotal == 0.0) {
            return order.guestAssignments.map { assignment ->
                GuestSplitChargeBreakdown(
                    guestId = assignment.guest.guestId,
                    guestName = assignment.guest.guestName,
                    cabinNumber = assignment.guest.cabinNumber,
                    folioId = assignment.guest.folioId,
                    subtotal = 0.0,
                    taxAmount = 0.0,
                    gratuityAmount = 0.0,
                    tipAmount = roundToCents(assignment.customTipAmount),
                    totalCharge = roundToCents(assignment.customTipAmount)
                )
            }
        }

        val orderTotalTax = roundToCents(totalSubtotal * order.taxRate)
        val orderTotalGratuity = roundToCents(totalSubtotal * order.gratuityRate)

        // Distribute tax and gratuity proportionally
        val activeAssignments = order.guestAssignments.filter { (guestSubtotals[it.guest.guestId] ?: 0.0) > 0.0 }
        var distributedTax = 0.0
        var distributedGratuity = 0.0

        val results = mutableListOf<GuestSplitChargeBreakdown>()

        for (i in activeAssignments.indices) {
            val assignment = activeAssignments[i]
            val subtotal = guestSubtotals[assignment.guest.guestId] ?: 0.0
            val isLast = (i == activeAssignments.size - 1)

            val taxShare = if (isLast) {
                roundToCents(orderTotalTax - distributedTax)
            } else {
                val share = roundToCents(orderTotalTax * (subtotal / totalSubtotal))
                distributedTax += share
                share
            }

            val gratuityShare = if (isLast) {
                roundToCents(orderTotalGratuity - distributedGratuity)
            } else {
                val share = roundToCents(orderTotalGratuity * (subtotal / totalSubtotal))
                distributedGratuity += share
                share
            }

            val tip = roundToCents(assignment.customTipAmount)
            val total = roundToCents(subtotal + taxShare + gratuityShare + tip)

            results.add(
                GuestSplitChargeBreakdown(
                    guestId = assignment.guest.guestId,
                    guestName = assignment.guest.guestName,
                    cabinNumber = assignment.guest.cabinNumber,
                    folioId = assignment.guest.folioId,
                    subtotal = subtotal,
                    taxAmount = taxShare,
                    gratuityAmount = gratuityShare,
                    tipAmount = tip,
                    totalCharge = total
                )
            )
        }

        // Add 0-subtotal guests if any
        val zeroAssignments = order.guestAssignments.filter { (guestSubtotals[it.guest.guestId] ?: 0.0) == 0.0 }
        for (assignment in zeroAssignments) {
            val tip = roundToCents(assignment.customTipAmount)
            results.add(
                GuestSplitChargeBreakdown(
                    guestId = assignment.guest.guestId,
                    guestName = assignment.guest.guestName,
                    cabinNumber = assignment.guest.cabinNumber,
                    folioId = assignment.guest.folioId,
                    subtotal = 0.0,
                    taxAmount = 0.0,
                    gratuityAmount = 0.0,
                    tipAmount = tip,
                    totalCharge = tip
                )
            )
        }

        return results
    }

    private fun roundToCents(amount: Double): Double {
        if (amount.isNaN() || amount.isInfinite()) return 0.0
        val cents = floor(amount * 100.0 + 0.5).toLong()
        return cents / 100.0
    }
}
