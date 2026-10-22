package com.cruise.pos.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KmpHttpClientFactory {

    fun create(
        baseUrl: String = "http://localhost:8080",
        tokenProvider: () -> String? = { null }
    ): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("[KMP-Ktor] $message")
                    }
                }
                level = LogLevel.INFO
            }

            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
                tokenProvider()?.let { token ->
                    if (token.isNotBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
            }
        }
    }
}
