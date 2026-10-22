package com.example.cruise_pos.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingQueueInspectionScreen(
    pendingTransactions: List<TransactionEntity>,
    isSyncing: Boolean,
    onManualSyncTrigger: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("pending_queue_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Offline Sync Queue",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pendingTransactions.size} transactions pending upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onManualSyncTrigger,
                        enabled = !isSyncing && pendingTransactions.isNotEmpty(),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_trigger_sync_now")
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Sync Now")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (pendingTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("empty_pending_queue_view"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All transactions are fully synchronized ✓",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pending_transactions_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingTransactions, key = { it.id }) { tx ->
                        PendingTransactionCard(tx = tx)
                    }
                }
            }
        }
    }
}

@Composable
fun PendingTransactionCard(
    tx: TransactionEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pending_tx_card_${tx.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${tx.sequenceNumber} • ${tx.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (tx.syncStatus) {
                        TransactionSyncStatus.PENDING -> MaterialTheme.colorScheme.errorContainer
                        TransactionSyncStatus.SYNCED -> MaterialTheme.colorScheme.primaryContainer
                        TransactionSyncStatus.FAILED -> MaterialTheme.colorScheme.error
                    }
                ) {
                    Text(
                        text = tx.syncStatus.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (tx.syncStatus) {
                            TransactionSyncStatus.PENDING -> MaterialTheme.colorScheme.onErrorContainer
                            TransactionSyncStatus.SYNCED -> MaterialTheme.colorScheme.onPrimaryContainer
                            TransactionSyncStatus.FAILED -> MaterialTheme.colorScheme.onError
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Folio: ${tx.guestFolioId} (Cabin ${tx.cabinNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$${"%.2f".format(tx.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (tx.retryCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Retry attempts: ${tx.retryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
