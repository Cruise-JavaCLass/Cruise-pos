package com.example.cruise_pos.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cruise.pos.shared.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierSelectionBottomSheet(
    menuItem: MenuItem,
    onConfirmSelection: (ConfiguredCartItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Store selected modifier options keyed by groupId
    val selectedOptions = remember {
        mutableStateMapOf<String, MutableSet<ModifierOption>>().apply {
            menuItem.modifierGroups.forEach { group ->
                val defaultOpts = group.options.filter { it.isDefault }.take(group.maxSelections)
                put(group.id, defaultOpts.toMutableSet())
            }
        }
    }

    var quantity by remember { mutableIntStateOf(1) }

    val isSelectionValid = remember {
        derivedStateOf {
            menuItem.modifierGroups.all { group ->
                val count = selectedOptions[group.id]?.size ?: 0
                count >= group.minSelections && count <= group.maxSelections
            }
        }
    }

    val currentTotal = remember {
        derivedStateOf {
            val flattenedModifiers = selectedOptions.values.flatten().map {
                SelectedModifier("", "", it.id, it.name, it.priceDelta)
            }
            CatalogFilterUtils.calculateConfiguredPrice(menuItem.basePrice, flattenedModifiers, quantity)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(menuItem.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Base Price: $${"%.2f".format(menuItem.basePrice)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Modifier Groups
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 380.dp)
            ) {
                items(menuItem.modifierGroups) { group ->
                    val currentSet = selectedOptions[group.id] ?: emptySet()
                    ModifierGroupSection(
                        group = group,
                        selectedOptions = currentSet,
                        onToggleOption = { option ->
                            selectedOptions[group.id] = toggleOption(group, option, currentSet)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Quantity selector & Add to cart button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { if (quantity > 1) quantity-- },
                        enabled = quantity > 1,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "$quantity",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = { quantity++ },
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    enabled = isSelectionValid.value,
                    onClick = {
                        val flatModifiers = mutableListOf<SelectedModifier>()
                        menuItem.modifierGroups.forEach { group ->
                            val opts = selectedOptions[group.id] ?: emptySet()
                            opts.forEach { opt ->
                                flatModifiers.add(
                                    SelectedModifier(
                                        groupId = group.id,
                                        groupName = group.name,
                                        optionId = opt.id,
                                        optionName = opt.name,
                                        priceDelta = opt.priceDelta
                                    )
                                )
                            }
                        }
                        onConfirmSelection(
                            ConfiguredCartItem(
                                menuItem = menuItem,
                                selectedModifiers = flatModifiers,
                                quantity = quantity
                            )
                        )
                    }
                ) {
                    Text("Add to Cart • $${"%.2f".format(currentTotal.value)}")
                }
            }
        }
    }
}

/**
 * Renders a single modifier group with its options and proper accessibility semantics.
 */
@Composable
fun ModifierGroupSection(
    group: ModifierGroup,
    selectedOptions: Set<ModifierOption>,
    onToggleOption: (ModifierOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSingleChoice = group.maxSelections == 1

    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        group.options.forEach { option ->
            val isSelected = selectedOptions.any { it.id == option.id }

            val rowModifier = if (isSingleChoice) {
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onToggleOption(option) }
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        onValueChange = { onToggleOption(option) }
                    )
            }

            Row(
                modifier = rowModifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSingleChoice) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null // handled by selectable row
                        )
                    } else {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null // handled by toggleable row
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.name, style = MaterialTheme.typography.bodyLarge)
                }

                if (option.priceDelta > 0.0) {
                    Text(
                        "+$${"%.2f".format(option.priceDelta)}",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Consolidates modifier selection logic for both single-choice and multi-choice groups.
 *
 * For single-choice groups:
 * - If minSelections is 0 and the option is already selected, unselects (clears) it.
 * - Otherwise, selects the tapped option.
 *
 * For multi-choice groups:
 * - Toggles the tapped option on/off while respecting maxSelections limit.
 */
internal fun toggleOption(
    group: ModifierGroup,
    option: ModifierOption,
    currentSet: Set<ModifierOption>
): MutableSet<ModifierOption> {
    val isSingleChoice = group.maxSelections == 1
    return if (isSingleChoice) {
        val isAlreadySelected = currentSet.any { it.id == option.id }
        if (group.minSelections == 0 && isAlreadySelected) {
            mutableSetOf()
        } else {
            mutableSetOf(option)
        }
    } else {
        val newSet = currentSet.toMutableSet()
        val isSelected = newSet.any { it.id == option.id }
        if (isSelected) {
            newSet.removeAll { it.id == option.id }
        } else if (newSet.size < group.maxSelections) {
            newSet.add(option)
        }
        newSet
    }
}
