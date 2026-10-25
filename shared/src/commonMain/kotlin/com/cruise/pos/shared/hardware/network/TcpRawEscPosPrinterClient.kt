package com.cruise.pos.shared.hardware.network

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed class PrintResult {
    data class Success(val bytesWritten: Int, val host: String, val port: Int) : PrintResult()
    data class Failure(val host: String, val port: Int, val reason: String, val cause: Throwable? = null) : PrintResult()
}

interface EscPosNetworkPrinterClient {
    suspend fun print(host: String, port: Int, payload: ByteArray, timeoutMillis: Long = 3000L): PrintResult
}

class TcpRawEscPosPrinterClient(
    private val defaultTimeoutMillis: Long = 3000L
) : EscPosNetworkPrinterClient {

    override suspend fun print(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Long
    ): PrintResult = withContext(Dispatchers.Default) {
        val effectiveTimeout = if (timeoutMillis > 0) timeoutMillis else defaultTimeoutMillis
        var selector: SelectorManager? = null
        var socket: Socket? = null
        try {
            withTimeout(effectiveTimeout) {
                val sm = SelectorManager(Dispatchers.Default).also { selector = it }
                val sk = aSocket(sm).tcp().connect(host, port).also { socket = it }
                val writeChannel: ByteWriteChannel = sk.openWriteChannel(autoFlush = true)
                writeChannel.writeFully(payload)
                writeChannel.flush()
            }
            PrintResult.Success(
                bytesWritten = payload.size,
                host = host,
                port = port
            )
        } catch (e: TimeoutCancellationException) {
            PrintResult.Failure(
                host = host,
                port = port,
                reason = "TCP socket connection/write timed out after ${effectiveTimeout}ms",
                cause = e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            PrintResult.Failure(
                host = host,
                port = port,
                reason = t.message ?: "TCP ESC/POS connection error",
                cause = t
            )
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            try { selector?.close() } catch (_: Exception) {}
        }
    }
}
