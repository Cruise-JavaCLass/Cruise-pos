package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.DestinationType
import com.cruise.pos.shared.domain.model.OrderRoutingRequest
import com.cruise.pos.shared.domain.model.PrepStationType
import com.cruise.pos.shared.domain.model.PrinterEndpoint
import com.cruise.pos.shared.domain.model.TicketLineItem
import com.cruise.pos.shared.domain.model.TicketModifier
import com.cruise.pos.shared.domain.service.PrinterSpoolerQueue
import com.cruise.pos.shared.domain.service.SpoolJobStatus
import com.cruise.pos.shared.domain.service.TicketRoutingEngine
import com.cruise.pos.shared.hardware.network.EscPosNetworkPrinterClient
import com.cruise.pos.shared.hardware.network.PrintResult
import com.cruise.pos.shared.hardware.template.KitchenOrderTicketTemplate
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TicketRoutingAndPrinterSpoolerTest {

    private val engine = TicketRoutingEngine()

    private class MockPrinterClient(
        private val failingHosts: Set<String> = emptySet(),
        var throwTimeoutOn: Set<String> = emptySet()
    ) : EscPosNetworkPrinterClient {
        val printedPayloads = mutableListOf<Triple<String, Int, ByteArray>>()

        override suspend fun print(
            host: String,
            port: Int,
            payload: ByteArray,
            timeoutMillis: Long
        ): PrintResult {
            if (throwTimeoutOn.contains(host)) {
                return PrintResult.Failure(host, port, "SocketTimeoutException: Connection timed out after ${timeoutMillis}ms")
            }
            if (failingHosts.contains(host)) {
                return PrintResult.Failure(host, port, "ConnectException: Connection refused (printer offline)")
            }
            printedPayloads.add(Triple(host, port, payload))
            return PrintResult.Success(payload.size, host, port)
        }
    }

    @Test
    fun testCategoryDestinationPartitioning() {
        assertEquals(DestinationType.BAR, engine.determineDestination("bar_beverages"))
        assertEquals(DestinationType.BAR, engine.determineDestination("cocktails"))
        assertEquals(DestinationType.BAR, engine.determineDestination("wines"))
        assertEquals(DestinationType.BAR, engine.determineDestination("coffee_tea"))

        assertEquals(DestinationType.GALLEY, engine.determineDestination("hot_entrees"))
        assertEquals(DestinationType.GALLEY, engine.determineDestination("cold_appetizers"))
        assertEquals(DestinationType.GALLEY, engine.determineDestination("pastry_desserts"))
    }

    @Test
    fun testPrepStationResolution() {
        assertEquals(PrepStationType.HOT_LINE, engine.resolvePrepStation("entrees", PrepStationType.HOT_LINE))
        assertEquals(PrepStationType.COLD_LINE, engine.resolvePrepStation("salad_raw", null))
        assertEquals(PrepStationType.DESSERT, engine.resolvePrepStation("dessert_bakery", null))
        assertEquals(PrepStationType.BAR, engine.resolvePrepStation("cocktail", null))
    }

    @Test
    fun testOrderRoutingPartitionBetweenHotColdAndBar() {
        val request = OrderRoutingRequest(
            orderId = "ORD-9021",
            tableNumber = "T-14",
            cabinNumber = "CAB-8022",
            terminalStationId = "POS-MAIN-DECK",
            serverName = "Marco Rossi",
            timestampMillis = 1718900000000L,
            items = listOf(
                TicketLineItem(
                    itemId = "item-steak",
                    name = "Wagyu Ribeye Steak",
                    categoryId = "hot_entrees",
                    quantity = 2,
                    seatNumber = 1,
                    specialInstructions = "Medium rare, sauce on side",
                    modifiers = listOf(TicketModifier("Doneness", "Medium Rare")),
                    prepStation = PrepStationType.HOT_LINE
                ),
                TicketLineItem(
                    itemId = "item-salad",
                    name = "Caesar Salad",
                    categoryId = "cold_appetizers",
                    quantity = 1,
                    seatNumber = 2,
                    modifiers = listOf(TicketModifier("Dressing", "Extra Caesar")),
                    prepStation = PrepStationType.COLD_LINE
                ),
                TicketLineItem(
                    itemId = "item-martini",
                    name = "Dry Gin Martini",
                    categoryId = "bar_cocktails",
                    quantity = 2,
                    seatNumber = 1,
                    modifiers = listOf(TicketModifier("Garnish", "3 Olives")),
                    prepStation = PrepStationType.BAR
                )
            )
        )

        val routed = engine.routeOrder(request)
        assertEquals("ORD-9021", routed.orderId)
        assertEquals(2, routed.galleyTickets.size)
        assertEquals(1, routed.barTickets.size)

        val hotTicket = routed.galleyTickets.firstOrNull { it.stationType == PrepStationType.HOT_LINE }
        assertNotNull(hotTicket)
        assertEquals(1, hotTicket.items.size)
        assertEquals("Wagyu Ribeye Steak", hotTicket.items.first().name)
        assertEquals(1, hotTicket.items.first().seatNumber)

        val coldTicket = routed.galleyTickets.firstOrNull { it.stationType == PrepStationType.COLD_LINE }
        assertNotNull(coldTicket)
        assertEquals(1, coldTicket.items.size)
        assertEquals("Caesar Salad", coldTicket.items.first().name)

        val barTicket = routed.barTickets.first()
        assertEquals(PrepStationType.BAR, barTicket.stationType)
        assertEquals(1, barTicket.items.size)
        assertEquals("Dry Gin Martini", barTicket.items.first().name)
    }

    @Test
    fun testEmptyOrderThrowsException() {
        val request = OrderRoutingRequest(
            orderId = "ORD-EMPTY",
            terminalStationId = "POS-01",
            serverName = "John",
            timestampMillis = 1718900000000L,
            items = emptyList()
        )

        assertFailsWith<IllegalArgumentException> {
            engine.routeOrder(request)
        }
    }

    @Test
    fun testTicketEscPosPayloadFormatting() {
        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-101",
                tableNumber = "T-05",
                terminalStationId = "POS-01",
                serverName = "Sarah",
                timestampMillis = 1718900000000L,
                items = listOf(
                    TicketLineItem(
                        itemId = "it-1",
                        name = "Grilled Salmon",
                        categoryId = "hot_entrees",
                        quantity = 1,
                        seatNumber = 3,
                        specialInstructions = "Gluten free breadcrumbs"
                    )
                )
            )
        ).galleyTickets.first()

        val text = KitchenOrderTicketTemplate.renderTicketText(ticket)
        assertTrue(text.contains("ORD-101"))
        assertTrue(text.contains("TABLE: T-05"))
        assertTrue(text.contains("Grilled Salmon"))
        assertTrue(text.contains("S:3"))
        assertTrue(text.contains("Gluten free breadcrumbs"))

        val rawEscPos = KitchenOrderTicketTemplate.buildEscPosPayload(ticket)
        assertTrue(rawEscPos.isNotEmpty())
        assertEquals(0x1B.toByte(), rawEscPos[0]) // ESC init
        assertEquals(0x40.toByte(), rawEscPos[1])
    }

    @Test
    fun testPrinterSpoolerPrimarySuccess() = runTest {
        val client = MockPrinterClient()
        val spooler = PrinterSpoolerQueue(client, MapSettings())

        val printer = PrinterEndpoint("p1", "Hot Galley", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.10")
        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-1",
                terminalStationId = "T1",
                serverName = "Alice",
                timestampMillis = 1000L,
                items = listOf(TicketLineItem("1", "Burger", "entree"))
            )
        ).galleyTickets.first()

        val job = spooler.enqueueAndDispatch(ticket, printer, currentTimeMillis = 1000L)
        assertEquals(SpoolJobStatus.SENT_TO_PRIMARY, job.status)
        assertEquals(1, job.attempts)
        assertEquals(1, client.printedPayloads.size)
        assertTrue(spooler.alerts.value.isEmpty())
    }

    @Test
    fun testPrinterSpoolerFailoverToBackupOnSocketTimeout() = runTest {
        val primary = PrinterEndpoint("p-hot", "Hot Galley Net", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.50")
        val backup = PrinterEndpoint("p-local", "Local Receipt Backup", DestinationType.GALLEY, PrepStationType.EXPEDITE, "127.0.0.1", isBackup = true)

        val client = MockPrinterClient(throwTimeoutOn = setOf("192.168.1.50"))
        val spooler = PrinterSpoolerQueue(client, MapSettings())

        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-2",
                terminalStationId = "T1",
                serverName = "Bob",
                timestampMillis = 2000L,
                items = listOf(TicketLineItem("1", "Ribs", "entree"))
            )
        ).galleyTickets.first()

        val job = spooler.enqueueAndDispatch(ticket, primary, backup, currentTimeMillis = 2000L)
        assertEquals(SpoolJobStatus.FAILED_OVER_TO_BACKUP, job.status)
        assertEquals(2, job.attempts)
        assertEquals(1, client.printedPayloads.size)
        assertEquals("127.0.0.1", client.printedPayloads.first().first)

        // Crew alert should have been raised for primary failure
        assertEquals(1, spooler.alerts.value.size)
        assertTrue(spooler.alerts.value.first().message.contains("OFFLINE"))
    }

    @Test
    fun testPrinterSpoolerBothPrintersOfflineTriggersQueueAlertAndRetry() = runTest {
        val primary = PrinterEndpoint("p-hot", "Hot Net", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.50")
        val backup = PrinterEndpoint("p-backup", "Backup Net", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.51")

        val client = MockPrinterClient(failingHosts = setOf("192.168.1.50", "192.168.1.51"))
        val spooler = PrinterSpoolerQueue(client, MapSettings())

        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-3",
                terminalStationId = "T1",
                serverName = "Charlie",
                timestampMillis = 3000L,
                items = listOf(TicketLineItem("1", "Soup", "entree"))
            )
        ).galleyTickets.first()

        val job = spooler.enqueueAndDispatch(ticket, primary, backup, currentTimeMillis = 3000L)
        assertEquals(SpoolJobStatus.ALERT_TRIGGERED, job.status)
        assertEquals(0, client.printedPayloads.size)
        assertEquals(1, spooler.alerts.value.size)

        // Simulate network restored
        val restoredClient = MockPrinterClient()
        val retrySpooler = PrinterSpoolerQueue(restoredClient, MapSettings())
        val retryJob = retrySpooler.enqueueAndDispatch(ticket, primary, backup, currentTimeMillis = 3500L)
        assertEquals(SpoolJobStatus.SENT_TO_PRIMARY, retryJob.status)
    }
}
