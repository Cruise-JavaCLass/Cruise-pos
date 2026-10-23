package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.ConnectionType
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TicketRoutingAndPrinterSpoolerTest {

    private val engine = TicketRoutingEngine()

    private class MockPrinterClient(
        var failingHosts: Set<String> = emptySet(),
        var throwTimeoutOn: Set<String> = emptySet(),
        var throwCancellationOn: Set<String> = emptySet()
    ) : EscPosNetworkPrinterClient {
        val printedPayloads = mutableListOf<Triple<String, Int, ByteArray>>()

        override suspend fun print(
            host: String,
            port: Int,
            payload: ByteArray,
            timeoutMillis: Long
        ): PrintResult {
            if (throwCancellationOn.contains(host)) {
                throw CancellationException("Simulated coroutine cancellation during print to $host")
            }
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
    fun testCategoryWholeTokenMatchingRegression() {
        // "tea" in "steak_entrees" must NOT route to BAR
        assertEquals(DestinationType.GALLEY, engine.determineDestination("steak_entrees"))
        assertEquals(PrepStationType.HOT_LINE, engine.resolvePrepStation("steak_entrees", null))

        // "bar" in "barbecue_grill" must NOT route to BAR
        assertEquals(DestinationType.GALLEY, engine.determineDestination("barbecue_grill"))
        assertEquals(PrepStationType.HOT_LINE, engine.resolvePrepStation("barbecue_grill", null))

        // "raw" in "strawberry_desserts" must NOT route to COLD_LINE
        assertEquals(DestinationType.GALLEY, engine.determineDestination("strawberry_desserts"))
        assertEquals(PrepStationType.DESSERT, engine.resolvePrepStation("strawberry_desserts", null))

        // "coffee_tea" must consistently route to BAR for both destination and station
        assertEquals(DestinationType.BAR, engine.determineDestination("coffee_tea"))
        assertEquals(PrepStationType.BAR, engine.resolvePrepStation("coffee_tea", null))
    }

    @Test
    fun testExplicitBarPrepStationRoutesToBarDestination() {
        val request = OrderRoutingRequest(
            orderId = "ORD-EXPLICIT-BAR",
            terminalStationId = "T1",
            serverName = "Alice",
            timestampMillis = 1000L,
            items = listOf(
                TicketLineItem(
                    itemId = "item-special-drink",
                    name = "Signature Smoked Old Fashioned",
                    categoryId = "specialty_menu",
                    quantity = 1,
                    prepStation = PrepStationType.BAR
                )
            )
        )
        val routed = engine.routeOrder(request)
        assertEquals(0, routed.galleyTickets.size)
        assertEquals(1, routed.barTickets.size)
        val barTicket = routed.barTickets.first()
        assertEquals(DestinationType.BAR, barTicket.destination)
        assertEquals(PrepStationType.BAR, barTicket.stationType)
    }

    @Test
    fun testExplicitDessertStationOnBarCategoryRoutesToGalleyDessertPrinter() {
        val request = OrderRoutingRequest(
            orderId = "ORD-DESSERT-AFFOGATO",
            terminalStationId = "T1",
            serverName = "Bob",
            timestampMillis = 1000L,
            items = listOf(
                TicketLineItem(
                    itemId = "item-affogato",
                    name = "Espresso Affogato",
                    categoryId = "coffee_desserts",
                    quantity = 1,
                    prepStation = PrepStationType.DESSERT
                )
            )
        )
        val routed = engine.routeOrder(request)
        assertEquals(1, routed.galleyTickets.size)
        assertEquals(0, routed.barTickets.size)
        val galleyTicket = routed.galleyTickets.first()
        assertEquals(DestinationType.GALLEY, galleyTicket.destination)
        assertEquals(PrepStationType.DESSERT, galleyTicket.stationType)
        assertEquals("printer-galley-dessert", galleyTicket.targetPrinterId)
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
    fun testTicketIdIncludesDestination() {
        val routed = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-DEST",
                terminalStationId = "T1",
                serverName = "Alice",
                timestampMillis = 1000L,
                items = listOf(
                    TicketLineItem("1", "Steak", "hot_entrees", prepStation = PrepStationType.HOT_LINE),
                    TicketLineItem("2", "Beer", "bar_beverages", prepStation = PrepStationType.BAR)
                )
            )
        )
        val galleyTicket = routed.galleyTickets.first()
        val barTicket = routed.barTickets.first()
        assertEquals("ORD-DEST-galley-hot_line", galleyTicket.ticketId)
        assertEquals("ORD-DEST-bar-bar", barTicket.ticketId)
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
                items = listOf(TicketLineItem("1", "Burger", "hot_entrees"))
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
                items = listOf(TicketLineItem("1", "Ribs", "hot_entrees"))
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
        val sharedSettings = MapSettings()
        val spooler = PrinterSpoolerQueue(client, sharedSettings)

        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-3",
                terminalStationId = "T1",
                serverName = "Charlie",
                timestampMillis = 3000L,
                items = listOf(TicketLineItem("1", "Soup", "hot_entrees"))
            )
        ).galleyTickets.first()

        val job = spooler.enqueueAndDispatch(ticket, primary, backup, currentTimeMillis = 3000L)
        assertEquals(SpoolJobStatus.ALERT_TRIGGERED, job.status)
        assertEquals(2, job.attempts) // Primary and backup both attempted
        assertEquals(0, client.printedPayloads.size)
        assertEquals(1, spooler.alerts.value.size)

        // 1. Test retry with backup printer: primary remains failing, backup is restored
        client.failingHosts = setOf("192.168.1.50") // 1.51 is healthy now
        val retryBackupJobs = spooler.retryPendingJobs(currentTimeMillis = 3500L)
        assertEquals(1, retryBackupJobs.size)
        val backupSuccessJob = retryBackupJobs.first()
        assertEquals(SpoolJobStatus.FAILED_OVER_TO_BACKUP, backupSuccessJob.status)
        assertEquals(4, backupSuccessJob.attempts) // 2 calls initially + 2 calls during retry
        assertEquals(1, client.printedPayloads.size)
        assertEquals("192.168.1.51", client.printedPayloads.first().first)

        // 2. Test restore from shared settings and retry primary success on another ticket
        val ticket2 = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-4",
                terminalStationId = "T1",
                serverName = "Charlie",
                timestampMillis = 4000L,
                items = listOf(TicketLineItem("2", "Salad", "cold_appetizers"))
            )
        ).galleyTickets.first()

        client.failingHosts = setOf("192.168.1.50", "192.168.1.51")
        val job2 = spooler.enqueueAndDispatch(ticket2, primary, backup, currentTimeMillis = 4000L)
        assertEquals(SpoolJobStatus.ALERT_TRIGGERED, job2.status)
        assertEquals(2, job2.attempts)

        client.failingHosts = emptySet()
        val restoredSpooler = PrinterSpoolerQueue(client, sharedSettings)
        val retriedRestored = restoredSpooler.retryPendingJobs(currentTimeMillis = 4500L)
        assertEquals(1, retriedRestored.size)
        assertEquals(SpoolJobStatus.SENT_TO_PRIMARY, retriedRestored.first().status)
        assertEquals(3, retriedRestored.first().attempts) // 2 calls initially + 1 primary call
    }

    @Test
    fun testExhaustingMaxAttemptsEndsInFailedWithAlert() = runTest {
        val primary = PrinterEndpoint("p-hot", "Hot Net", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.50")
        val client = MockPrinterClient(failingHosts = setOf("192.168.1.50"))
        val spooler = PrinterSpoolerQueue(client, MapSettings())

        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-FAIL",
                terminalStationId = "T1",
                serverName = "Charlie",
                timestampMillis = 5000L,
                items = listOf(TicketLineItem("1", "Fish", "hot_entrees"))
            )
        ).galleyTickets.first()

        val job = spooler.enqueueAndDispatch(ticket, primary, currentTimeMillis = 5000L)
        assertEquals(SpoolJobStatus.ALERT_TRIGGERED, job.status)
        assertEquals(1, job.attempts)

        // Assert initial alert count and affected job id before retries
        assertEquals(1, spooler.alerts.value.size)
        val initialAlert = spooler.alerts.value.first()
        assertEquals(job.jobId, initialAlert.affectedJobId)

        // Retry 1: attempts becomes 2, maxAttempts is 3 -> still ALERT_TRIGGERED
        val retry1 = spooler.retryPendingJobs(currentTimeMillis = 5100L)
        assertEquals(1, retry1.size)
        assertEquals(2, retry1.first().attempts)
        assertEquals(SpoolJobStatus.ALERT_TRIGGERED, retry1.first().status)
        assertEquals(1, spooler.alerts.value.size)

        // Retry 2: attempts becomes 3 >= 3 -> FAILED with offline alert
        val retry2 = spooler.retryPendingJobs(currentTimeMillis = 5200L)
        assertEquals(1, retry2.size)
        assertEquals(3, retry2.first().attempts)
        assertEquals(SpoolJobStatus.FAILED, retry2.first().status)

        // Verify the alert count and message for permanently failed job
        assertEquals(1, spooler.alerts.value.size)
        val terminalAlert = spooler.alerts.value.first()
        assertEquals(job.jobId, terminalAlert.affectedJobId)
        assertTrue(terminalAlert.message.contains("ABANDONED/FAILED"))
    }

    @Test
    fun testRetryCancellationLeavesFinishedJobsAndResetsInFlightForNextCall() = runTest {
        val printerSuccess = PrinterEndpoint("p-1", "Printer 1", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.100")
        val printerCancel = PrinterEndpoint("p-2", "Printer 2", DestinationType.GALLEY, PrepStationType.HOT_LINE, "cancel-host")

        // Initially both fail to get queued into ALERT_TRIGGERED
        val client = MockPrinterClient(failingHosts = setOf("192.168.1.100", "cancel-host"))
        val spooler = PrinterSpoolerQueue(client, MapSettings())

        val ticket1 = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-CANCEL-1",
                terminalStationId = "T1",
                serverName = "Alice",
                timestampMillis = 1000L,
                items = listOf(TicketLineItem("1", "Item1", "hot_entrees"))
            )
        ).galleyTickets.first()

        val ticket2 = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-CANCEL-2",
                terminalStationId = "T1",
                serverName = "Bob",
                timestampMillis = 1000L,
                items = listOf(TicketLineItem("2", "Item2", "hot_entrees"))
            )
        ).galleyTickets.first()

        spooler.enqueueAndDispatch(ticket1, printerSuccess, currentTimeMillis = 1000L)
        spooler.enqueueAndDispatch(ticket2, printerCancel, currentTimeMillis = 1000L)

        // Now set printer 1 healthy, but printer 2 will throw CancellationException during print
        client.failingHosts = emptySet()
        client.throwCancellationOn = setOf("cancel-host")

        assertFailsWith<CancellationException> {
            spooler.retryPendingJobs(currentTimeMillis = 2000L)
        }

        // Job 1 must have completed SENT_TO_PRIMARY, Job 2 must be reset to ALERT_TRIGGERED (not stuck IN_FLIGHT)
        val snapshotAfterCancel = spooler.getQueueSnapshot()
        val job1Status = snapshotAfterCancel.first { it.ticket.orderId == "ORD-CANCEL-1" }.status
        val job2Status = snapshotAfterCancel.first { it.ticket.orderId == "ORD-CANCEL-2" }.status
        assertEquals(SpoolJobStatus.SENT_TO_PRIMARY, job1Status)
        assertEquals(SpoolJobStatus.ALERT_TRIGGERED, job2Status)

        // Subsequent retry succeeds for Job 2 once cancellation condition is removed
        client.throwCancellationOn = emptySet()
        val retryFollowUp = spooler.retryPendingJobs(currentTimeMillis = 3000L)
        assertEquals(1, retryFollowUp.size)
        assertEquals("ORD-CANCEL-2", retryFollowUp.first().ticket.orderId)
        assertEquals(SpoolJobStatus.SENT_TO_PRIMARY, retryFollowUp.first().status)
    }

    @Test
    fun testDistinctJobsForTicketsSharingTimestamp() = runTest {
        val primary = PrinterEndpoint("p-hot", "Hot Net", DestinationType.GALLEY, PrepStationType.HOT_LINE, "192.168.1.50")
        val client = MockPrinterClient()
        val spooler = PrinterSpoolerQueue(client, MapSettings())

        val ticket1 = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-SAME-TIME-1",
                terminalStationId = "T1",
                serverName = "Alice",
                timestampMillis = 1000L,
                items = listOf(TicketLineItem("1", "Burger", "hot_entrees"))
            )
        ).galleyTickets.first()

        val ticket2 = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-SAME-TIME-2",
                terminalStationId = "T1",
                serverName = "Bob",
                timestampMillis = 1000L,
                items = listOf(TicketLineItem("2", "Fries", "hot_entrees"))
            )
        ).galleyTickets.first()

        val job1 = spooler.enqueueAndDispatch(ticket1, primary, currentTimeMillis = 1000L)
        val job2 = spooler.enqueueAndDispatch(ticket2, primary, currentTimeMillis = 1000L)

        assertTrue(job1.jobId.isNotEmpty())
        assertTrue(job2.jobId.isNotEmpty())
        assertTrue(job1.jobId != job2.jobId, "Job IDs must be distinct despite identical timestamps")
    }

    @Test
    fun testRejectBleEndpointsBeforeEnqueue() = runTest {
        val client = MockPrinterClient()
        val spooler = PrinterSpoolerQueue(client, MapSettings())
        val blePrinter = PrinterEndpoint(
            id = "ble-1",
            name = "Mobile BLE",
            destination = DestinationType.GALLEY,
            stationType = PrepStationType.HOT_LINE,
            host = "",
            connectionType = ConnectionType.BLE,
            macAddress = "AA:BB:CC:DD:EE:FF"
        )
        val ticket = engine.routeOrder(
            OrderRoutingRequest(
                orderId = "ORD-BLE",
                terminalStationId = "T1",
                serverName = "Alice",
                timestampMillis = 1000L,
                items = listOf(TicketLineItem("1", "Burger", "hot_entrees"))
            )
        ).galleyTickets.first()

        assertFailsWith<IllegalArgumentException> {
            spooler.enqueueAndDispatch(ticket, blePrinter)
        }
    }
}
