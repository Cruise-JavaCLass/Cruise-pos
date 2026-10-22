package com.example.cruise_pos.ui.split

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.cruise.pos.shared.domain.model.GuestFolioProfile
import com.cruise.pos.shared.domain.model.GuestSplitAssignment

@Composable
fun MultiGuestSplitDrawer(
    isOpen: Boolean,
    items: List<CartItem>,
    guests: List<GuestFolioProfile>,
    assignments: List<GuestSplitAssignment>,
    selectedGuestId: String,
    onGuestSelected: (String) -> Unit,
    onToggleItemAssignment: (guestId: String, cartItemId: String) -> Unit,
    onAddGuestProfile: (GuestFolioProfile) -> Unit,
    onProceedToTender: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Surface(
        modifier = modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight()
            .testTag("multi_guest_split_drawer"),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                        text = "Split Folio By Guest",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("split_drawer_title")
                    )
                    Text(
                        text = "Partition items across staterooms & guests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_split_drawer_btn")
                ) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            // Guest Selector Chips / Tabs
            Text(
                text = "Select Guest to Assign Items:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("guest_chips_row")
            ) {
                items(guests, key = { it.guestId }) { guest ->
                    val isSelected = guest.guestId == selectedGuestId
                    val assignedCount = assignments.firstOrNull { it.guest.guestId == guest.guestId }?.assignedItemIds?.size ?: 0

                    FilterChip(
                        selected = isSelected,
                        onClick = { onGuestSelected(guest.guestId) },
                        label = {
                            Text("${guest.guestName} (${guest.cabinNumber}) • $assignedCount items")
                        },
                        modifier = Modifier.testTag("chip_guest_${guest.guestId}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Item Assignment List
            Text(
                text = "Tap item to assign/unassign to current guest:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("split_items_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val assignedGuest = assignments.firstOrNull { it.assignedItemIds.contains(item.id) }
                    val isAssignedToCurrent = assignedGuest?.guest?.guestId == selectedGuestId

                    SplitItemRow(
                        item = item,
                        assignedGuestName = assignedGuest?.guest?.guestName,
                        assignedCabin = assignedGuest?.guest?.cabinNumber,
                        isAssignedToCurrent = isAssignedToCurrent,
                        onClick = {
                            onToggleItemAssignment(selectedGuestId, item.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            // Footer / Proceed Button
            val allAssigned = items.all { item ->
                assignments.any { it.assignedItemIds.contains(item.id) }
            }

            Button(
                onClick = onProceedToTender,
                enabled = allAssigned && assignments.any { it.assignedItemIds.isNotEmpty() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_proceed_to_tender"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (allAssigned) "Proceed to Multi-Tender" else "Assign all items first",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SplitItemRow(
    item: CartItem,
    assignedGuestName: String?,
    assignedCabin: String?,
    isAssignedToCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("split_item_row_${item.id}"),
        shape = RoundedCornerShape(8.dp),
        border = if (isAssignedToCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isAssignedToCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.quantity}x ${item.menuItem.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$${"%.2f".format(item.itemSubtotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (assignedGuestName != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAssignedToCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "$assignedGuestName ($assignedCabin)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAssignedToCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Unassigned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
