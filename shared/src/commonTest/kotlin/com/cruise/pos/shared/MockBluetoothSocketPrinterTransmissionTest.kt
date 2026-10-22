package com.cruise.pos.shared

import com.cruise.pos.shared.data.printer.BluetoothPrintSpoolerQueue
import com.cruise.pos.shared.data.printer.BluetoothSppManager
import com.cruise.pos.shared.domain.printer.BluetoothConnectionState
import com.cruise.pos.shared.domain.printer.BluetoothDeviceModel
import com.cruise.pos.shared.domain.printer.BluetoothSocketConnection
import com.cruise.pos.shared.domain.printer.PrintJob
import com.cruise.pos.shared.domain.printer.PrintJobResult
import com.cruise.pos.shared.domain.printer.PrinterDeviceStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockBluetoothSocketPrinterTransmissionTest {

    private class MockBluetoothSocket(
        override val remoteAddress: String = "00:11:22:33:44:55"
    ) : BluetoothSocketConnection {
        override var isConnected: Boolean = false
        val transmittedBytes = mutableListOf<ByteArray>()
        var writeShouldFail: Boolean = false

        override suspend fun connect(): Boolean {
            isConnected = true
            return true
        }

        override suspend fun write(bytes: ByteArray): Boolean {
            if (!isConnected || writeShouldFail) return false
            transmittedBytes.add(bytes)
            return true
        }

        override suspend fun read(buffer: ByteArray): Int {
            return 0
        }

        override suspend fun close() {
            isConnected = false
        }
    }

    @Test
    fun testBluetoothPairingAndSocketTransmission() = runTest {
        val mockSocket = MockBluetoothSocket("AA:BB:CC:DD:EE:FF")
        val manager = BluetoothSppManager(socketFactory = { mockSocket })

        val printerDevice = BluetoothDeviceModel(
            name = "Thermal-Receipt-Printer-58",
            address = "AA:BB:CC:DD:EE:FF"
        )

        assertEquals(BluetoothConnectionState.DISCONNECTED, manager.connectionState)

        // Pair and connect
        val connectResult = manager.pairAndConnect(printerDevice)
        assertTrue(connectResult.isSuccess)
        assertEquals(BluetoothConnectionState.CONNECTED, manager.connectionState)
        assertEquals(printerDevice.address, manager.connectedDevice?.address)
        assertTrue(mockSocket.isConnected)

        // Setup spooler
        val spooler = BluetoothPrintSpoolerQueue(socketProvider = { mockSocket })
        val job = PrintJob(
            id = "JOB-001",
            title = "Receipt #RCP-001",
            payload = "TEST_ESC_POS_BYTES".encodeToByteArray()
        )

        spooler.enqueueJob(job)
        assertEquals(1, spooler.queueSize.value)

        // Process job
        val result = spooler.processNextJob()
        assertIs<PrintJobResult.Success>(result)
        assertEquals("JOB-001", (result as PrintJobResult.Success).jobId)
        assertEquals(0, spooler.queueSize.value)
        assertEquals(1, mockSocket.transmittedBytes.size)
        assertEquals("TEST_ESC_POS_BYTES", mockSocket.transmittedBytes[0].decodeToString())

        // Disconnect
        manager.disconnect()
        assertEquals(BluetoothConnectionState.DISCONNECTED, manager.connectionState)
        assertTrue(!mockSocket.isConnected)
    }

    @Test
    fun testSpoolerOutOfPaperAndConnectionLostHandling() = runTest {
        val mockSocket = MockBluetoothSocket("AA:BB:CC:DD:EE:FF")
        val spooler = BluetoothPrintSpoolerQueue(socketProvider = { mockSocket })

        val job = PrintJob(
            id = "JOB-FAIL-1",
            title = "Bar Receipt",
            payload = byteArrayOf(0x1B, 0x40)
        )
        spooler.enqueueJob(job)

        // 1. Simulate Out of Paper
        spooler.updatePrinterHardwareStatus(PrinterDeviceStatus.OUT_OF_PAPER)
        val outOfPaperResult = spooler.processNextJob()
        assertIs<PrintJobResult.OutOfPaper>(outOfPaperResult)
        assertEquals(1, spooler.failedJobs.value.size)
        assertEquals("Printer is out of paper", spooler.failedJobs.value[0].errorMessage)

        // 2. Clear out of paper and retry failed job
        spooler.updatePrinterHardwareStatus(PrinterDeviceStatus.READY)
        val retried = spooler.retryFailedJob("JOB-FAIL-1")
        assertTrue(retried)
        assertEquals(0, spooler.failedJobs.value.size)
        assertEquals(1, spooler.queueSize.value)

        // 3. Socket not connected yet -> ConnectionLost error
        mockSocket.isConnected = false
        val connLostResult = spooler.processNextJob()
        assertIs<PrintJobResult.ConnectionLost>(connLostResult)

        // Connect socket and finish printing
        mockSocket.isConnected = true
        val retryResult = spooler.processNextJob()
        assertIs<PrintJobResult.Success>(retryResult)
        assertEquals(0, spooler.queueSize.value)
    }
}
