package com.cruise.pos.shared.domain.model

class CartHoldManager(
    private val maxHeldTabs: Int = 20,
    private val clock: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds }
) {
    private val heldTabs = mutableMapOf<String, CartHoldTab>()
    private var sequenceCounter = 0L

    fun getHeldTabs(): List<CartHoldTab> {
        return heldTabs.values.sortedByDescending { it.createdAtEpochMs }
    }

    fun holdOrder(
        guestIdentifier: String,
        items: List<CartItem>,
        note: String = "",
        timestampEpochMs: Long? = null,
        tabId: String? = null
    ): CartHoldTab {
        require(items.isNotEmpty()) { "Cannot hold an empty cart" }
        require(guestIdentifier.isNotBlank()) { "Guest identifier is required to park an order" }
        check(heldTabs.size < maxHeldTabs) { "Maximum hold capacity reached ($maxHeldTabs tabs)" }

        val actualTimestamp = timestampEpochMs ?: clock()
        val actualTabId = tabId ?: run {
            sequenceCounter++
            "TAB-${actualTimestamp.toString().takeLast(6)}-$sequenceCounter"
        }
        require(!heldTabs.containsKey(actualTabId)) { "Tab with id $actualTabId already exists" }

        val tab = CartHoldTab(
            tabId = actualTabId,
            guestIdentifier = guestIdentifier.trim(),
            items = items,
            createdAtEpochMs = actualTimestamp,
            note = note.trim()
        )
        heldTabs[tab.tabId] = tab
        return tab
    }

    fun recallOrder(tabId: String): CartHoldTab? {
        return heldTabs.remove(tabId)
    }

    fun discardOrder(tabId: String): Boolean {
        return heldTabs.remove(tabId) != null
    }

    fun clearAll() {
        heldTabs.clear()
    }
}
