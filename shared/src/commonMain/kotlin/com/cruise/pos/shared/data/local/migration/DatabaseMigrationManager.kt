package com.cruise.pos.shared.data.local.migration

import com.cruise.pos.shared.data.local.entity.TransactionEntity

data class DatabaseSchema(
    val version: Int,
    val description: String
)

interface DatabaseMigration {
    val startVersion: Int
    val endVersion: Int
    fun migrateTransactions(transactions: List<TransactionEntity>): List<TransactionEntity>
}

/**
 * Migration from Schema V1 to V2:
 * Adds retryCount and ensures all pending transactions maintain monotonic ordering without data loss.
 */
class Migration1To2 : DatabaseMigration {
    override val startVersion: Int = 1
    override val endVersion: Int = 2

    override fun migrateTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return transactions.map { tx ->
            if (tx.retryCount != 0) tx.copy(retryCount = 0) else tx
        }
    }
}

class DatabaseMigrationManager(
    private val migrations: List<DatabaseMigration> = listOf(Migration1To2())
) {
    fun executeMigrations(
        currentVersion: Int,
        targetVersion: Int,
        transactions: List<TransactionEntity>
    ): List<TransactionEntity> {
        require(currentVersion <= targetVersion) {
            "Downgrade from version $currentVersion to $targetVersion is not supported"
        }
        if (currentVersion == targetVersion) return transactions

        var version = currentVersion
        var data = transactions

        while (version < targetVersion) {
            val migration = migrations.firstOrNull { it.startVersion == version }
                ?: error("Missing database migration from version $version to target $targetVersion")
            data = migration.migrateTransactions(data)
            version = migration.endVersion
        }

        return data
    }
}
