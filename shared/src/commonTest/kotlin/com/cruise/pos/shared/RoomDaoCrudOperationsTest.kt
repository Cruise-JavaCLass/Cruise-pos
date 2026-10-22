package com.cruise.pos.shared

import com.cruise.pos.shared.data.local.dao.InMemoryOutletDao
import com.cruise.pos.shared.data.local.dao.InMemoryProductDao
import com.cruise.pos.shared.data.local.dao.InMemoryTransactionDao
import com.cruise.pos.shared.data.local.entity.OutletEntity
import com.cruise.pos.shared.data.local.entity.ProductEntity
import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoomDaoCrudOperationsTest {

    @Test
    fun testOutletDaoCrudAndFlow() = runTest {
        val outletDao = InMemoryOutletDao()

        val sampleOutlets = listOf(
            OutletEntity(id = "OUT-01", name = "Lido Deck Pool Bar", deckNumber = 11, outletType = "BAR"),
            OutletEntity(id = "OUT-02", name = "Sunset Dining Room", deckNumber = 5, outletType = "RESTAURANT")
        )

        outletDao.upsertOutlets(sampleOutlets)

        val fetched = outletDao.getOutletById("OUT-01")
        assertNotNull(fetched)
        assertEquals("Lido Deck Pool Bar", fetched.name)
        assertEquals(11, fetched.deckNumber)

        val flowOutlets = outletDao.getAllOutletsFlow().first()
        assertEquals(2, flowOutlets.size)

        outletDao.clearOutlets()
        val emptyFlow = outletDao.getAllOutletsFlow().first()
        assertTrue(emptyFlow.isEmpty())
    }

    @Test
    fun testProductDaoFilteringAndStockUpdate() = runTest {
        val productDao = InMemoryProductDao()

        val products = listOf(
            ProductEntity(id = "P1", outletId = "OUT-01", categoryId = "C1", name = "Mojito", basePrice = 12.0, stockQuota = 20),
            ProductEntity(id = "P2", outletId = "OUT-01", categoryId = "C1", name = "Pina Colada", basePrice = 14.0, stockQuota = 15),
            ProductEntity(id = "P3", outletId = "OUT-02", categoryId = "C2", name = "Filet Mignon", basePrice = 38.0, stockQuota = 5)
        )
        productDao.upsertProducts(products)

        val barProducts = productDao.getProductsByOutletFlow("OUT-01").first()
        assertEquals(2, barProducts.size)

        productDao.updateStockQuota(productId = "P1", quota = 0, isAvailable = false)
        val updatedP1 = productDao.getProductById("P1")
        assertNotNull(updatedP1)
        assertEquals(0, updatedP1.stockQuota)
        assertEquals(false, updatedP1.isAvailable)
    }

    @Test
    fun testTransactionDaoMonotonicSequenceAndPendingFlow() = runTest {
        val transactionDao = InMemoryTransactionDao()

        val seq1 = transactionDao.getNextMonotonicSequence()
        val seq2 = transactionDao.getNextMonotonicSequence()
        assertTrue(seq2 > seq1)

        val tx1 = TransactionEntity(
            id = "TX-001",
            sequenceNumber = seq1,
            outletId = "OUT-01",
            stationId = "ST-01",
            cashierId = "USR-01",
            guestFolioId = "FOLIO-702",
            cabinNumber = "7024",
            subtotal = 26.0,
            taxAmount = 2.6,
            gratuityAmount = 4.68,
            tipAmount = 2.0,
            totalAmount = 35.28,
            signatureSvg = "svg_sig_001",
            syncStatus = TransactionSyncStatus.PENDING,
            createdAtEpochMs = 1774268000000L
        )

        val tx2 = TransactionEntity(
            id = "TX-002",
            sequenceNumber = seq2,
            outletId = "OUT-01",
            stationId = "ST-01",
            cashierId = "USR-01",
            guestFolioId = "FOLIO-804",
            cabinNumber = "8041",
            subtotal = 14.0,
            taxAmount = 1.4,
            gratuityAmount = 2.52,
            tipAmount = 0.0,
            totalAmount = 17.92,
            signatureSvg = "svg_sig_002",
            syncStatus = TransactionSyncStatus.PENDING,
            createdAtEpochMs = 1774268010000L
        )

        transactionDao.insertTransaction(tx1)
        transactionDao.insertTransaction(tx2)

        val pending = transactionDao.getPendingTransactionsFlow().first()
        assertEquals(2, pending.size)
        assertEquals("TX-001", pending[0].id)
        assertEquals("TX-002", pending[1].id)

        // Mark TX-001 as synced
        transactionDao.updateSyncStatus("TX-001", TransactionSyncStatus.SYNCED, syncedAtEpochMs = 1774268050000L)
        val remainingPending = transactionDao.getPendingTransactionsFlow().first()
        assertEquals(1, remainingPending.size)
        assertEquals("TX-002", remainingPending[0].id)
    }
}
