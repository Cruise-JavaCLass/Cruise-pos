package com.cruise.pos.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CashierUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: String,
    val stationAssigned: String? = null
)

@Serializable
data class LoginResponse(
    val token: String,
    val cashier: CashierUser
)
