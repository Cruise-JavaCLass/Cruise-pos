package com.cruise.pos.shared.domain.model

import kotlin.math.round

@kotlinx.serialization.Serializable
enum class DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}

@kotlinx.serialization.Serializable
data class HappyHourRule(
    val id: String,
    val name: String,
    val eligibleCategoryIds: Set<String> = emptySet(),
    val eligibleMenuItemIds: Set<String> = emptySet(),
    val discountType: DiscountType,
    val discountValue: Double, // e.g. 0.20 for 20% or 2.0 for $2.0 off
    val startHour24: Int,      // e.g. 16 (4 PM)
    val endHour24: Int,        // e.g. 19 (7 PM)
    val isEnabled: Boolean = true
) {
    fun isTimeActive(currentHour24: Int): Boolean {
        if (!isEnabled) return false
        return if (startHour24 <= endHour24) {
            currentHour24 in startHour24 until endHour24
        } else {
            // Over-midnight span (e.g. 22 to 02)
            currentHour24 >= startHour24 || currentHour24 < endHour24
        }
    }

    fun isItemEligible(menuItem: MenuItem): Boolean {
        if (!isEnabled) return false
        if (eligibleMenuItemIds.contains(menuItem.id)) return true
        if (eligibleCategoryIds.contains(menuItem.categoryId)) return true
        return false
    }

    fun calculateDiscount(regularUnitPrice: Double): Double {
        val rawDiscount = when (discountType) {
            DiscountType.PERCENTAGE -> regularUnitPrice * discountValue
            DiscountType.FIXED_AMOUNT -> discountValue
        }
        val capped = rawDiscount.coerceIn(0.0, regularUnitPrice)
        return round(capped * 100.0) / 100.0
    }
}

object HappyHourEngine {

    /**
     * Evaluates happy hour rules and applies the best promotional discount per item.
     */
    fun applyPromotions(
        items: List<CartItem>,
        rules: List<HappyHourRule>,
        currentHour24: Int
    ): List<CartItem> {
        val activeRules = rules.filter { it.isTimeActive(currentHour24) }
        if (activeRules.isEmpty()) {
            return items.map { it.copy(isHappyHourEligible = false, discountPerUnit = 0.0) }
        }

        return items.map { item ->
            val matchingRules = activeRules.filter { it.isItemEligible(item.menuItem) }
            if (matchingRules.isEmpty()) {
                item.copy(isHappyHourEligible = false, discountPerUnit = 0.0)
            } else {
                // Pick best discount for the guest
                val bestDiscount = matchingRules.maxOf { rule ->
                    rule.calculateDiscount(item.regularUnitPrice)
                }
                item.copy(
                    isHappyHourEligible = bestDiscount > 0.0,
                    discountPerUnit = bestDiscount
                )
            }
        }
    }
}
