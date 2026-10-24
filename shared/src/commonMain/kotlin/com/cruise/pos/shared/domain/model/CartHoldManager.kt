package com.cruise.pos.shared.domain.model

class CartHoldManager(
    private val maxHeldTabs: Int = 20,
    private val clock: () -> Long = { 0L }
) {
    private val heldTabs = mutableMapOf<String, CartHoldTab>()
    private var sequence = 0L

    fun getHeldTabs(): List<CartHoldTab> {
        return heldTabs.values.sortedByDescending { it.createdAtEpochMs }
    }

    fun holdOrder(
        guestIdentifier: String,
        items: List<CartItem>,
        note: String = "",
        timestampEpochMs: Long = clock(),
        tabId: String? = null
    ): CartHoldTab {
        require(items.isNotEmpty()) { "Cannot hold an empty cart" }
        require(guestIdentifier.isNotBlank()) { "Guest identifier is required to park an order" }
        check(heldTabs.size < maxHeldTabs) { "Maximum hold capacity reached ($maxHeldTabs tabs)" }

        val resolvedTabId = tabId ?: "TAB-$timestampEpochMs-${++sequence}"
        require(resolvedTabId !in heldTabs) { "Tab id already in use: $resolvedTabId" }

        val tab = CartHoldTab(
            tabId = resolvedTabId,
            guestIdentifier = guestIdentifier.trim(),
            items = items,
            createdAtEpochMs = timestampEpochMs,
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
