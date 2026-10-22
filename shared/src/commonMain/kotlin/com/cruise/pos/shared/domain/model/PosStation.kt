package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PosStation(
    val stationId: String,
    val stationName: String,
    val outletCategory: String, // BAR, RESTAURANT, SUNDECK, SOUVENIR
    val deckNumber: Int
) {
    companion object {
        val DEFAULT_STATIONS = listOf(
            PosStation("STATION_BAR_01", "Lido Deck Pool Bar", "BAR", 9),
            PosStation("STATION_REST_01", "Ocean View Main Dining", "RESTAURANT", 4),
            PosStation("STATION_SUNDECK", "Sundeck Sky Lounge", "BAR", 12),
            PosStation("STATION_SOUVENIR", "Promenade Boutique Souvenir", "SOUVENIR", 5)
        )
    }
}
