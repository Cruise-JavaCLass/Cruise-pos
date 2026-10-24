package com.example.cruise_pos.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.domain.model.CartItem
import com.cruise.pos.shared.domain.model.CartTaxBreakdown

@Composable
fun ShoppingCartDrawer(
    items: List<CartItem>,
    taxBreakdown: CartTaxBreakdown,
    isOpen: Boolean,
    onQuantityIncrease: (String) -> Unit,
    onQuantityDecrease: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onHoldOrder: () -> Unit,
    onCheckout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Surface(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight()
            .testTag("cart_drawer_surface"),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Order Cart",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("cart_title")
                    )
                    Text(
                        text = "${items.sumOf { it.quantity }} items selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_cart_btn")
                ) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Cart Items List
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("empty_cart_container"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your cart is currently empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cart_items_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        CartItemRow(
                            item = item,
                            onIncrease = { onQuantityIncrease(item.id) },
                            onDecrease = { onQuantityDecrease(item.id) },
                            onRemove = { onItemRemove(item.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Pricing & Tax Summary Section
            CartSummarySection(taxBreakdown = taxBreakdown)

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onHoldOrder,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hold_cart_order_btn")
                ) {
                    Text("Park/Hold", maxLines = 1)
                }

                Button(
                    onClick = onCheckout,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("checkout_cart_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Pay Now", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cart_item_row_${item.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.menuItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.selectedModifiers.isNotEmpty()) {
                    Text(
                        text = item.selectedModifiers.joinToString(", ") { it.optionName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$${formatPrice(item.effectiveUnitPrice)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (item.discountPerUnit > 0.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($${formatPrice(item.regularUnitPrice)})",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Stepper controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (item.quantity == 1) {
                            onRemove()
                        } else {
                            onDecrease()
                        }
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_decrease_${item.id}"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                ) {
                    Text(if (item.quantity == 1) "X" else "-")
                }

                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("qty_label_${item.id}")
                )

                Button(
                    onClick = onIncrease,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_increase_${item.id}"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
fun CartSummarySection(
    taxBreakdown: CartTaxBreakdown,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cart_tax_summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SummaryRow(label = "Subtotal", amount = taxBreakdown.subtotalBeforeDiscount)

        if (taxBreakdown.totalDiscount > 0.0) {
            SummaryRow(
                label = "Happy Hour Savings",
                amount = -taxBreakdown.totalDiscount,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SummaryRow(
            label = "Maritime VAT (${(taxBreakdown.taxRate * 100).toInt()}%)",
            amount = taxBreakdown.taxAmount,
            highlight = taxBreakdown.taxRate == 0.0
        )

        SummaryRow(
            label = "Auto Gratuity (${(taxBreakdown.gratuityRate * 100).toInt()}%)",
            amount = taxBreakdown.gratuityAmount
        )

        Divider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Estimated Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$${formatPrice(taxBreakdown.grandTotal)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("cart_grand_total")
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: Double,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) MaterialTheme.colorScheme.tertiary else color
        )
        Text(
            text = if (amount < 0) "-$${formatPrice(-amount)}" else "$${formatPrice(amount)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

private fun formatPrice(price: Double): String {
    val rounded = kotlin.math.round(price * 100.0) / 100.0
    val intPart = rounded.toLong()
    val fracPart = kotlin.math.round((rounded - intPart) * 100).toInt()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
