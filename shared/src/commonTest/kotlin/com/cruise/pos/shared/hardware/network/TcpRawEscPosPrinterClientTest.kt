package com.cruise.pos.shared.hardware.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TcpRawEscPosPrinterClientTest {

    // Ktor Sockets are hard to test perfectly cross-platform without a mock server or complex setup.
    // At minimum, we should test the basic interface signature and timeout handling behavior by attempting a connection
    // to a known bad IP (e.g. 192.0.2.1 TEST-NET-1 which shouldn't resolve quickly), expecting a fast failure or timeout.

    @Test
    fun testPrintConnectionFailsCleanlyOnBadHost() = runTest {
        val client = TcpRawEscPosPrinterClient(defaultTimeoutMillis = 100)

        val payload = byteArrayOf(0x1B, 0x40, 0x0A) // Init printer, line feed

        // Attempting to connect to an invalid host port combo
        val result = client.print(
            host = "192.0.2.254", // TEST-NET-1 block, usually blackholed or unreachable
            port = 9100,
            payload = payload,
            timeoutMillis = 100 // Very short timeout
        )

        assertTrue(result is PrintResult.Failure, "Expected Failure due to timeout or unreachable host")
    }
}
