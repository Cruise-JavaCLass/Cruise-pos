package com.cruise.pos.shared.data.sync

import com.cruise.pos.shared.data.local.dao.TransactionDao
import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus
import kotlinx.serialization.Serializable

@Serializable
data class SyncBatchRequest(
    val batchId: String,
    val idempotencyKey: String,
    val transactions: List<TransactionEntity>,
    val clientTimestampEpochMs: Long
)

@Serializable
sealed class SyncBatchResponse {
    @Serializable
    data class Success(val batchId: String, val syncedIds: List<String>) : SyncBatchResponse()

    @Serializable
    data class TransientError(val batchId: String, val message: String) : SyncBatchResponse()

    @Serializable
    data class PermanentError(val batchId: String, val message: String) : SyncBatchResponse()
}

interface RemoteTransactionSyncApi {
    suspend fun dispatchBatch(request: SyncBatchRequest): SyncBatchResponse
}

class TransactionSyncEngine(
    private val transactionDao: TransactionDao,
    private val remoteApi: RemoteTransactionSyncApi,
    private val chunkSize: Int = 10,
    private val maxRetries: Int = 3
) {
    /**
     * Chunks pending transactions in strict FIFO sequence and uploads with individual idempotency keys.
     */
    suspend fun syncPendingTransactions(
        isNetworkAvailable: Boolean = true,
        currentTimestampEpochMs: Long = 1774268000000L
    ): SyncBatchResponse {
        if (!isNetworkAvailable) {
            return SyncBatchResponse.TransientError("OFFLINE", "No network connection available")
        }

        val allTransactions = transactionDao.getAllTransactions()
        val pending = allTransactions
            .filter { it.syncStatus == TransactionSyncStatus.PENDING || (it.syncStatus == TransactionSyncStatus.FAILED && it.retryCount < maxRetries) }
            .sortedBy { it.sequenceNumber }

        if (pending.isEmpty()) {
            return SyncBatchResponse.Success("EMPTY", emptyList())
        }

        val batchToSync = pending.take(chunkSize)
        val batchId = "BATCH-${batchToSync.first().sequenceNumber}-${batchToSync.last().sequenceNumber}"
        val idempotencyKey = "IDEMP-SYNC-$batchId-${batchToSync.size}"

        val request = SyncBatchRequest(
            batchId = batchId,
            idempotencyKey = idempotencyKey,
            transactions = batchToSync,
            clientTimestampEpochMs = currentTimestampEpochMs
        )

        val response = remoteApi.dispatchBatch(request)

        when (response) {
            is SyncBatchResponse.Success -> {
                for (id in response.syncedIds) {
                    transactionDao.updateSyncStatus(id, TransactionSyncStatus.SYNCED, currentTimestampEpochMs)
                }
            }
            is SyncBatchResponse.TransientError -> {
                for (tx in batchToSync) {
                    transactionDao.markFailedAndIncrementRetry(tx.id, response.message)
                }
            }
            is SyncBatchResponse.PermanentError -> {
                for (tx in batchToSync) {
                    transactionDao.markFailedAndIncrementRetry(tx.id, response.message)
                }
            }
        }

        return response
    }
}
