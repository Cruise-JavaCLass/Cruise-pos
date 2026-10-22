package com.cruise.pos.shared.domain.service

import com.cruise.pos.shared.domain.model.DestinationType
import com.cruise.pos.shared.domain.model.OrderRoutingRequest
import com.cruise.pos.shared.domain.model.PrepStationType
import com.cruise.pos.shared.domain.model.PreparedTicket
import com.cruise.pos.shared.domain.model.PrinterEndpoint
import com.cruise.pos.shared.domain.model.RoutedOrderTickets
import com.cruise.pos.shared.domain.model.TicketLineItem

class TicketRoutingEngine(
    private val printerRegistry: List<PrinterEndpoint> = defaultShipPrinters()
) {

    fun determineDestination(categoryId: String): DestinationType {
        val normalized = categoryId.trim().lowercase()
        return when {
            normalized.contains("bar") ||
            normalized.contains("cocktail") ||
            normalized.contains("wine") ||
            normalized.contains("beer") ||
            normalized.contains("beverage") ||
            normalized.contains("drink") ||
            normalized.contains("coffee") ||
            normalized.contains("tea") -> DestinationType.BAR

            else -> DestinationType.GALLEY
        }
    }

    fun resolvePrepStation(categoryId: String, assignedStation: PrepStationType?): PrepStationType {
        if (assignedStation != null && assignedStation != PrepStationType.HOT_LINE) {
            return assignedStation
        }
        val normalized = categoryId.trim().lowercase()
        return when {
            normalized.contains("bar") ||
            normalized.contains("drink") ||
            normalized.contains("beverage") ||
            normalized.contains("wine") ||
            normalized.contains("cocktail") -> PrepStationType.BAR

            normalized.contains("cold") ||
            normalized.contains("salad") ||
            normalized.contains("sushi") ||
            normalized.contains("raw") ||
            normalized.contains("appetizer") -> PrepStationType.COLD_LINE

            normalized.contains("dessert") ||
            normalized.contains("bakery") ||
            normalized.contains("pastry") ||
            normalized.contains("ice_cream") -> PrepStationType.DESSERT

            else -> PrepStationType.HOT_LINE
        }
    }

    fun routeOrder(request: OrderRoutingRequest): RoutedOrderTickets {
        require(request.items.isNotEmpty()) { "Cannot route order with empty items" }

        val partitionedByDestination = request.items.groupBy { item ->
            determineDestination(item.categoryId)
        }

        val galleyItems = partitionedByDestination[DestinationType.GALLEY].orEmpty()
        val barItems = partitionedByDestination[DestinationType.BAR].orEmpty()

        val galleyTickets = partitionIntoTickets(
            orderId = request.orderId,
            destination = DestinationType.GALLEY,
            items = galleyItems,
            request = request
        )

        val barTickets = partitionIntoTickets(
            orderId = request.orderId,
            destination = DestinationType.BAR,
            items = barItems,
            request = request
        )

        return RoutedOrderTickets(
            orderId = request.orderId,
            galleyTickets = galleyTickets,
            barTickets = barTickets
        )
    }

    private fun partitionIntoTickets(
        orderId: String,
        destination: DestinationType,
        items: List<TicketLineItem>,
        request: OrderRoutingRequest
    ): List<PreparedTicket> {
        if (items.isEmpty()) return emptyList()

        val stationGroups = items.groupBy { item ->
            resolvePrepStation(item.categoryId, item.prepStation)
        }

        return stationGroups.map { (stationType, groupedItems) ->
            val targetPrinter = selectPrinterFor(destination, stationType)
            PreparedTicket(
                ticketId = "${orderId}-${stationType.name.lowercase()}",
                orderId = orderId,
                destination = destination,
                stationType = stationType,
                targetPrinterId = targetPrinter.id,
                tableNumber = request.tableNumber,
                cabinNumber = request.cabinNumber,
                serverName = request.serverName,
                timestampMillis = request.timestampMillis,
                items = groupedItems
            )
        }
    }

    fun selectPrinterFor(destination: DestinationType, stationType: PrepStationType): PrinterEndpoint {
        val matched = printerRegistry.firstOrNull {
            !it.isBackup && it.destination == destination && it.stationType == stationType
        } ?: printerRegistry.firstOrNull {
            !it.isBackup && it.destination == destination
        } ?: printerRegistry.firstOrNull {
            it.isBackup && it.destination == destination
        } ?: printerRegistry.first()

        return matched
    }

    companion object {
        fun defaultShipPrinters(): List<PrinterEndpoint> = listOf(
            PrinterEndpoint(
                id = "printer-galley-hot",
                name = "Galley Hot Line Network Printer",
                destination = DestinationType.GALLEY,
                stationType = PrepStationType.HOT_LINE,
                host = "192.168.10.101",
                port = 9100
            ),
            PrinterEndpoint(
                id = "printer-galley-cold",
                name = "Galley Cold Prep Network Printer",
                destination = DestinationType.GALLEY,
                stationType = PrepStationType.COLD_LINE,
                host = "192.168.10.102",
                port = 9100
            ),
            PrinterEndpoint(
                id = "printer-galley-dessert",
                name = "Bakery & Dessert Prep Printer",
                destination = DestinationType.GALLEY,
                stationType = PrepStationType.DESSERT,
                host = "192.168.10.103",
                port = 9100
            ),
            PrinterEndpoint(
                id = "printer-bar-main",
                name = "Main Deck Bar Receipt/KOT Printer",
                destination = DestinationType.BAR,
                stationType = PrepStationType.BAR,
                host = "192.168.10.201",
                port = 9100
            ),
            PrinterEndpoint(
                id = "printer-local-backup",
                name = "Local Terminal Receipt Printer (Failover)",
                destination = DestinationType.GALLEY,
                stationType = PrepStationType.EXPEDITE,
                host = "127.0.0.1",
                port = 9100,
                isBackup = true
            )
        )
    }
}
