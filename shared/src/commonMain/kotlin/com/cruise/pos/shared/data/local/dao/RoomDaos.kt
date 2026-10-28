package com.cruise.pos.shared.data.local.dao

import com.cruise.pos.shared.data.local.entity.OutletEntity
import com.cruise.pos.shared.data.local.entity.ProductEntity
import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface OutletDao {
    fun getAllOutletsFlow(): Flow<List<OutletEntity>>
    suspend fun getOutletById(id: String): OutletEntity?
    suspend fun upsertOutlets(outlets: List<OutletEntity>)
    suspend fun clearOutlets()
}

interface ProductDao {
    fun getProductsByOutletFlow(outletId: String): Flow<List<ProductEntity>>
    suspend fun getProductById(id: String): ProductEntity?
    suspend fun upsertProducts(products: List<ProductEntity>)
    suspend fun updateStockQuota(productId: String, quota: Int, isAvailable: Boolean)
    suspend fun clearProducts()
}

interface TransactionDao {
    fun getPendingTransactionsFlow(): Flow<List<TransactionEntity>>
    suspend fun getNextMonotonicSequence(): Long
    suspend fun insertTransaction(transaction: TransactionEntity)
    suspend fun updateSyncStatus(transactionId: String, status: TransactionSyncStatus, syncedAtEpochMs: Long? = null)
    suspend fun markFailedAndIncrementRetry(transactionId: String, errorMessage: String)
    suspend fun getTransactionById(id: String): TransactionEntity?
    suspend fun getAllTransactions(): List<TransactionEntity>
}

class InMemoryOutletDao : OutletDao {
    private val mutex = Mutex()
    private val outletsFlow = MutableStateFlow<List<OutletEntity>>(emptyList())

    override fun getAllOutletsFlow(): Flow<List<OutletEntity>> = outletsFlow.asStateFlow()

    override suspend fun getOutletById(id: String): OutletEntity? = mutex.withLock {
        outletsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertOutlets(outlets: List<OutletEntity>) = mutex.withLock {
        val current = outletsFlow.value.associateBy { it.id }.toMutableMap()
        outlets.forEach { current[it.id] = it }
        outletsFlow.value = current.values.toList()
    }

    override suspend fun clearOutlets() = mutex.withLock {
        outletsFlow.value = emptyList()
    }
}

class InMemoryProductDao : ProductDao {
    private val mutex = Mutex()
    private val productsFlow = MutableStateFlow<List<ProductEntity>>(emptyList())

    override fun getProductsByOutletFlow(outletId: String): Flow<List<ProductEntity>> {
        return productsFlow.map { list -> list.filter { it.outletId == outletId } }
    }

    override suspend fun getProductById(id: String): ProductEntity? = mutex.withLock {
        productsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertProducts(products: List<ProductEntity>) = mutex.withLock {
        val current = productsFlow.value.associateBy { it.id }.toMutableMap()
        products.forEach { incoming ->
            val existing = current[incoming.id]
            if (existing == null || incoming.version > existing.version) {
                current[incoming.id] = incoming
            }
        }
        productsFlow.value = current.values.toList()
    }

    override suspend fun updateStockQuota(productId: String, quota: Int, isAvailable: Boolean) = mutex.withLock {
        val current = productsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index != -1) {
            current[index] = current[index].copy(stockQuota = quota, isAvailable = isAvailable)
            productsFlow.value = current
        }
    }

    override suspend fun clearProducts() = mutex.withLock {
        productsFlow.value = emptyList()
    }
}

class InMemoryTransactionDao : TransactionDao {
    private val mutex = Mutex()
    private val transactionsFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var sequenceCounter: Long = 0L

    override fun getPendingTransactionsFlow(): Flow<List<TransactionEntity>> {
        return transactionsFlow.map { list ->
            list.filter { it.syncStatus == TransactionSyncStatus.PENDING }
                .sortedBy { it.sequenceNumber }
        }
    }

    override suspend fun getNextMonotonicSequence(): Long = mutex.withLock {
        ++sequenceCounter
    }

    override suspend fun insertTransaction(transaction: TransactionEntity) = mutex.withLock {
        val current = transactionsFlow.value.toMutableList()
        require(current.none { it.id == transaction.id }) { "Transaction with id ${transaction.id} already exists" }
        current.add(transaction)
        transactionsFlow.value = current
    }

    override suspend fun updateSyncStatus(
        transactionId: String,
        status: TransactionSyncStatus,
        syncedAtEpochMs: Long?
    ) = mutex.withLock {
        val current = transactionsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == transactionId }
        if (index != -1) {
            current[index] = current[index].copy(
                syncStatus = status,
                syncedAtEpochMs = syncedAtEpochMs
            )
            transactionsFlow.value = current
        }
    }

    override suspend fun markFailedAndIncrementRetry(
        transactionId: String,
        errorMessage: String
    ) = mutex.withLock {
        val current = transactionsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == transactionId }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(
                syncStatus = TransactionSyncStatus.FAILED,
                retryCount = existing.retryCount + 1,
                lastError = errorMessage
            )
            transactionsFlow.value = current
        }
    }

    override suspend fun getTransactionById(id: String): TransactionEntity? = mutex.withLock {
        transactionsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun getAllTransactions(): List<TransactionEntity> = mutex.withLock {
        transactionsFlow.value
    }
}
