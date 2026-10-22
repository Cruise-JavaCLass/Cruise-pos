package com.cruise.pos.shared.domain.printer

data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val isBonded: Boolean = false,
    val isConnected: Boolean = false,
    val rssi: Int = 0
)

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

interface BluetoothDiscoveryListener {
    fun onDeviceDiscovered(device: BluetoothDeviceModel)
    fun onDiscoveryStarted()
    fun onDiscoveryFinished()
    fun onError(errorCode: Int, message: String)
}

interface BluetoothSocketConnection {
    val isConnected: Boolean
    val remoteAddress: String
    suspend fun connect(): Boolean
    suspend fun write(bytes: ByteArray): Boolean
    suspend fun read(buffer: ByteArray): Int
    suspend fun close()
}

interface BluetoothPrinterManager {
    val connectionState: BluetoothConnectionState
    val connectedDevice: BluetoothDeviceModel?
    val pairedDevices: List<BluetoothDeviceModel>

    fun startDiscovery(listener: BluetoothDiscoveryListener)
    fun stopDiscovery()
    suspend fun pairAndConnect(device: BluetoothDeviceModel): Result<BluetoothSocketConnection>
    suspend fun disconnect()
}
