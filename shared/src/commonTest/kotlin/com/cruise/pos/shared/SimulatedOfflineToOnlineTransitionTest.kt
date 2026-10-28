package com.cruise.pos.shared

import com.cruise.pos.shared.data.local.dao.InMemoryTransactionDao
import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus
import com.cruise.pos.shared.data.sync.RemoteTransactionSyncApi
import com.cruise.pos.shared.data.sync.SyncBatchRequest
import com.cruise.pos.shared.data.sync.SyncBatchResponse
import com.cruise.pos.shared.data.sync.SyncTriggerPolicy
import com.cruise.pos.shared.data.sync.TransactionSyncEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulatedOfflineToOnlineTransitionTest {

    private class StatefulRemoteSyncApi : RemoteTransactionSyncApi {
        var isOnline: Boolean = false
        val receivedBatches = mutableListOf<SyncBatchRequest>()

        override suspend fun dispatchBatch(request: SyncBatchRequest): SyncBatchResponse {
            return if (!isOnline) {
                SyncBatchResponse.TransientError(request.batchId, "Connection refused: offline")
            } else {
                receivedBatches.add(request)
                SyncBatchResponse.Success(request.batchId, request.transactions.map { it.id })
            }
        }
    }

    @Test
    fun testOfflineQueueAccumulatesAndFlushesUponOnlineTransition() = runTest {
        val dao = InMemoryTransactionDao()
        val mockApi = StatefulRemoteSyncApi()
        val engine = TransactionSyncEngine(dao, mockApi, chunkSize = 5)
        val triggerPolicy = SyncTriggerPolicy(requiresUnmeteredWifi = true)

        // 1. Vessel is in maritime blind spot (Offline)
        mockApi.isOnline = false

        // Cashier records 2 transactions while offline
        val seq1 = dao.getNextMonotonicSequence()
        dao.insertTransaction(
            TransactionEntity(
                id = "TX-OFF-01",
                sequenceNumber = seq1,
                outletId = "OUT-01",
                stationId = "ST-01",
                cashierId = "USR-01",
                guestFolioId = "FOLIO-1",
                cabinNumber = "8021",
                subtotal = 30.0,
                taxAmount = 0.0,
                gratuityAmount = 5.4,
                tipAmount = 0.0,
                totalAmount = 35.4,
                signatureSvg = "sig_1",
                syncStatus = TransactionSyncStatus.PENDING,
                createdAtEpochMs = 1774268000000L
            )
        )

        val seq2 = dao.getNextMonotonicSequence()
        dao.insertTransaction(
            TransactionEntity(
                id = "TX-OFF-02",
                sequenceNumber = seq2,
                outletId = "OUT-01",
                stationId = "ST-01",
                cashierId = "USR-01",
                guestFolioId = "FOLIO-2",
                cabinNumber = "8022",
                subtotal = 15.0,
                taxAmount = 0.0,
                gratuityAmount = 2.7,
                tipAmount = 1.0,
                totalAmount = 18.7,
                signatureSvg = "sig_2",
                syncStatus = TransactionSyncStatus.PENDING,
                createdAtEpochMs = 1774268005000L
            )
        )

        // Sync attempt fails while offline
        val offlineResponse = engine.syncPendingTransactions(isNetworkAvailable = false)
        assertIs<SyncBatchResponse.TransientError>(offlineResponse)
        assertEquals(0, mockApi.receivedBatches.size)

        // Policy check verifies cannot sync without Wi-Fi
        val canSyncOffline = triggerPolicy.canExecuteSync(isWifiConnected = false, isBatteryOk = true, isDeviceIdle = false)
        assertTrue(!canSyncOffline)

        // 2. Vessel connects to satellite Wi-Fi (Online transition)
        mockApi.isOnline = true
        val canSyncOnline = triggerPolicy.canExecuteSync(isWifiConnected = true, isBatteryOk = true, isDeviceIdle = false)
        assertTrue(canSyncOnline)

        // Sync attempt triggers successfully
        val onlineResponse = engine.syncPendingTransactions(isNetworkAvailable = true)
        assertIs<SyncBatchResponse.Success>(onlineResponse)
        val success = onlineResponse as SyncBatchResponse.Success
        assertEquals(2, success.syncedIds.size)
        assertEquals(1, mockApi.receivedBatches.size)

        // Verify database state: both transactions are now SYNCED
        val tx1 = dao.getTransactionById("TX-OFF-01")
        val tx2 = dao.getTransactionById("TX-OFF-02")
        assertEquals(TransactionSyncStatus.SYNCED, tx1?.syncStatus)
        assertEquals(TransactionSyncStatus.SYNCED, tx2?.syncStatus)
    }
}
