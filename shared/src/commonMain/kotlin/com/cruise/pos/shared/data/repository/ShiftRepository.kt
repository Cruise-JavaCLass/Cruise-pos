package com.cruise.pos.shared.data.repository

import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface ShiftRepository {
    val currentShift: StateFlow<ShiftRecord?>
    suspend fun openShift(request: OpenShiftRequest): ApiResult<ShiftRecord>
    suspend fun closeShift(request: CloseShiftRequest): ApiResult<ShiftRecord>
    suspend fun getCurrentActiveShift(cashierId: Long): ApiResult<ShiftRecord?>
    suspend fun recordCashDrop(request: CashDropRequest): ApiResult<CashDropRecord>
    fun setCurrentShift(shift: ShiftRecord?)
}

class ShiftRepositoryImpl(
    private val httpClient: HttpClient? = null,
    private val baseUrl: String = "https://cruise-pos-api.internal",
    private val clock: Clock = Clock.System
) : ShiftRepository {

    private val _currentShift = MutableStateFlow<ShiftRecord?>(null)
    override val currentShift: StateFlow<ShiftRecord?> = _currentShift.asStateFlow()

    override fun setCurrentShift(shift: ShiftRecord?) {
        _currentShift.value = shift
    }

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
            } catch (e: CancellationException) {
                throw e
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
            openedAt = clock.now().toString()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Network error closing shift", throwable = e)
            }
        }

        val existing = _currentShift.value
        val expected = (existing?.startingFloat ?: 0.0) + 350.0 - (existing?.totalCashDrops ?: 0.0) // Simulated cash sales minus drops
        val diff = CashCalculationUtils.calculateDiscrepancy(request.actualCountedCash, expected)

        val closed = ShiftRecord(
            id = request.shiftId,
            cashierId = existing?.cashierId ?: 1L,
            cashierName = existing?.cashierName ?: "Cashier",
            stationId = existing?.stationId ?: "STATION_1",
            status = ShiftStatus.CLOSED,
            startingFloat = existing?.startingFloat ?: 0.0,
            openedAt = existing?.openedAt ?: clock.now().toString(),
            closedAt = clock.now().toString(),
            totalCashDrops = existing?.totalCashDrops ?: 0.0,
            cashDropHistory = existing?.cashDropHistory ?: emptyList(),
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Network error querying active shift", throwable = e)
            }
        }

        return ApiResult.Success(_currentShift.value)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun recordCashDrop(request: CashDropRequest): ApiResult<CashDropRecord> {
        val shift = _currentShift.value
            ?: return ApiResult.Error(code = 404, message = "No active shift found")

        if (shift.id != request.shiftId) {
            return ApiResult.Error(code = 400, message = "Shift ID does not match active shift")
        }

        if (shift.status != ShiftStatus.OPEN) {
            return ApiResult.Error(code = 400, message = "Cannot perform cash drop on closed shift")
        }

        if (!request.amount.isFinite() || request.amount <= 0.0) {
            return ApiResult.Error(code = 400, message = "Cash drop amount must be greater than zero")
        }

        if (request.witnessId == request.cashierId) {
            return ApiResult.Error(code = 403, message = "Witness ID cannot match cashier ID")
        }

        if (request.witnessPin.isBlank() || request.witnessPin.length < 4) {
            return ApiResult.Error(code = 403, message = "Valid witness PIN authorization is required")
        }

        if (httpClient != null) {
            return try {
                val response = httpClient.post("$baseUrl/api/v1/pos/shifts/${request.shiftId}/cash-drop") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                if (response.status.isSuccess()) {
                    val drop = response.body<CashDropRecord>()
                    if (drop.shiftId != shift.id || _currentShift.value?.id != shift.id) {
                        return ApiResult.Error(code = 400, message = "Shift changed or mismatched drop record")
                    }
                    val updated = updateLocalShiftWithDrop(drop)
                    if (!updated) {
                        return ApiResult.Error(code = 400, message = "Shift changed or mismatched drop record")
                    }
                    ApiResult.Success(drop)
                } else {
                    ApiResult.Error(code = response.status.value, message = "Failed to record cash drop: ${response.status.description}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Network error recording cash drop", throwable = e)
            }
        }

        // Local simulation / Offline mode
        val nowIso = clock.now().toString()
        val dropRecord = CashDropRecord(
            id = "drop-${shift.id}-${shift.cashDropHistory.size + 1}",
            shiftId = shift.id,
            amount = request.amount,
            cashierId = request.cashierId,
            witnessId = request.witnessId,
            receiptNumber = "SAFE-DROP-${shift.id}-${(1000..9999).random()}",
            safeBalanceAfter = kotlin.math.round((10000.0 + shift.totalCashDrops + request.amount) * 100.0) / 100.0,
            denominations = request.denominations,
            droppedAt = nowIso,
            notes = request.notes
        )

        val updated = updateLocalShiftWithDrop(dropRecord)
        if (!updated) {
            return ApiResult.Error(code = 400, message = "Shift changed or mismatched drop record")
        }
        return ApiResult.Success(dropRecord)
    }

    private fun updateLocalShiftWithDrop(drop: CashDropRecord): Boolean {
        var applied = false
        _currentShift.update { current ->
            applied = current != null && current.id == drop.shiftId
            if (current != null && applied) {
                current.copy(
                    totalCashDrops = kotlin.math.round((current.totalCashDrops + drop.amount) * 100.0) / 100.0,
                    cashDropHistory = current.cashDropHistory + drop
                )
            } else {
                current
            }
        }
        return applied
    }
}
