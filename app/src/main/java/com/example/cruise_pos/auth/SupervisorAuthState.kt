package com.example.cruise_pos.auth

data class SupervisorAuthState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
    val pinInput: String = "",
    val managerId: String = "",
    val authMethod: AuthMethod = AuthMethod.PIN
)

enum class AuthMethod { PIN, BIOMETRIC }
