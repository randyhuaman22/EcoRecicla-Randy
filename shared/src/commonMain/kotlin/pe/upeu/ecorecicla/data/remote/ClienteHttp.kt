package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.domain.repository.SesionSegura

data class ApiConfig(val baseUrl: String)

fun crearClienteHttp(
    motor: HttpClientEngine,
    config: ApiConfig,
    sesionSegura: SesionSegura,
    modoDepuracion: Boolean = false,
): HttpClient = HttpClient(motor) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
    defaultRequest {
        url(config.baseUrl)
        contentType(ContentType.Application.Json)
    }
    install(Auth) {
        bearer {
            loadTokens { sesionSegura.leer()?.let { BearerTokens(it.token, "") } }
            sendWithoutRequest { request -> !request.url.encodedPath.endsWith("/auth/login") }
        }
    }
    install(Logging) {
        level = if (modoDepuracion) LogLevel.HEADERS else LogLevel.NONE
        sanitizeHeader { it == HttpHeaders.Authorization }
    }
}

