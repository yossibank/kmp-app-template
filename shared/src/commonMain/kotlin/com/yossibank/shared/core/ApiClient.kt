package com.yossibank.shared.core

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

internal class ApiClient(
    private val baseUrl: String,
    engine: HttpClientEngine? = null,
) {
    @Volatile
    private var closed = false

    private val client: HttpClient =
        if (engine == null) {
            HttpClient { installDefaults() }
        } else {
            HttpClient(engine) { installDefaults() }
        }

    suspend inline fun <reified T> get(
        path: String,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = request(path, configure) { it.body<T>() }

    fun close() {
        closed = true
        client.close()
    }

    suspend fun <T> request(
        path: String,
        configure: HttpRequestBuilder.() -> Unit,
        decode: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        if (closed) {
            return ApiResult.Err(ApiFailure.Closed)
        }

        val response = try {
            client.get("$baseUrl$path") { configure() }
        } catch (e: CancellationException) {
            return closedOrRethrow(e)
        } catch (e: Exception) {
            return ApiResult.Err(if (e.isTimeout()) ApiFailure.Timeout else ApiFailure.Offline)
        }

        if (!response.status.isSuccess()) {
            return ApiResult.Err(ApiFailure.Server(response.status.value))
        }

        return try {
            ApiResult.Ok(decode(response))
        } catch (e: CancellationException) {
            closedOrRethrow(e)
        } catch (e: Exception) {
            ApiResult.Err(ApiFailure.Unreadable)
        }
    }

    private suspend fun closedOrRethrow(e: CancellationException): ApiResult.Err {
        currentCoroutineContext().ensureActive()

        if (closed) {
            return ApiResult.Err(ApiFailure.Closed)
        }

        throw e
    }

    companion object {
        const val CONNECT_TIMEOUT_MILLIS: Long = 10_000

        const val REQUEST_TIMEOUT_MILLIS: Long = 15_000
    }
}

private fun Throwable.isTimeout(): Boolean = this is HttpRequestTimeoutException ||
    this is ConnectTimeoutException ||
    this is SocketTimeoutException

private fun HttpClientConfig<*>.installDefaults() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = ApiClient.REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = ApiClient.CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = ApiClient.CONNECT_TIMEOUT_MILLIS
    }
}
