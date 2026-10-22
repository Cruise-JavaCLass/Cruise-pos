package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.PreparedTicket
import com.cruise.pos.shared.domain.model.PrinterEndpoint
import com.cruise.pos.shared.hardware.network.EscPosNetworkPrinterClient
import com.cruise.pos.shared.hardware.network.PrintResult
import com.cruise.pos.shared.hardware.template.KitchenOrderTicketTemplate
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class SpoolJobStatus {
    PENDING,
    SENT_TO_PRIMARY,
    FAILED_OVER_TO_BACKUP,
    FAILED,
    ALERT_TRIGGERED
}

@Serializable
data class SpooledPrintJob(
    val jobId: String,
    val ticket: PreparedTicket,
    val targetPrinter: PrinterEndpoint,
    val backupPrinter: PrinterEndpoint?,
    val attempts: Int = 0,
    val maxAttempts: Int = 3,
    val status: SpoolJobStatus = SpoolJobStatus.PENDING,
    val lastError: String? = null,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null
)

@Serializable
data class SpoolerAlert(
    val alertId: String,
    val printerId: String,
    val printerName: String,
    val host: String,
    val message: String,
    val timestampMillis: Long,
    val affectedJobId: String
)

class PrinterSpoolerQueue(
    private val printerClient: EscPosNetworkPrinterClient,
    private val settings: Settings? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val mutex = Mutex()
    private val inMemoryQueue = mutableListOf<SpooledPrintJob>()
    private val _alerts = MutableStateFlow<List<SpoolerAlert>>(emptyList())
    val alerts: StateFlow<List<SpoolerAlert>> = _alerts.asStateFlow()

    private val _jobs = MutableStateFlow<List<SpooledPrintJob>>(emptyList())
    val jobs: StateFlow<List<SpooledPrintJob>> = _jobs.asStateFlow()

    init {
        restoreFromPersistence()
    }

    private fun restoreFromPersistence() {
        val serialized = settings?.getStringOrNull(KEY_PERSISTED_JOBS)
        if (!serialized.isNullOrBlank()) {
            try {
                val restored = json.decodeFromString<List<SpooledPrintJob>>(serialized)
                inMemoryQueue.addAll(restored)
                _jobs.value = inMemoryQueue.toList()
            } catch (_: Throwable) {
                // Ignore corruption, start clean
            }
        }
    }

    private fun persistQueue() {
        settings?.let { s ->
            try {
                val snapshot = inMemoryQueue.toList()
                s.putString(KEY_PERSISTED_JOBS, json.encodeToString(snapshot))
            } catch (_: Throwable) {}
        }
    }

    suspend fun enqueueAndDispatch(
        ticket: PreparedTicket,
        primaryPrinter: PrinterEndpoint,
        backupPrinter: PrinterEndpoint? = null,
        currentTimeMillis: Long = 0L
    ): SpooledPrintJob = mutex.withLock {
        val jobId = "job-${ticket.ticketId}-${currentTimeMillis}"
        val initialJob = SpooledPrintJob(
            jobId = jobId,
            ticket = ticket,
            targetPrinter = primaryPrinter,
            backupPrinter = backupPrinter,
            createdAtMillis = currentTimeMillis
        )
        inMemoryQueue.add(initialJob)
        _jobs.value = inMemoryQueue.toList()
        persistQueue()

        val payload = KitchenOrderTicketTemplate.buildEscPosPayload(ticket)

        // 1. Attempt Primary Printer
        val primaryResult = printerClient.print(
            host = primaryPrinter.host,
            port = primaryPrinter.port,
            payload = payload
        )

        if (primaryResult is PrintResult.Success) {
            val completedJob = initialJob.copy(
                attempts = 1,
                status = SpoolJobStatus.SENT_TO_PRIMARY,
                completedAtMillis = currentTimeMillis
            )
            updateJob(completedJob)
            return@withLock completedJob
        }

        // 2. Primary failed -> Record error and trigger crew alert
        val primaryError = (primaryResult as PrintResult.Failure).reason
        triggerOfflineAlert(primaryPrinter, primaryError, jobId, currentTimeMillis)

        // 3. Attempt Failover to Backup / Local Printer
        if (backupPrinter != null) {
            val backupResult = printerClient.print(
                host = backupPrinter.host,
                port = backupPrinter.port,
                payload = payload
            )

            if (backupResult is PrintResult.Success) {
                val failedOverJob = initialJob.copy(
                    attempts = 2,
                    status = SpoolJobStatus.FAILED_OVER_TO_BACKUP,
                    lastError = "Primary printer [${primaryPrinter.name}] offline: $primaryError. Rerouted to backup [${backupPrinter.name}]",
                    completedAtMillis = currentTimeMillis
                )
                updateJob(failedOverJob)
                return@withLock failedOverJob
            }
        }

        // 4. Both failed -> Retain in spooler queue for retry & alert crew
        val finalJob = initialJob.copy(
            attempts = if (backupPrinter != null) 2 else 1,
            status = SpoolJobStatus.ALERT_TRIGGERED,
            lastError = "All printers unreachable. Primary: $primaryError"
        )
        updateJob(finalJob)
        return@withLock finalJob
    }

    suspend fun retryPendingJobs(currentTimeMillis: Long): List<SpooledPrintJob> = mutex.withLock {
        val pending = inMemoryQueue.filter {
            it.status == SpoolJobStatus.PENDING || it.status == SpoolJobStatus.ALERT_TRIGGERED
        }

        val updated = mutableListOf<SpooledPrintJob>()
        for (job in pending) {
            val payload = KitchenOrderTicketTemplate.buildEscPosPayload(job.ticket)
            val result = printerClient.print(
                host = job.targetPrinter.host,
                port = job.targetPrinter.port,
                payload = payload
            )

            val newJob = if (result is PrintResult.Success) {
                job.copy(
                    attempts = job.attempts + 1,
                    status = SpoolJobStatus.SENT_TO_PRIMARY,
                    completedAtMillis = currentTimeMillis,
                    lastError = null
                )
            } else {
                val error = (result as PrintResult.Failure).reason
                job.copy(
                    attempts = job.attempts + 1,
                    lastError = error,
                    status = if (job.attempts + 1 >= job.maxAttempts) SpoolJobStatus.FAILED else SpoolJobStatus.ALERT_TRIGGERED
                )
            }
            updateJob(newJob)
            updated.add(newJob)
        }
        return@withLock updated
    }

    private fun updateJob(job: SpooledPrintJob) {
        val index = inMemoryQueue.indexOfFirst { it.jobId == job.jobId }
        if (index >= 0) {
            inMemoryQueue[index] = job
        } else {
            inMemoryQueue.add(job)
        }
        _jobs.value = inMemoryQueue.toList()
        persistQueue()
    }

    private fun triggerOfflineAlert(
        printer: PrinterEndpoint,
        reason: String,
        jobId: String,
        timestamp: Long
    ) {
        val alert = SpoolerAlert(
            alertId = "alert-${printer.id}-${timestamp}",
            printerId = printer.id,
            printerName = printer.name,
            host = printer.host,
            message = "Galley/Bar Printer [${printer.name}] at ${printer.host}:${printer.port} is OFFLINE: $reason. Check ship LAN or paper roll.",
            timestampMillis = timestamp,
            affectedJobId = jobId
        )
        _alerts.value = _alerts.value + alert
    }

    suspend fun dismissAlert(alertId: String) {
        mutex.withLock {
            _alerts.value = _alerts.value.filterNot { it.alertId == alertId }
        }
    }

    suspend fun getQueueSnapshot(): List<SpooledPrintJob> = mutex.withLock {
        inMemoryQueue.toList()
    }

    companion object {
        private const val KEY_PERSISTED_JOBS = "printer_spooler_persisted_jobs"
    }
}
