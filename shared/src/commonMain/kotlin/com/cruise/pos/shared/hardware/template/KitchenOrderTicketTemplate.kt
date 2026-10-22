package com.cruise.pos.shared.hardware.template

import com.cruise.pos.shared.domain.model.DestinationType
import com.cruise.pos.shared.domain.model.PreparedTicket
import com.cruise.pos.shared.domain.model.TicketLineItem

object KitchenOrderTicketTemplate {

    // ESC/POS Command Constants
    private val ESC_INIT = byteArrayOf(0x1B, 0x40) // ESC @: Initialize printer
    private val TXT_NORMAL = byteArrayOf(0x1B, 0x21, 0x00) // ESC ! 0: Normal text
    private val TXT_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1: Bold on
    private val TXT_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0: Bold off
    private val TXT_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10) // ESC ! 16: Double height
    private val TXT_DOUBLE_SIZE = byteArrayOf(0x1B, 0x21, 0x30) // ESC ! 48: Double width + height
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0: Align left
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1: Align center
    private val FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x03) // GS V 65 3: Partial cut with feed
    private val LINE_SEPARATOR = "------------------------------------------\n"
    private val DOUBLE_SEPARATOR = "==========================================\n"

    fun renderTicketText(ticket: PreparedTicket): String {
        val sb = StringBuilder()
        val stationHeader = when (ticket.destination) {
            DestinationType.BAR -> "*** BAR ORDER TICKET ***"
            DestinationType.GALLEY -> "*** KITCHEN ORDER TICKET (${ticket.stationType.name}) ***"
        }

        sb.append(DOUBLE_SEPARATOR)
        sb.append("         $stationHeader\n")
        sb.append(DOUBLE_SEPARATOR)
        sb.append("Order #: ${ticket.orderId}\n")
        sb.append("Ticket : ${ticket.ticketId}\n")
        sb.append("Station: ${ticket.stationType.name}\n")
        sb.append("Server : ${ticket.serverName}\n")

        val locationParts = mutableListOf<String>()
        ticket.tableNumber?.let { locationParts.add("TABLE: $it") }
        ticket.cabinNumber?.let { locationParts.add("CABIN: $it") }
        val locationStr = if (locationParts.isNotEmpty()) locationParts.joinToString("  |  ") else "LOCATION: TO-GO / GENERAL"
        sb.append("$locationStr\n")
        sb.append("Time   : ${formatTimestamp(ticket.timestampMillis)}\n")
        sb.append(LINE_SEPARATOR)
        sb.append("QTY  ITEM DESCRIPTION                    SEAT\n")
        sb.append(LINE_SEPARATOR)

        ticket.items.forEach { item ->
            sb.append(formatLineItem(item))
        }

        sb.append(LINE_SEPARATOR)
        val totalQty = ticket.items.sumOf { it.quantity }
        sb.append("TOTAL ITEMS: $totalQty\n")
        sb.append(DOUBLE_SEPARATOR)
        sb.append("\n\n")
        return sb.toString()
    }

    private fun formatLineItem(item: TicketLineItem): String {
        val sb = StringBuilder()
        val seatStr = item.seatNumber?.let { "S:$it" } ?: ""
        val qtyStr = "${item.quantity}x".padEnd(4)
        val maxNameLen = 32
        val truncatedName = if (item.name.length > maxNameLen) {
            item.name.substring(0, maxNameLen - 1) + "…"
        } else {
            item.name
        }
        val paddedName = truncatedName.padEnd(maxNameLen)
        sb.append("$qtyStr $paddedName $seatStr\n".trimEnd() + "\n")

        item.modifiers.forEach { mod ->
            sb.append("   * ${mod.groupName}: ${mod.optionName}\n")
        }

        item.specialInstructions?.takeIf { it.isNotBlank() }?.let { instruction ->
            sb.append("   >> NOTE: [${instruction.trim()}]\n")
        }

        return sb.toString()
    }

    fun buildEscPosPayload(ticket: PreparedTicket): ByteArray {
        val output = mutableListOf<Byte>()

        fun append(bytes: ByteArray) {
            output.addAll(bytes.toList())
        }

        fun appendText(text: String) {
            output.addAll(text.encodeToByteArray().toList())
        }

        append(ESC_INIT)

        // Header
        append(ALIGN_CENTER)
        append(TXT_DOUBLE_SIZE)
        val headerTitle = when (ticket.destination) {
            DestinationType.BAR -> "BAR TICKET"
            DestinationType.GALLEY -> "GALLEY: ${ticket.stationType.name}"
        }
        appendText("$headerTitle\n")

        append(TXT_NORMAL)
        appendText(DOUBLE_SEPARATOR)

        // Location & Order Info
        append(ALIGN_LEFT)
        append(TXT_BOLD_ON)
        val tableInfo = ticket.tableNumber?.let { "TBL: $it" } ?: ""
        val cabinInfo = ticket.cabinNumber?.let { "CAB: $it" } ?: ""
        val locCombined = listOf(tableInfo, cabinInfo).filter { it.isNotBlank() }.joinToString(" | ")
        if (locCombined.isNotBlank()) {
            append(TXT_DOUBLE_HEIGHT)
            appendText("$locCombined\n")
            append(TXT_NORMAL)
            append(TXT_BOLD_ON)
        }
        appendText("ORDER: ${ticket.orderId}   SERVER: ${ticket.serverName}\n")
        append(TXT_BOLD_OFF)
        appendText("TIME : ${formatTimestamp(ticket.timestampMillis)}\n")
        appendText(LINE_SEPARATOR)

        // Items
        ticket.items.forEach { item ->
            append(TXT_BOLD_ON)
            val seatTag = item.seatNumber?.let { " [SEAT $it]" } ?: ""
            appendText("${item.quantity}x ${item.name}$seatTag\n")
            append(TXT_BOLD_OFF)

            item.modifiers.forEach { mod ->
                appendText("    + ${mod.groupName}: ${mod.optionName}\n")
            }
            item.specialInstructions?.takeIf { it.isNotBlank() }?.let { note ->
                append(TXT_BOLD_ON)
                appendText("    *** NOTE: $note ***\n")
                append(TXT_BOLD_OFF)
            }
        }

        appendText(LINE_SEPARATOR)
        append(ALIGN_CENTER)
        appendText("TOTAL QTY: ${ticket.items.sumOf { it.quantity }}\n")
        appendText(DOUBLE_SEPARATOR)

        // Feed and partial cut
        append(FEED_AND_CUT)

        return output.toByteArray()
    }

    private fun formatTimestamp(millis: Long): String {
        return millis.toString()
    }
}
