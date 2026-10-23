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
        val tokens = extractCategoryTokens(categoryId)
        return if (tokens.any { it in BAR_KEYWORDS }) {
            DestinationType.BAR
        } else {
            DestinationType.GALLEY
        }
    }

    fun resolvePrepStation(categoryId: String, assignedStation: PrepStationType?): PrepStationType {
        if (assignedStation != null && assignedStation != PrepStationType.HOT_LINE) {
            return assignedStation
        }
        val tokens = extractCategoryTokens(categoryId)
        return when {
            tokens.any { it in BAR_KEYWORDS } -> PrepStationType.BAR
            tokens.any { it in COLD_LINE_KEYWORDS } -> PrepStationType.COLD_LINE
            tokens.any { it in DESSERT_KEYWORDS } -> PrepStationType.DESSERT
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
                ticketId = "${orderId}-${destination.name.lowercase()}-${stationType.name.lowercase()}",
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
        private val BAR_KEYWORDS = setOf(
            "bar", "cocktail", "cocktails", "wine", "wines",
            "beer", "beers", "beverage", "beverages",
            "drink", "drinks", "coffee", "tea"
        )

        private val COLD_LINE_KEYWORDS = setOf(
            "cold", "salad", "salads", "sushi", "raw", "appetizer", "appetizers"
        )

        private val DESSERT_KEYWORDS = setOf(
            "dessert", "desserts", "bakery", "pastry", "pastries", "ice_cream", "icecream"
        )

        private fun extractCategoryTokens(categoryId: String): Set<String> {
            val normalized = categoryId.trim().lowercase()
            val words = normalized.split(Regex("[\\s_/-]+")).filter { it.isNotBlank() }
            return (words + normalized).toSet()
        }

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
