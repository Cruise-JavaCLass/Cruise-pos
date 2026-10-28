package com.cruise.pos.shared.data.printer

import com.cruise.pos.shared.domain.printer.BluetoothConnectionState
import com.cruise.pos.shared.domain.printer.BluetoothDeviceModel
import com.cruise.pos.shared.domain.printer.BluetoothDiscoveryListener
import com.cruise.pos.shared.domain.printer.BluetoothPrinterManager
import com.cruise.pos.shared.domain.printer.BluetoothSocketConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Standard Bluetooth SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
 */
const val SPP_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB"

class BluetoothSppManager(
    private val socketFactory: (BluetoothDeviceModel) -> BluetoothSocketConnection
) : BluetoothPrinterManager {

    private val mutex = Mutex()
    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    private val _connectedDeviceFlow = MutableStateFlow<BluetoothDeviceModel?>(null)
    private val _connectionStateFlow = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    private var _activeSocket: BluetoothSocketConnection? = null
    private var currentDiscoveryListener: BluetoothDiscoveryListener? = null
    private var isDiscovering: Boolean = false

    override val connectionState: BluetoothConnectionState
        get() = _connectionStateFlow.value

    override val connectedDevice: BluetoothDeviceModel?
        get() = _connectedDeviceFlow.value

    override val pairedDevices: List<BluetoothDeviceModel>
        get() = _pairedDevices.value

    fun addPairedDevice(device: BluetoothDeviceModel) {
        _pairedDevices.update { current ->
            if (current.any { it.address == device.address }) current
            else current + device.copy(isBonded = true)
        }
    }

    override fun startDiscovery(listener: BluetoothDiscoveryListener) {
        currentDiscoveryListener = listener
        isDiscovering = true
        listener.onDiscoveryStarted()
    }

    override fun stopDiscovery() {
        if (isDiscovering) {
            isDiscovering = false
            currentDiscoveryListener?.onDiscoveryFinished()
            currentDiscoveryListener = null
        }
    }

    fun notifyDeviceFound(device: BluetoothDeviceModel) {
        if (isDiscovering) {
            currentDiscoveryListener?.onDeviceDiscovered(device)
        }
    }

    override suspend fun pairAndConnect(device: BluetoothDeviceModel): Result<BluetoothSocketConnection> =
        mutex.withLock {
            var pendingSocket: BluetoothSocketConnection? = null
            try {
                stopDiscovery()
                _connectionStateFlow.value = BluetoothConnectionState.CONNECTING

                // If currently connected to another device, close it first
                _activeSocket?.let {
                    if (it.isConnected) {
                        try { it.close() } catch (_: Exception) {}
                    }
                }
                _activeSocket = null
                _connectedDeviceFlow.value = null

                val socket = socketFactory(device)
                pendingSocket = socket
                val success = socket.connect()
                if (success) {
                    _activeSocket = socket
                    _connectedDeviceFlow.value = device.copy(isConnected = true, isBonded = true)
                    _connectionStateFlow.value = BluetoothConnectionState.CONNECTED
                    addPairedDevice(device)
                    Result.success(socket)
                } else {
                    try { socket.close() } catch (_: Exception) {}
                    _connectionStateFlow.value = BluetoothConnectionState.ERROR
                    Result.failure(IllegalStateException("Failed to establish RFCOMM SPP socket to ${device.address}"))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                try { pendingSocket?.close() } catch (_: Exception) {}
                _connectionStateFlow.value = BluetoothConnectionState.ERROR
                Result.failure(e)
            }
        }

    override suspend fun disconnect(): Unit {
        mutex.withLock {
            _connectionStateFlow.value = BluetoothConnectionState.DISCONNECTING
            try {
                _activeSocket?.close()
            } finally {
                _activeSocket = null
                _connectedDeviceFlow.value = null
                _connectionStateFlow.value = BluetoothConnectionState.DISCONNECTED
            }
        }
    }
}
