package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.ConnectionType
import com.cruise.pos.shared.domain.model.PreparedTicket
import com.cruise.pos.shared.domain.model.PrinterEndpoint
import com.cruise.pos.shared.hardware.network.EscPosNetworkPrinterClient
import com.cruise.pos.shared.hardware.network.PrintResult
import com.cruise.pos.shared.hardware.template.KitchenOrderTicketTemplate
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private suspend fun dispatchOnce(
        job: SpooledPrintJob,
        payload: ByteArray,
        currentTimeMillis: Long
    ): SpooledPrintJob {
        val primaryResult = printerClient.print(
            host = job.targetPrinter.host,
            port = job.targetPrinter.port,
            payload = payload
        )

        if (primaryResult is PrintResult.Success) {
            return job.copy(
                attempts = job.attempts + 1,
                status = SpoolJobStatus.SENT_TO_PRIMARY,
                completedAtMillis = currentTimeMillis,
                lastError = null
            )
        }

        val primaryError = (primaryResult as PrintResult.Failure).reason
        if (job.backupPrinter != null) {
            val backupResult = printerClient.print(
                host = job.backupPrinter.host,
                port = job.backupPrinter.port,
                payload = payload
            )

            if (backupResult is PrintResult.Success) {
                return job.copy(
                    attempts = job.attempts + 2,
                    status = SpoolJobStatus.FAILED_OVER_TO_BACKUP,
                    completedAtMillis = currentTimeMillis,
                    lastError = "Primary printer [${job.targetPrinter.name}] offline: $primaryError. Rerouted to backup [${job.backupPrinter.name}]"
                )
            }

            val backupError = (backupResult as PrintResult.Failure).reason
            val nextAttempts = job.attempts + 2
            val isExhausted = nextAttempts >= job.maxAttempts
            return job.copy(
                attempts = nextAttempts,
                status = if (isExhausted) SpoolJobStatus.FAILED else SpoolJobStatus.ALERT_TRIGGERED,
                completedAtMillis = if (isExhausted) currentTimeMillis else null,
                lastError = "All printers unreachable. Primary: $primaryError, Backup: $backupError"
            )
        } else {
            val nextAttempts = job.attempts + 1
            val isExhausted = nextAttempts >= job.maxAttempts
            return job.copy(
                attempts = nextAttempts,
                status = if (isExhausted) SpoolJobStatus.FAILED else SpoolJobStatus.ALERT_TRIGGERED,
                completedAtMillis = if (isExhausted) currentTimeMillis else null,
                lastError = primaryError
            )
        }
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

        // 1. Enqueue job under lock with status IN_FLIGHT so concurrent retries skip it
        val initialJob = mutex.withLock {
            val jobId = generateUniqueJobId(ticket.ticketId, currentTimeMillis)
            val job = SpooledPrintJob(
                jobId = jobId,
                ticket = ticket,
                targetPrinter = primaryPrinter,
                backupPrinter = backupPrinter,
                createdAtMillis = currentTimeMillis,
                status = SpoolJobStatus.IN_FLIGHT
            )
            inMemoryQueue.add(job)
            _jobs.value = inMemoryQueue.toList()
            persistQueue()
            job
        }

        val payload = KitchenOrderTicketTemplate.buildEscPosPayload(ticket)

        // 2. Dispatch outside lock with cancellation safety
        val outcome: SpooledPrintJob
        try {
            outcome = dispatchOnce(initialJob, payload, currentTimeMillis)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    updateJobUnderLock(initialJob.copy(status = SpoolJobStatus.ALERT_TRIGGERED))
                }
            }
            throw e
        }

        // 3. Reacquire lock to persist and trigger alert if primary failed
        mutex.withLock {
            if (outcome.status == SpoolJobStatus.FAILED_OVER_TO_BACKUP ||
                outcome.status == SpoolJobStatus.ALERT_TRIGGERED ||
                outcome.status == SpoolJobStatus.FAILED
            ) {
                val primaryError = outcome.lastError ?: "Primary printer offline"
                triggerOfflineAlertUnderLock(
                    printer = primaryPrinter,
                    reason = primaryError,
                    jobId = initialJob.jobId,
                    timestamp = currentTimeMillis,
                    isTerminal = (outcome.status == SpoolJobStatus.FAILED)
                )
            }
            updateJobUnderLock(outcome)
        }
        return outcome
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

        // 2. Perform print attempts outside lock with cancellation protection
        val dispatchOutcomes = mutableListOf<SpooledPrintJob>()
        try {
            for (job in toRetry) {
                val payload = KitchenOrderTicketTemplate.buildEscPosPayload(job.ticket)
                val outcome = dispatchOnce(job, payload, currentTimeMillis)
                dispatchOutcomes.add(outcome)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    val finishedIds = dispatchOutcomes.mapTo(HashSet()) { it.jobId }
                    for (outcome in dispatchOutcomes) {
                        if (outcome.status == SpoolJobStatus.FAILED) {
                            triggerOfflineAlertUnderLock(
                                printer = outcome.targetPrinter,
                                reason = outcome.lastError ?: "Max retry attempts reached",
                                jobId = outcome.jobId,
                                timestamp = currentTimeMillis,
                                isTerminal = true
                            )
                        }
                        updateJobUnderLock(outcome)
                    }
                    toRetry.filter { it.jobId !in finishedIds }.forEach {
                        updateJobUnderLock(it.copy(status = SpoolJobStatus.ALERT_TRIGGERED))
                    }
                }
            }
            throw e
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
                        timestamp = currentTimeMillis,
                        isTerminal = true
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
        timestamp: Long,
        isTerminal: Boolean = false
    ) {
        if (!isTerminal && _alerts.value.any { it.affectedJobId == jobId && it.printerId == printer.id }) {
            return
        }
        val prefix = if (isTerminal) "ABANDONED/FAILED: " else ""
        val alertId = generateUniqueAlertId(printer.id, timestamp)
        val alert = SpoolerAlert(
            alertId = alertId,
            printerId = printer.id,
            printerName = printer.name,
            host = printer.host,
            message = "${prefix}Galley/Bar Printer [${printer.name}] at ${printer.host}:${printer.port} is OFFLINE: $reason. Check ship LAN or paper roll.",
            timestampMillis = timestamp,
            affectedJobId = jobId
        )
        _alerts.value = _alerts.value.filterNot { it.affectedJobId == jobId && it.printerId == printer.id } + alert
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
