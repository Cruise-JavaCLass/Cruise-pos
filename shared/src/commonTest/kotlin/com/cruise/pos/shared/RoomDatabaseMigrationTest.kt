package com.cruise.pos.shared

import com.cruise.pos.shared.data.local.entity.TransactionEntity
import com.cruise.pos.shared.data.local.entity.TransactionSyncStatus
import com.cruise.pos.shared.data.local.migration.DatabaseMigrationManager
import com.cruise.pos.shared.data.local.migration.Migration1To2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RoomDatabaseMigrationTest {

    @Test
    fun testMigrationFromV1ToV2PreservesPendingTransactions() {
        // Transactions created in Schema V1
        val v1PendingTransactions = listOf(
            TransactionEntity(
                id = "TX-V1-100",
                sequenceNumber = 1L,
                outletId = "OUT-BAR-01",
                stationId = "TERM-04",
                cashierId = "STAFF-902",
                guestFolioId = "FOLIO-9912",
                cabinNumber = "9912",
                subtotal = 42.0,
                taxAmount = 4.2,
                gratuityAmount = 7.56,
                tipAmount = 3.0,
                totalAmount = 56.76,
                signatureSvg = "svg_sig_v1_100",
                syncStatus = TransactionSyncStatus.PENDING,
                createdAtEpochMs = 1774268000000L,
                retryCount = 3 // Non-zero retryCount to test reset or preservation
            ),
            TransactionEntity(
                id = "TX-V1-101",
                sequenceNumber = 2L,
                outletId = "OUT-BAR-01",
                stationId = "TERM-04",
                cashierId = "STAFF-902",
                guestFolioId = "FOLIO-7041",
                cabinNumber = "7041",
                subtotal = 18.5,
                taxAmount = 1.85,
                gratuityAmount = 3.33,
                tipAmount = 0.0,
                totalAmount = 23.68,
                signatureSvg = "svg_sig_v1_101",
                syncStatus = TransactionSyncStatus.PENDING,
                createdAtEpochMs = 1774268020000L,
                retryCount = 0
            )
        )

        val migrationManager = DatabaseMigrationManager(listOf(Migration1To2()))

        // Execute migration from V1 to V2
        val migratedTransactions = migrationManager.executeMigrations(
            currentVersion = 1,
            targetVersion = 2,
            transactions = v1PendingTransactions
        )

        // Verifications
        assertEquals(2, migratedTransactions.size, "No pending transactions should be lost during migration")

        val tx1 = migratedTransactions.first { it.id == "TX-V1-100" }
        assertEquals(1L, tx1.sequenceNumber, "Monotonic FIFO ordering must be preserved")
        assertEquals("FOLIO-9912", tx1.guestFolioId)
        assertEquals(56.76, tx1.totalAmount)
        assertEquals(TransactionSyncStatus.PENDING, tx1.syncStatus)
        assertEquals(0, tx1.retryCount)

        val tx2 = migratedTransactions.first { it.id == "TX-V1-101" }
        assertEquals(2L, tx2.sequenceNumber)
        assertEquals(23.68, tx2.totalAmount)
        assertEquals(TransactionSyncStatus.PENDING, tx2.syncStatus)
    }

    @Test
    fun testDowngradeMigrationThrowsException() {
        val migrationManager = DatabaseMigrationManager(listOf(Migration1To2()))
        assertFailsWith<IllegalArgumentException> {
            migrationManager.executeMigrations(currentVersion = 2, targetVersion = 1, transactions = emptyList())
        }
    }
    @Test
    fun testInvalidMigrationEndVersionThrowsIllegalStateException() {
        val badMigration = object : com.cruise.pos.shared.data.local.migration.DatabaseMigration {
            override val startVersion: Int = 1
            override val endVersion: Int = 1 // Not strictly greater than startVersion!
            override fun migrateTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> = transactions
        }
        val manager = DatabaseMigrationManager(listOf(badMigration))
        assertFailsWith<IllegalStateException> {
            manager.executeMigrations(currentVersion = 1, targetVersion = 2, transactions = emptyList())
        }
    }
}