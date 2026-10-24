package com.cruise.pos.shared.data.printer

import com.cruise.pos.shared.domain.printer.BluetoothConnectionState
import com.cruise.pos.shared.domain.printer.BluetoothDeviceModel
import com.cruise.pos.shared.domain.printer.BluetoothDiscoveryListener
import com.cruise.pos.shared.domain.printer.BluetoothPrinterManager
import com.cruise.pos.shared.domain.printer.BluetoothSocketConnection
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
    private val _pairedDevices = mutableListOf<BluetoothDeviceModel>()
    private var _connectedDevice: BluetoothDeviceModel? = null
    private var _activeSocket: BluetoothSocketConnection? = null
    private var _connectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
    private var currentDiscoveryListener: BluetoothDiscoveryListener? = null
    private var isDiscovering: Boolean = false

    override val connectionState: BluetoothConnectionState
        get() = _connectionState

    override val connectedDevice: BluetoothDeviceModel?
        get() = _connectedDevice

    override val pairedDevices: List<BluetoothDeviceModel>
        get() = _pairedDevices.toList()

    fun addPairedDevice(device: BluetoothDeviceModel) {
        if (_pairedDevices.none { it.address == device.address }) {
            _pairedDevices.add(device.copy(isBonded = true))
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
            try {
                stopDiscovery()
                _connectionState = BluetoothConnectionState.CONNECTING

                // If currently connected to another device, close it first
                _activeSocket?.let {
                    if (it.isConnected) {
                        it.close()
                    }
                }

                val socket = socketFactory(device)
                val success = socket.connect()
                if (success) {
                    _activeSocket = socket
                    _connectedDevice = device.copy(isConnected = true, isBonded = true)
                    _connectionState = BluetoothConnectionState.CONNECTED
                    addPairedDevice(device)
                    Result.success(socket)
                } else {
                    _connectionState = BluetoothConnectionState.ERROR
                    Result.failure(IllegalStateException("Failed to establish RFCOMM SPP socket to ${device.address}"))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _connectionState = BluetoothConnectionState.ERROR
                Result.failure(e)
            }
        }

    override suspend fun disconnect(): Unit {
        mutex.withLock {
            _connectionState = BluetoothConnectionState.DISCONNECTING
            try {
                _activeSocket?.close()
            } finally {
                _activeSocket = null
                _connectedDevice = null
                _connectionState = BluetoothConnectionState.DISCONNECTED
            }
        }
    }
}
