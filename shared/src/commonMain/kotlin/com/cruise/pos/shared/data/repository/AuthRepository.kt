package com.cruise.pos.shared.data.repository

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.core.storage.TokenStorage
import com.cruise.pos.shared.domain.model.CashierUser

interface AuthRepository {
    suspend fun loginWithPin(pin: String, stationId: String): ApiResult<CashierUser>
    fun getSavedToken(): String?
    fun logout()
    fun getSavedStation(): String?
}

class AuthRepositoryImpl(
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override suspend fun loginWithPin(pin: String, stationId: String): ApiResult<CashierUser> {
        if (pin.length < 4) {
            return ApiResult.Error(code = 400, message = "PIN must be at least 4 digits")
        }

        // Demo/Simulated Auth handshake - in live calls KmpHttpClientFactory
        val fakeToken = "JWT_POS_TOKEN_${pin.hashCode()}_${stationId}"
        tokenStorage.saveToken(fakeToken)
        tokenStorage.saveCurrentStation(stationId)

        val cashier = CashierUser(
            id = 1L,
            username = "cashier_$pin",
            fullName = "Onboard Duty Cashier",
            role = "CASHIER",
            stationAssigned = stationId
        )

        return ApiResult.Success(cashier)
    }

    override fun getSavedToken(): String? = tokenStorage.getToken()

    override fun logout() {
        tokenStorage.clearToken()
    }

    override fun getSavedStation(): String? = tokenStorage.getCurrentStation()
}
