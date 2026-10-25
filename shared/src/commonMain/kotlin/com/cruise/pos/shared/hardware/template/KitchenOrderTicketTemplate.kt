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
    private const val LINE_SEPARATOR = "------------------------------------------\n"
    private const val DOUBLE_SEPARATOR = "==========================================\n"

    var defaultTimestampFormatter: (Long) -> String = { millis ->
        formatUtcIsoTimestamp(millis)
    }

    fun renderTicketText(
        ticket: PreparedTicket,
        timestampFormatter: ((Long) -> String)? = null
    ): String {
        val formatTime = timestampFormatter ?: defaultTimestampFormatter
        val sb = StringBuilder()
        val stationHeader = when (ticket.destination) {
            DestinationType.BAR -> "*** BAR ORDER TICKET ***"
            DestinationType.GALLEY -> "*** KITCHEN ORDER TICKET (${sanitizeSingleLine(ticket.stationType.name)}) ***"
        }

        sb.append(DOUBLE_SEPARATOR)
        sb.append("         $stationHeader\n")
        sb.append(DOUBLE_SEPARATOR)
        sb.append("Order #: ${sanitizeSingleLine(ticket.orderId)}\n")
        sb.append("Ticket : ${sanitizeSingleLine(ticket.ticketId)}\n")
        sb.append("Station: ${sanitizeSingleLine(ticket.stationType.name)}\n")
        sb.append("Server : ${sanitizeSingleLine(ticket.serverName)}\n")

        val locationParts = mutableListOf<String>()
        ticket.tableNumber?.let { locationParts.add("TABLE: ${sanitizeSingleLine(it)}") }
        ticket.cabinNumber?.let { locationParts.add("CABIN: ${sanitizeSingleLine(it)}") }
        val locationStr = if (locationParts.isNotEmpty()) locationParts.joinToString("  |  ") else "LOCATION: TO-GO / GENERAL"
        sb.append("$locationStr\n")
        sb.append("Time   : ${formatTime(ticket.timestampMillis)}\n")
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
        val cleanName = sanitizeSingleLine(item.name)
        val maxNameLen = 32
        val truncatedName = if (cleanName.length > maxNameLen) {
            cleanName.substring(0, maxNameLen - 1) + "."
        } else {
            cleanName
        }
        val paddedName = truncatedName.padEnd(maxNameLen)
        sb.append("$qtyStr $paddedName $seatStr\n".trimEnd() + "\n")

        item.modifiers.forEach { mod ->
            val cleanGroup = sanitizeSingleLine(mod.groupName)
            val cleanOption = sanitizeSingleLine(mod.optionName)
            sb.append("   * $cleanGroup: $cleanOption\n")
        }

        item.specialInstructions?.takeIf { it.isNotBlank() }?.let { instruction ->
            val cleanInstruction = sanitizeSingleLine(instruction)
            sb.append("   >> NOTE: [${cleanInstruction.trim()}]\n")
        }

        return sb.toString()
    }

    fun buildEscPosPayload(
        ticket: PreparedTicket,
        timestampFormatter: ((Long) -> String)? = null
    ): ByteArray {
        val formatTime = timestampFormatter ?: defaultTimestampFormatter
        val output = mutableListOf<Byte>()

        fun append(bytes: ByteArray) {
            output.addAll(bytes.toList())
        }

        fun appendText(text: String) {
            val sanitized = sanitizeMultiline(text)
            output.addAll(sanitized.encodeToByteArray().toList())
        }

        append(ESC_INIT)

        // Header
        append(ALIGN_CENTER)
        append(TXT_DOUBLE_SIZE)
        val headerTitle = when (ticket.destination) {
            DestinationType.BAR -> "BAR TICKET"
            DestinationType.GALLEY -> "GALLEY: ${sanitizeSingleLine(ticket.stationType.name)}"
        }
        appendText("$headerTitle\n")

        append(TXT_NORMAL)
        appendText(DOUBLE_SEPARATOR)

        // Location & Order Info
        append(ALIGN_LEFT)
        append(TXT_BOLD_ON)
        val tableInfo = ticket.tableNumber?.let { "TBL: ${sanitizeSingleLine(it)}" } ?: ""
        val cabinInfo = ticket.cabinNumber?.let { "CAB: ${sanitizeSingleLine(it)}" } ?: ""
        val locCombined = listOf(tableInfo, cabinInfo).filter { it.isNotBlank() }.joinToString(" | ")
        if (locCombined.isNotBlank()) {
            append(TXT_DOUBLE_HEIGHT)
            appendText("$locCombined\n")
            append(TXT_NORMAL)
            append(TXT_BOLD_ON)
        }
        val cleanOrder = sanitizeSingleLine(ticket.orderId)
        val cleanServer = sanitizeSingleLine(ticket.serverName)
        appendText("ORDER: $cleanOrder   SERVER: $cleanServer\n")
        append(TXT_BOLD_OFF)
        appendText("TIME : ${formatTime(ticket.timestampMillis)}\n")
        appendText(LINE_SEPARATOR)

        // Items
        ticket.items.forEach { item ->
            append(TXT_BOLD_ON)
            val seatTag = item.seatNumber?.let { " [SEAT $it]" } ?: ""
            val cleanItemName = sanitizeSingleLine(item.name)
            appendText("${item.quantity}x $cleanItemName$seatTag\n")
            append(TXT_BOLD_OFF)

            item.modifiers.forEach { mod ->
                val cleanGroup = sanitizeSingleLine(mod.groupName)
                val cleanOption = sanitizeSingleLine(mod.optionName)
                appendText("    + $cleanGroup: $cleanOption\n")
            }
            item.specialInstructions?.takeIf { it.isNotBlank() }?.let { note ->
                val cleanNote = sanitizeSingleLine(note)
                append(TXT_BOLD_ON)
                appendText("    *** NOTE: $cleanNote ***\n")
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

    fun sanitizeSingleLine(input: String): String = sanitizeText(input, allowNewlines = false)

    fun sanitizeMultiline(input: String): String = sanitizeText(input, allowNewlines = true)

    private fun sanitizeText(input: String, allowNewlines: Boolean): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch == '\n' -> if (allowNewlines) sb.append('\n') else sb.append(' ')
                ch == '\r' -> if (allowNewlines) Unit else sb.append(' ')
                ch == '\t' -> sb.append("  ")
                ch.code < 0x20 || ch.code == 0x7F -> {
                    // Drop C0 control characters and DEL
                }
                else -> sb.append(transliterateToAscii(ch))
            }
        }
        return sb.toString()
    }

    private fun transliterateToAscii(ch: Char): Char = when (ch) {
        'á', 'à', 'â', 'ä', 'ã', 'å', 'ā' -> 'a'
        'Á', 'À', 'Â', 'Ä', 'Ã', 'Å', 'Ā' -> 'A'
        'é', 'è', 'ê', 'ë', 'ē' -> 'e'
        'É', 'È', 'Ê', 'Ë', 'Ē' -> 'E'
        'í', 'ì', 'î', 'ï', 'ī' -> 'i'
        'Í', 'Ì', 'Î', 'Ï', 'Ī' -> 'I'
        'ó', 'ò', 'ô', 'ö', 'õ', 'ō' -> 'o'
        'Ó', 'Ò', 'Ô', 'Ö', 'Õ', 'Ō' -> 'O'
        'ú', 'ù', 'û', 'ü', 'ū' -> 'u'
        'Ú', 'Ù', 'Û', 'Ü', 'Ū' -> 'U'
        'ñ' -> 'n'; 'Ñ' -> 'N'
        'ç' -> 'c'; 'Ç' -> 'C'
        'ß' -> 's'
        '—', '–' -> '-'
        '“', '”', '«', '»' -> '"'
        '‘', '’' -> '\''
        '…' -> '.'
        else -> if (ch.code in 0x20..0x7E) ch else '?'
    }

    fun formatUtcIsoTimestamp(millis: Long): String {
        if (millis <= 0L) return "N/A"
        val totalSeconds = millis / 1000
        val daySeconds = ((totalSeconds % 86400) + 86400) % 86400
        val hours = daySeconds / 3600
        val minutes = (daySeconds % 3600) / 60
        val seconds = daySeconds % 60

        val epochDays = (totalSeconds / 86400) + if (totalSeconds < 0 && (totalSeconds % 86400 != 0L)) -1 else 0
        val (year, month, day) = civilDateFromEpochDays(epochDays)

        fun pad2(n: Long) = if (n < 10) "0$n" else n.toString()
        fun pad4(n: Long) = when {
            n < 10 -> "000$n"
            n < 100 -> "00$n"
            n < 1000 -> "0$n"
            else -> n.toString()
        }

        return "${pad4(year)}-${pad2(month.toLong())}-${pad2(day.toLong())} ${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)} UTC"
    }

    private fun civilDateFromEpochDays(days: Long): Triple<Long, Int, Int> {
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + if (mp < 10) 3 else -9
        val year = y + if (m <= 2) 1 else 0
        return Triple(year, m.toInt(), d.toInt())
    }
}
