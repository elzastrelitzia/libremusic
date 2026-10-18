package app.pulse.providers.feedback

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Feedback {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 10_000L
            }

            expectSuccess = false
        }
    }

    /** HTTP status code, or 0 on timeout/network error. Never throws. */
    suspend fun send(
        endpoint: String,
        name: String,
        description: String,
        version: String,
        device: String,
        deviceId: String,
    ): Int = try {
        client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(FeedbackPayload(name, description, version, device, deviceId))
        }.status.value
    } catch (_: Exception) {
        0
    }
}

@Serializable
private data class FeedbackPayload(
    val name: String,
    val description: String,
    val version: String,
    val device: String,
    val deviceId: String,
)
