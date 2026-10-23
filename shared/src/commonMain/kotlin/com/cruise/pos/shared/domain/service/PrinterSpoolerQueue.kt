package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.ConnectionType
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
    IN_FLIGHT,
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

    private var jobSequence = 0
    private var alertSequence = 0

    init {
        restoreFromPersistence()
    }

    private fun restoreFromPersistence() {
        val serialized = settings?.getStringOrNull(KEY_PERSISTED_JOBS)
        if (!serialized.isNullOrBlank()) {
            try {
                val restored = json.decodeFromString<List<SpooledPrintJob>>(serialized)
                val normalized = restored.map { job ->
                    if (job.status == SpoolJobStatus.IN_FLIGHT) {
                        job.copy(status = SpoolJobStatus.ALERT_TRIGGERED)
                    } else {
                        job
                    }
                }
                inMemoryQueue.addAll(normalized)
                _jobs.value = inMemoryQueue.toList()
            } catch (t: Throwable) {
                println("[PrinterSpoolerQueue] ERROR restoring persisted queue: ${t.message}")
            }
        }
    }

    private fun persistQueue() {
        settings?.let { s ->
            try {
                val unfinishedJobs = inMemoryQueue.filter {
                    it.status == SpoolJobStatus.PENDING ||
                    it.status == SpoolJobStatus.IN_FLIGHT ||
                    it.status == SpoolJobStatus.ALERT_TRIGGERED
                }
                s.putString(KEY_PERSISTED_JOBS, json.encodeToString(unfinishedJobs))
            } catch (t: Throwable) {
                println("[PrinterSpoolerQueue] ERROR persisting queue: ${t.message}")
            }
        }
    }

    private fun generateUniqueJobId(ticketId: String, timestamp: Long): String {
        var candidate = "job-$ticketId-$timestamp"
        if (inMemoryQueue.any { it.jobId == candidate }) {
            do {
                jobSequence++
                candidate = "job-$ticketId-$timestamp-$jobSequence"
            } while (inMemoryQueue.any { it.jobId == candidate })
        }
        return candidate
    }

    private fun generateUniqueAlertId(printerId: String, timestamp: Long): String {
        var candidate = "alert-$printerId-$timestamp"
        if (_alerts.value.any { it.alertId == candidate }) {
            do {
                alertSequence++
                candidate = "alert-$printerId-$timestamp-$alertSequence"
            } while (_alerts.value.any { it.alertId == candidate })
        }
        return candidate
    }

    suspend fun enqueueAndDispatch(
        ticket: PreparedTicket,
        primaryPrinter: PrinterEndpoint,
        backupPrinter: PrinterEndpoint? = null,
        currentTimeMillis: Long = 0L
    ): SpooledPrintJob {
        require(primaryPrinter.connectionType != ConnectionType.BLE) {
            "BLE printers are not supported by PrinterSpoolerQueue (printer ${primaryPrinter.id})"
        }
        backupPrinter?.let {
            require(it.connectionType != ConnectionType.BLE) {
                "BLE backup printers are not supported by PrinterSpoolerQueue (printer ${it.id})"
            }
        }

        // 1. Enqueue job under lock
        val initialJob = mutex.withLock {
            val jobId = generateUniqueJobId(ticket.ticketId, currentTimeMillis)
            val job = SpooledPrintJob(
                jobId = jobId,
                ticket = ticket,
                targetPrinter = primaryPrinter,
                backupPrinter = backupPrinter,
                createdAtMillis = currentTimeMillis
            )
            inMemoryQueue.add(job)
            _jobs.value = inMemoryQueue.toList()
            persistQueue()
            job
        }

        val payload = KitchenOrderTicketTemplate.buildEscPosPayload(ticket)

        // 2. Dispatch outside lock: Attempt Primary Printer
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
            mutex.withLock { updateJobUnderLock(completedJob) }
            return completedJob
        }

        // 3. Primary failed -> Record error and attempt failover to Backup Printer outside lock
        val primaryError = (primaryResult as PrintResult.Failure).reason

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
                mutex.withLock {
                    triggerOfflineAlertUnderLock(primaryPrinter, primaryError, initialJob.jobId, currentTimeMillis)
                    updateJobUnderLock(failedOverJob)
                }
                return failedOverJob
            }
        }

        // 4. Both failed / primary failed with no backup
        val finalJob = initialJob.copy(
            attempts = 1,
            status = SpoolJobStatus.ALERT_TRIGGERED,
            lastError = "All printers unreachable. Primary: $primaryError"
        )
        mutex.withLock {
            triggerOfflineAlertUnderLock(primaryPrinter, primaryError, initialJob.jobId, currentTimeMillis)
            updateJobUnderLock(finalJob)
        }
        return finalJob
    }

    suspend fun retryPendingJobs(currentTimeMillis: Long): List<SpooledPrintJob> {
        // 1. Snapshot and mark pending jobs IN_FLIGHT under lock
        val toRetry = mutex.withLock {
            val pending = inMemoryQueue.filter {
                it.status == SpoolJobStatus.PENDING || it.status == SpoolJobStatus.ALERT_TRIGGERED
            }
            if (pending.isEmpty()) return@withLock emptyList<SpooledPrintJob>()

            pending.map { job ->
                val inFlightJob = job.copy(status = SpoolJobStatus.IN_FLIGHT)
                val idx = inMemoryQueue.indexOfFirst { it.jobId == job.jobId }
                if (idx >= 0) inMemoryQueue[idx] = inFlightJob
                inFlightJob
            }.also {
                _jobs.value = inMemoryQueue.toList()
                persistQueue()
            }
        }

        if (toRetry.isEmpty()) return emptyList()

        // 2. Perform print attempts outside lock
        val dispatchOutcomes = mutableListOf<SpooledPrintJob>()
        for (job in toRetry) {
            val payload = KitchenOrderTicketTemplate.buildEscPosPayload(job.ticket)
            val primaryResult = printerClient.print(
                host = job.targetPrinter.host,
                port = job.targetPrinter.port,
                payload = payload
            )

            if (primaryResult is PrintResult.Success) {
                dispatchOutcomes.add(
                    job.copy(
                        attempts = job.attempts + 1,
                        status = SpoolJobStatus.SENT_TO_PRIMARY,
                        completedAtMillis = currentTimeMillis,
                        lastError = null
                    )
                )
            } else {
                val primaryError = (primaryResult as PrintResult.Failure).reason
                var backupSuccess = false

                if (job.backupPrinter != null) {
                    val backupResult = printerClient.print(
                        host = job.backupPrinter.host,
                        port = job.backupPrinter.port,
                        payload = payload
                    )
                    if (backupResult is PrintResult.Success) {
                        backupSuccess = true
                        dispatchOutcomes.add(
                            job.copy(
                                attempts = job.attempts + 1,
                                status = SpoolJobStatus.FAILED_OVER_TO_BACKUP,
                                completedAtMillis = currentTimeMillis,
                                lastError = "Primary printer [${job.targetPrinter.name}] offline: $primaryError. Rerouted to backup [${job.backupPrinter.name}]"
                            )
                        )
                    }
                }

                if (!backupSuccess) {
                    val nextAttempts = job.attempts + 1
                    val isExhausted = nextAttempts >= job.maxAttempts
                    dispatchOutcomes.add(
                        job.copy(
                            attempts = nextAttempts,
                            lastError = primaryError,
                            status = if (isExhausted) SpoolJobStatus.FAILED else SpoolJobStatus.ALERT_TRIGGERED,
                            completedAtMillis = if (isExhausted) currentTimeMillis else null
                        )
                    )
                }
            }
        }

        // 3. Reacquire lock to apply results, persist, and trigger alerts if FAILED
        return mutex.withLock {
            val finalUpdated = mutableListOf<SpooledPrintJob>()
            for (outcome in dispatchOutcomes) {
                if (outcome.status == SpoolJobStatus.FAILED) {
                    triggerOfflineAlertUnderLock(
                        printer = outcome.targetPrinter,
                        reason = outcome.lastError ?: "Max retry attempts reached",
                        jobId = outcome.jobId,
                        timestamp = currentTimeMillis
                    )
                }
                updateJobUnderLock(outcome)
                finalUpdated.add(outcome)
            }
            finalUpdated
        }
    }

    private fun updateJobUnderLock(job: SpooledPrintJob) {
        val index = inMemoryQueue.indexOfFirst { it.jobId == job.jobId }
        if (index >= 0) {
            inMemoryQueue[index] = job
        } else {
            inMemoryQueue.add(job)
        }
        pruneTerminalJobsUnderLock()
        _jobs.value = inMemoryQueue.toList()
        persistQueue()
    }

    private fun pruneTerminalJobsUnderLock() {
        val unfinished = inMemoryQueue.filter {
            it.status == SpoolJobStatus.PENDING ||
            it.status == SpoolJobStatus.IN_FLIGHT ||
            it.status == SpoolJobStatus.ALERT_TRIGGERED
        }
        val terminal = inMemoryQueue.filter {
            it.status == SpoolJobStatus.SENT_TO_PRIMARY ||
            it.status == SpoolJobStatus.FAILED_OVER_TO_BACKUP ||
            it.status == SpoolJobStatus.FAILED
        }.takeLast(MAX_TERMINAL_JOBS)

        inMemoryQueue.clear()
        inMemoryQueue.addAll(unfinished + terminal)
    }

    private fun triggerOfflineAlertUnderLock(
        printer: PrinterEndpoint,
        reason: String,
        jobId: String,
        timestamp: Long
    ) {
        if (_alerts.value.any { it.affectedJobId == jobId && it.printerId == printer.id }) {
            return
        }
        val alertId = generateUniqueAlertId(printer.id, timestamp)
        val alert = SpoolerAlert(
            alertId = alertId,
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
        private const val MAX_TERMINAL_JOBS = 50
    }
}
