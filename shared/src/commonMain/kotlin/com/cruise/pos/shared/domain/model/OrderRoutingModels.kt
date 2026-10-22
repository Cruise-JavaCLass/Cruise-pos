package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PrepStationType {
    HOT_LINE,
    COLD_LINE,
    BAR,
    DESSERT,
    EXPEDITE
}

@Serializable
enum class DestinationType {
    GALLEY,
    BAR
}

@Serializable
enum class ConnectionType {
    TCP_RAW,
    BLE
}

@Serializable
data class PrinterEndpoint(
    val id: String,
    val name: String,
    val destination: DestinationType,
    val stationType: PrepStationType,
    val host: String,
    val port: Int = 9100,
    val connectionType: ConnectionType = ConnectionType.TCP_RAW,
    val macAddress: String? = null,
    val isBackup: Boolean = false
)

@Serializable
data class TicketModifier(
    val groupName: String,
    val optionName: String,
    val priceDelta: Double = 0.0
)

@Serializable
data class TicketLineItem(
    val itemId: String,
    val name: String,
    val categoryId: String,
    val quantity: Int = 1,
    val seatNumber: Int? = null,
    val specialInstructions: String? = null,
    val modifiers: List<TicketModifier> = emptyList(),
    val prepStation: PrepStationType = PrepStationType.HOT_LINE
)

@Serializable
data class OrderRoutingRequest(
    val orderId: String,
    val tableNumber: String? = null,
    val cabinNumber: String? = null,
    val terminalStationId: String,
    val serverName: String,
    val timestampMillis: Long,
    val items: List<TicketLineItem>
)

@Serializable
data class PreparedTicket(
    val ticketId: String,
    val orderId: String,
    val destination: DestinationType,
    val stationType: PrepStationType,
    val targetPrinterId: String,
    val tableNumber: String?,
    val cabinNumber: String?,
    val serverName: String,
    val timestampMillis: Long,
    val items: List<TicketLineItem>
)

@Serializable
data class RoutedOrderTickets(
    val orderId: String,
    val galleyTickets: List<PreparedTicket>,
    val barTickets: List<PreparedTicket>
) {
    val allTickets: List<PreparedTicket>
        get() = galleyTickets + barTickets
}
