package com.example.cruise_pos.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupervisorAuthViewModel : ViewModel() {

    private val _state = MutableStateFlow(SupervisorAuthState())
    val state: StateFlow<SupervisorAuthState> = _state.asStateFlow()

    // Hardcoded valid credentials (demo only — in prod would call API)
    private val validCredentials = mapOf(
        "MGR-01" to "8888",
        "MGR-ADMIN" to "9999"
    )

    fun onManagerIdChange(id: String) {
        _state.value = _state.value.copy(
            managerId = id,
            errorMessage = null
        )
    }

    fun onPinDigitEntered(digit: String) {
        val current = _state.value.pinInput
        if (current.length >= 4) return
        val updated = current + digit
        _state.value = _state.value.copy(
            pinInput = updated,
            errorMessage = null
        )
        if (updated.length == 4) {
            verifyPin(updated)
        }
    }

    fun onPinBackspace() {
        val current = _state.value.pinInput
        if (current.isEmpty()) return
        _state.value = _state.value.copy(
            pinInput = current.dropLast(1),
            errorMessage = null
        )
    }

    fun onAuthMethodChange(method: AuthMethod) {
        _state.value = _state.value.copy(
            authMethod = method,
            errorMessage = null,
            pinInput = "",
            isAuthenticated = false
        )
    }

    fun onBiometricAuth() {
        // Simulate biometric success — in real app would use BiometricPrompt with verified manager identity
        val currentId = _state.value.managerId.trim()
        val verifiedManagerId = if (currentId.isNotBlank() && validCredentials.containsKey(currentId)) {
            currentId
        } else {
            "MGR-ADMIN"
        }

        _state.value = _state.value.copy(
            managerId = verifiedManagerId,
            isAuthenticated = true,
            errorMessage = null
        )
    }

    fun verifyPin(pin: String = _state.value.pinInput) {
        val managerId = _state.value.managerId.trim()
        val expectedPin = validCredentials[managerId]

        when {
            managerId.isBlank() -> {
                _state.value = _state.value.copy(
                    errorMessage = "Manager ID is required",
                    pinInput = ""
                )
            }
            expectedPin == null -> {
                _state.value = _state.value.copy(
                    errorMessage = "Unknown Manager ID",
                    pinInput = ""
                )
            }
            expectedPin != pin -> {
                _state.value = _state.value.copy(
                    errorMessage = "Incorrect PIN. Please try again.",
                    pinInput = ""
                )
            }
            else -> {
                _state.value = _state.value.copy(
                    isAuthenticated = true,
                    errorMessage = null
                )
            }
        }
    }

    fun reset() {
        _state.value = SupervisorAuthState()
    }
}
