package com.cruise.pos.shared.data.printer

import com.cruise.pos.shared.domain.printer.BluetoothSocketConnection
import com.cruise.pos.shared.domain.printer.PrintJob
import com.cruise.pos.shared.domain.printer.PrintJobResult
import com.cruise.pos.shared.domain.printer.PrintJobStatus
import com.cruise.pos.shared.domain.printer.PrintSpoolerQueue
import com.cruise.pos.shared.domain.printer.PrinterDeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BluetoothPrintSpoolerQueue(
    private val socketProvider: () -> BluetoothSocketConnection?
) : PrintSpoolerQueue {

    private val mutex = Mutex()
    private val pendingQueue = ArrayDeque<PrintJob>()
    private val _failedJobs = MutableStateFlow<List<PrintJob>>(emptyList())
    private val _printerStatus = MutableStateFlow(PrinterDeviceStatus.READY)
    private val _queueSize = MutableStateFlow(0)
    private val _activeJob = MutableStateFlow<PrintJob?>(null)

    override val printerStatus: StateFlow<PrinterDeviceStatus> = _printerStatus.asStateFlow()
    override val queueSize: StateFlow<Int> = _queueSize.asStateFlow()
    override val activeJob: StateFlow<PrintJob?> = _activeJob.asStateFlow()
    override val failedJobs: StateFlow<List<PrintJob>> = _failedJobs.asStateFlow()

    override suspend fun enqueueJob(job: PrintJob): Boolean = mutex.withLock {
        pendingQueue.addLast(job.copy(status = PrintJobStatus.QUEUED))
        _queueSize.value = pendingQueue.size
        return true
    }

    override fun updatePrinterHardwareStatus(status: PrinterDeviceStatus) {
        _printerStatus.value = status
    }

    override suspend fun clearQueue(): Unit = mutex.withLock {
        pendingQueue.clear()
        _queueSize.value = 0
        _activeJob.value = null
    }

    override suspend fun retryFailedJob(jobId: String): Boolean = mutex.withLock {
        val failed = _failedJobs.value.firstOrNull { it.id == jobId } ?: return false
        _failedJobs.value = _failedJobs.value.filterNot { it.id == jobId }
        pendingQueue.addLast(failed.copy(status = PrintJobStatus.QUEUED, errorMessage = null))
        _queueSize.value = pendingQueue.size
        return true
    }

    override suspend fun processNextJob(): PrintJobResult = mutex.withLock {
        if (pendingQueue.isEmpty()) {
            return PrintJobResult.QueueEmpty
        }

        // Check hardware status guard
        val currentStatus = _printerStatus.value
        val headJob = pendingQueue.first()

        if (currentStatus == PrinterDeviceStatus.OUT_OF_PAPER) {
            handleFailedJob(headJob, "Printer is out of paper", PrintJobStatus.FAILED)
            return PrintJobResult.OutOfPaper(headJob.id, "Printer is out of paper")
        }

        if (currentStatus == PrinterDeviceStatus.COVER_OPEN || currentStatus == PrinterDeviceStatus.OVERHEAT) {
            handleFailedJob(headJob, "Printer hardware fault: $currentStatus", PrintJobStatus.FAILED)
            return PrintJobResult.HardwareError(headJob.id, currentStatus)
        }

        val socket = socketProvider()
        if (socket == null || !socket.isConnected) {
            _printerStatus.value = PrinterDeviceStatus.DISCONNECTED
            handleFailedJob(headJob, "Bluetooth printer connection lost", PrintJobStatus.RETRYING)
            return PrintJobResult.ConnectionLost(headJob.id)
        }

        val job = pendingQueue.removeFirst()
        _queueSize.value = pendingQueue.size
        _activeJob.value = job.copy(status = PrintJobStatus.PRINTING)
        _printerStatus.value = PrinterDeviceStatus.PRINTING

        val written = try {
            socket.write(job.payload)
        } catch (e: Exception) {
            _activeJob.value = null
            _failedJobs.value = _failedJobs.value + job.copy(
                status = PrintJobStatus.FAILED,
                errorMessage = "Write failed: ${e.message}"
            )
            _printerStatus.value = PrinterDeviceStatus.ERROR
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            return PrintJobResult.HardwareError(job.id, PrinterDeviceStatus.ERROR)
        }
        return if (written) {
            _activeJob.value = null
            if (_printerStatus.value == PrinterDeviceStatus.PRINTING) {
                _printerStatus.value = PrinterDeviceStatus.READY
            }
            PrintJobResult.Success(job.id)
        } else {
            handleFailedJob(job, "Socket write failed", PrintJobStatus.FAILED)
            _printerStatus.value = PrinterDeviceStatus.ERROR
            PrintJobResult.HardwareError(job.id, PrinterDeviceStatus.ERROR)
        }
    }

    private fun handleFailedJob(job: PrintJob, reason: String, targetStatus: PrintJobStatus) {
        val updatedRetry = job.retryCount + 1
        if (updatedRetry <= job.maxRetries && targetStatus == PrintJobStatus.RETRYING) {
            // Update in-place or re-queue at head
            if (pendingQueue.isNotEmpty() && pendingQueue.first().id == job.id) {
                pendingQueue.removeFirst()
            }
            pendingQueue.addFirst(job.copy(retryCount = updatedRetry, status = PrintJobStatus.RETRYING, errorMessage = reason))
        } else {
            // Push to failed list
            if (pendingQueue.isNotEmpty() && pendingQueue.first().id == job.id) {
                pendingQueue.removeFirst()
            }
            _failedJobs.value = _failedJobs.value + job.copy(
                status = PrintJobStatus.FAILED,
                retryCount = updatedRetry,
                errorMessage = reason
            )
        }
        _queueSize.value = pendingQueue.size
        _activeJob.value = null
    }
}
