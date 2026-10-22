package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.core.security.KmpSessionManager
import com.cruise.pos.shared.core.storage.KmpTokenStorage
import com.cruise.pos.shared.data.repository.AuthRepositoryImpl
import com.cruise.pos.shared.domain.model.PosStation
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashierPinValidationAndSessionTest {

    @Test
    fun testPinValidationStateMachine() = runTest {
        val settings = MapSettings()
        val storage = KmpTokenStorage(settings)
        val sessionManager = KmpSessionManager(settings)
        val repository = AuthRepositoryImpl(storage, sessionManager)

        assertFalse(sessionManager.isSessionActive())

        // Test 1: PIN too short (less than 4 digits)
        val shortResult = repository.loginWithPin("123", "STATION_BAR_01")
        assertTrue(shortResult is ApiResult.Error)
        assertEquals("PIN must be at least 4 digits", (shortResult as ApiResult.Error).message)
        assertFalse(sessionManager.isSessionActive())

        // Test 2: Valid 4-digit PIN
        val successResult = repository.loginWithPin("5566", "STATION_BAR_01")
        assertTrue(successResult is ApiResult.Success)

        val cashier = (successResult as ApiResult.Success).data
        assertEquals("CASHIER", cashier.role)
        assertEquals("STATION_BAR_01", cashier.stationAssigned)
        assertNotNull(repository.getSavedToken())

        // Verify sessionManager recognizes the session
        assertTrue(sessionManager.isSessionActive())
        assertEquals(repository.getSavedToken(), sessionManager.getJwtToken())
        assertEquals(cashier.id, sessionManager.getCashierId())
        assertEquals(cashier.username, sessionManager.getCashierUsername())
        assertEquals(cashier.role, sessionManager.getCashierRole())
        assertEquals("STATION_BAR_01", sessionManager.getAssignedStationId())

        // Logout clears session
        repository.logout()
        assertFalse(sessionManager.isSessionActive())
    }

    @Test
    fun testSessionManagerStationPersistence() {
        val settings = MapSettings()
        val sessionManager = KmpSessionManager(settings)

        assertFalse(sessionManager.isSessionActive())

        sessionManager.saveSession(
            token = "SAMPLE_JWT_TOKEN",
            cashierId = 101L,
            username = "cashier_mike",
            role = "HEAD_BARTENDER",
            stationId = "STATION_SUNDECK"
        )

        assertTrue(sessionManager.isSessionActive())
        assertEquals("SAMPLE_JWT_TOKEN", sessionManager.getJwtToken())
        assertEquals(101L, sessionManager.getCashierId())
        assertEquals("cashier_mike", sessionManager.getCashierUsername())
        assertEquals("HEAD_BARTENDER", sessionManager.getCashierRole())
        assertEquals("STATION_SUNDECK", sessionManager.getAssignedStationId())

        // Switch station
        sessionManager.saveAssignedStationId("STATION_REST_01")
        assertEquals("STATION_REST_01", sessionManager.getAssignedStationId())

        // Clear session
        sessionManager.clearSession()
        assertFalse(sessionManager.isSessionActive())
        assertNull(sessionManager.getJwtToken())
        // Station binding persists across logins
        assertEquals("STATION_REST_01", sessionManager.getAssignedStationId())
    }

    @Test
    fun testDefaultPosStationsData() {
        val stations = PosStation.DEFAULT_STATIONS
        assertEquals(4, stations.size)
        assertTrue(stations.any { it.stationId == "STATION_BAR_01" && it.deckNumber == 9 })
        assertTrue(stations.any { it.outletCategory == "RESTAURANT" })
    }
}
