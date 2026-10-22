package com.cruise.pos.shared

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.core.storage.KmpTokenStorage
import com.cruise.pos.shared.data.repository.AuthRepositoryImpl
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmpSharedAuthTest {

    @Test
    fun testTokenStorageLifecycle() {
        val mapSettings = MapSettings()
        val storage = KmpTokenStorage(mapSettings)

        assertNull(storage.getToken())

        storage.saveToken("TEST_JWT_123")
        storage.saveCurrentStation("STATION_BAR_01")

        assertEquals("TEST_JWT_123", storage.getToken())
        assertEquals("STATION_BAR_01", storage.getCurrentStation())

        storage.clearToken()
        assertNull(storage.getToken())
        assertEquals("STATION_BAR_01", storage.getCurrentStation())
    }

    @Test
    fun testAuthRepositoryPinLogin() = runTest {
        val storage = KmpTokenStorage(MapSettings())
        val repository = AuthRepositoryImpl(storage)

        // Invalid short pin
        val errorResult = repository.loginWithPin("12", "STATION_01")
        assertTrue(errorResult is ApiResult.Error)
        assertEquals(400, (errorResult as ApiResult.Error).code)

        // Valid pin
        val successResult = repository.loginWithPin("1234", "STATION_POOL_DECK")
        assertTrue(successResult is ApiResult.Success)

        val cashier = (successResult as ApiResult.Success).data
        assertEquals("CASHIER", cashier.role)
        assertEquals("STATION_POOL_DECK", cashier.stationAssigned)

        assertNotNull(repository.getSavedToken())
        assertEquals("STATION_POOL_DECK", repository.getSavedStation())

        repository.logout()
        assertNull(repository.getSavedToken())
    }
}
