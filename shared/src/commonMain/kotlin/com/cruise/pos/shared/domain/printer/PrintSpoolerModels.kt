package com.cruise.pos.shared.domain.printer

import kotlinx.coroutines.flow.StateFlow

enum class PrinterDeviceStatus {
    READY,
    PRINTING,
    OUT_OF_PAPER,
    COVER_OPEN,
    OVERHEAT,
    DISCONNECTED,
    ERROR
}

enum class PrintJobStatus {
    QUEUED,
    PRINTING,
    COMPLETED,
    FAILED,
    RETRYING
}

data class PrintJob(
    val id: String,
    val title: String,
    val payload: ByteArray,
    val status: PrintJobStatus = PrintJobStatus.QUEUED,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long = 0L
)

interface PrintSpoolerQueue {
    val printerStatus: StateFlow<PrinterDeviceStatus>
    val queueSize: StateFlow<Int>
    val activeJob: StateFlow<PrintJob?>
    val failedJobs: StateFlow<List<PrintJob>>

    fun enqueueJob(job: PrintJob): Boolean
    suspend fun processNextJob(): PrintJobResult
    fun updatePrinterHardwareStatus(status: PrinterDeviceStatus)
    fun clearQueue()
    fun retryFailedJob(jobId: String): Boolean
}

sealed class PrintJobResult {
    data class Success(val jobId: String) : PrintJobResult()
    data class OutOfPaper(val jobId: String, val message: String) : PrintJobResult()
    data class HardwareError(val jobId: String, val status: PrinterDeviceStatus) : PrintJobResult()
    data class ConnectionLost(val jobId: String) : PrintJobResult()
    data object QueueEmpty : PrintJobResult()
}
