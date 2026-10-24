package com.cruise.pos.shared.domain.model

import com.cruise.pos.shared.data.repository.InventoryCatalogRepository

sealed class CartInventoryValidationResult {
    data object Valid : CartInventoryValidationResult()
    data class DepletedItemsDetected(
        val invalidItems: List<CartItemValidationIssue>
    ) : CartInventoryValidationResult()
}

data class CartItemValidationIssue(
    val cartItemId: String,
    val menuItemId: String,
    val menuItemName: String,
    val requestedQuantity: Int,
    val remainingStock: Int?,
    val reason: String
)

class CartInventoryGuard(
    private val inventoryRepository: InventoryCatalogRepository
) {
    /**
     * Intercepts items in cart right before tender / checkout.
     * Prevents stale cart submissions where items have been marked 86'd or depleted in real-time.
     */
    suspend fun validateCartBeforeTender(items: List<CartItem>): CartInventoryValidationResult {
        val issues = mutableListOf<CartItemValidationIssue>()
        val totalQuantityByMenuItemId = items.groupBy { it.menuItem.id }
            .mapValues { (_, itemList) -> itemList.sumOf { it.quantity } }
        val handledMenuItemIds = mutableSetOf<String>()

        for (item in items) {
            val freshItem = inventoryRepository.getItemById(item.menuItem.id)
            if (freshItem == null || freshItem.is86ed) {
                issues.add(
                    CartItemValidationIssue(
                        cartItemId = item.id,
                        menuItemId = item.menuItem.id,
                        menuItemName = item.menuItem.name,
                        requestedQuantity = item.quantity,
                        remainingStock = freshItem?.stockQuota ?: 0,
                        reason = "Item has been 86'd (Sold Out) by the kitchen"
                    )
                )
            } else {
                val totalRequested = totalQuantityByMenuItemId[item.menuItem.id] ?: item.quantity
                if (freshItem.stockQuota != null && freshItem.stockQuota < totalRequested && handledMenuItemIds.add(item.menuItem.id)) {
                    issues.add(
                        CartItemValidationIssue(
                            cartItemId = item.id,
                            menuItemId = item.menuItem.id,
                            menuItemName = item.menuItem.name,
                            requestedQuantity = totalRequested,
                            remainingStock = freshItem.stockQuota,
                            reason = "Requested $totalRequested units, but only ${freshItem.stockQuota} remain in stock"
                        )
                    )
                }
            }
        }

        return if (issues.isEmpty()) {
            CartInventoryValidationResult.Valid
        } else {
            CartInventoryValidationResult.DepletedItemsDetected(issues)
        }
    }
}
