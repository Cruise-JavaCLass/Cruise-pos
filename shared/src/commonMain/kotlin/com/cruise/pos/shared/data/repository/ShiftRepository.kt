package com.cruise.pos.shared.data.repository

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ShiftRepository {
    val currentShift: StateFlow<ShiftRecord?>
    suspend fun openShift(request: OpenShiftRequest): ApiResult<ShiftRecord>
    suspend fun closeShift(request: CloseShiftRequest): ApiResult<ShiftRecord>
    suspend fun getCurrentActiveShift(cashierId: Long): ApiResult<ShiftRecord?>
}

class ShiftRepositoryImpl(
    private val httpClient: HttpClient? = null,
    private val baseUrl: String = "https://cruise-pos-api.internal"
) : ShiftRepository {

    private val _currentShift = MutableStateFlow<ShiftRecord?>(null)
    override val currentShift: StateFlow<ShiftRecord?> = _currentShift.asStateFlow()

    override suspend fun openShift(request: OpenShiftRequest): ApiResult<ShiftRecord> {
        if (!CashCalculationUtils.isValidStartingFloat(request.startingFloat)) {
            return ApiResult.Error(code = 400, message = "Invalid starting float amount")
        }

        if (httpClient != null) {
            return try {
                val response = httpClient.post("$baseUrl/api/v1/shifts/open") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                if (response.status.isSuccess()) {
                    val shift = response.body<ShiftRecord>()
                    _currentShift.value = shift
                    ApiResult.Success(shift)
                } else {
                    ApiResult.Error(code = response.status.value, message = "Failed to open shift: ${response.status.description}")
                }
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Network error opening shift", throwable = e)
            }
        }

        // Local / Standalone offline fallback simulation
        val newShift = ShiftRecord(
            id = "shift-${request.stationId}-${request.cashierId}",
            cashierId = request.cashierId,
            cashierName = "Cashier #${request.cashierId}",
            stationId = request.stationId,
            status = ShiftStatus.OPEN,
            startingFloat = request.startingFloat,
            startingDenominations = request.denominations,
            openedAt = "2026-10-22T08:00:00Z"
        )
        _currentShift.value = newShift
        return ApiResult.Success(newShift)
    }

    override suspend fun closeShift(request: CloseShiftRequest): ApiResult<ShiftRecord> {
        if (httpClient != null) {
            return try {
                val response = httpClient.post("$baseUrl/api/v1/shifts/close") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                if (response.status.isSuccess()) {
                    val shift = response.body<ShiftRecord>()
                    _currentShift.value = null
                    ApiResult.Success(shift)
                } else {
                    ApiResult.Error(code = response.status.value, message = "Failed to close shift: ${response.status.description}")
                }
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Network error closing shift", throwable = e)
            }
        }

        val existing = _currentShift.value
        val expected = (existing?.startingFloat ?: 0.0) + 350.0 // Simulated cash sales
        val diff = CashCalculationUtils.calculateDiscrepancy(request.actualCountedCash, expected)

        val closed = ShiftRecord(
            id = request.shiftId,
            cashierId = existing?.cashierId ?: 1L,
            cashierName = existing?.cashierName ?: "Cashier",
            stationId = existing?.stationId ?: "STATION_1",
            status = ShiftStatus.CLOSED,
            startingFloat = existing?.startingFloat ?: 0.0,
            openedAt = existing?.openedAt ?: "2026-10-22T08:00:00Z",
            closedAt = "2026-10-22T17:00:00Z",
            expectedCash = expected,
            actualCountedCash = request.actualCountedCash,
            cashDifference = diff,
            closingDenominations = request.denominations,
            closingNote = request.closingNote
        )
        _currentShift.value = null
        return ApiResult.Success(closed)
    }

    override suspend fun getCurrentActiveShift(cashierId: Long): ApiResult<ShiftRecord?> {
        if (httpClient != null) {
            return try {
                val response = httpClient.get("$baseUrl/api/v1/shifts/active") {
                    parameter("cashierId", cashierId)
                }
                if (response.status == HttpStatusCode.NotFound) {
                    _currentShift.value = null
                    ApiResult.Success(null)
                } else if (response.status.isSuccess()) {
                    val shift = response.body<ShiftRecord>()
                    _currentShift.value = shift
                    ApiResult.Success(shift)
                } else {
                    ApiResult.Error(code = response.status.value, message = "Failed to fetch active shift")
                }
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Network error querying active shift", throwable = e)
            }
        }

        return ApiResult.Success(_currentShift.value)
    }
}
