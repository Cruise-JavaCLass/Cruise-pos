package com.cruise.pos.shared

import com.cruise.pos.shared.data.local.dao.InMemoryTransactionDao
import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus
import com.cruise.pos.shared.data.sync.ExponentialBackoffPolicy
import com.cruise.pos.shared.data.sync.RemoteTransactionSyncApi
import com.cruise.pos.shared.data.sync.SyncBatchRequest
import com.cruise.pos.shared.data.sync.SyncBatchResponse
import com.cruise.pos.shared.data.sync.TransactionSyncEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkManagerSyncWorkerAndIdempotencyTest {

    private class MockRemoteSyncApi : RemoteTransactionSyncApi {
        var lastReceivedRequest: SyncBatchRequest? = null
        var shouldSucceed: Boolean = true

        override suspend fun dispatchBatch(request: SyncBatchRequest): SyncBatchResponse {
            lastReceivedRequest = request
            return if (shouldSucceed) {
                SyncBatchResponse.Success(
                    batchId = request.batchId,
                    syncedIds = request.transactions.map { it.id }
                )
            } else {
                SyncBatchResponse.TransientError(
                    batchId = request.batchId,
                    message = "Satellite network timeout"
                )
            }
        }
    }

    @Test
    fun testChunkedBatchSyncWithUniqueIdempotencyKey() = runTest {
        val dao = InMemoryTransactionDao()
        val mockApi = MockRemoteSyncApi()
        val engine = TransactionSyncEngine(dao, mockApi, chunkSize = 2)

        // Seed 3 pending transactions
        for (i in 1..3) {
            val seq = dao.getNextMonotonicSequence()
            dao.insertTransaction(
                TransactionEntity(
                    id = "TX-00$i",
                    sequenceNumber = seq,
                    outletId = "OUT-01",
                    stationId = "ST-01",
                    cashierId = "USR-01",
                    guestFolioId = "FOLIO-$i",
                    cabinNumber = "100$i",
                    subtotal = 20.0,
                    taxAmount = 2.0,
                    gratuityAmount = 3.6,
                    tipAmount = 0.0,
                    totalAmount = 25.6,
                    signatureSvg = "sig_$i",
                    syncStatus = TransactionSyncStatus.PENDING,
                    createdAtEpochMs = 1774268000000L + i * 1000
                )
            )
        }

        // Execute batch sync (chunkSize = 2 -> takes TX-001 and TX-002)
        val response = engine.syncPendingTransactions(isNetworkAvailable = true)
        assertIs<SyncBatchResponse.Success>(response)

        val success = response as SyncBatchResponse.Success
        assertEquals(2, success.syncedIds.size)
        assertEquals(listOf("TX-001", "TX-002"), success.syncedIds)

        // Verify idempotency key structure on dispatched payload
        val dispatched = mockApi.lastReceivedRequest
        assertEquals("BATCH-1-2", dispatched?.batchId)
        assertTrue(dispatched?.idempotencyKey?.startsWith("IDEMP-SYNC-BATCH-1-2") ?: false)

        // Verify TX-001 & TX-002 marked SYNCED in DAO, TX-003 remains PENDING
        val tx1 = dao.getTransactionById("TX-001")
        val tx3 = dao.getTransactionById("TX-003")
        assertEquals(TransactionSyncStatus.SYNCED, tx1?.syncStatus)
        assertEquals(TransactionSyncStatus.PENDING, tx3?.syncStatus)
    }

    @Test
    fun testExponentialBackoffPolicyDelayCalculation() {
        val policy = ExponentialBackoffPolicy(
            initialDelayMs = 10_000L,
            multiplier = 2.0,
            maxDelayMs = 100_000L,
            maxRetryAttempts = 5
        )

        assertEquals(10_000L, policy.calculateDelayMs(1))
        assertEquals(20_000L, policy.calculateDelayMs(2))
        assertEquals(40_000L, policy.calculateDelayMs(3))
        assertEquals(80_000L, policy.calculateDelayMs(4))
        assertEquals(100_000L, policy.calculateDelayMs(5), "Capped at maxDelayMs")

        assertTrue(policy.hasExceededMaxRetries(5))
    }
    @Test
    fun testFailedTransactionsBelowRetryLimitAreDispatchedAgain() = runTest {
        val dao = InMemoryTransactionDao()
        val mockApi = MockRemoteSyncApi()
        val engine = TransactionSyncEngine(dao, mockApi, chunkSize = 5, maxRetries = 3)

        val seq = dao.getNextMonotonicSequence()
        val tx = TransactionEntity(
            id = "TX-FAILED-01",
            sequenceNumber = seq,
            outletId = "OUT-01",
            stationId = "ST-01",
            cashierId = "USR-01",
            guestFolioId = "FOLIO-99",
            cabinNumber = "9901",
            subtotal = 50.0,
            taxAmount = 5.0,
            gratuityAmount = 9.0,
            tipAmount = 0.0,
            totalAmount = 64.0,
            signatureSvg = "sig_failed",
            syncStatus = TransactionSyncStatus.FAILED,
            retryCount = 1,
            lastError = "Network timeout",
            createdAtEpochMs = 1774268000000L
        )
        dao.insertTransaction(tx)

        // Sync should pick up the failed transaction since retryCount (1) < maxRetries (3)
        val response = engine.syncPendingTransactions(isNetworkAvailable = true)
        assertIs<SyncBatchResponse.Success>(response)
        val success = response as SyncBatchResponse.Success
        assertEquals(listOf("TX-FAILED-01"), success.syncedIds)

        // Verify status is now SYNCED
        val updated = dao.getTransactionById("TX-FAILED-01")
        assertEquals(TransactionSyncStatus.SYNCED, updated?.syncStatus)
    }
}