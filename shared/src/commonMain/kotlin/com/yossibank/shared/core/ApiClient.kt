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
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal class ApiClient(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    suspend inline fun <reified T> get(
        path: String,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = send(
        path,
        {
            method = HttpMethod.Get
            configure()
        },
    ) { it.body<T>() }

    suspend inline fun <reified B : Any, reified T> post(
        path: String,
        body: B,
    ): ApiResult<T> = send(
        path,
        {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
        },
    ) { it.body<T>() }

    suspend fun <T> send(
        path: String,
        configure: HttpRequestBuilder.() -> Unit,
        decode: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val response = try {
            client.request("$baseUrl$path") { configure() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RequestFailed) {
            return ApiResult.Err(e.failure)
        } catch (e: Exception) {
            return ApiResult.Err(if (e.isTimeout()) ApiFailure.Timeout else ApiFailure.Offline)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            return ApiResult.Err(ApiFailure.Unauthorized)
        }

        if (!response.status.isSuccess()) {
            return ApiResult.Err(ApiFailure.Server(response.status.value))
        }

        return try {
            ApiResult.Ok(decode(response))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Err(ApiFailure.Unreadable)
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MILLIS: Long = 10_000

        const val REQUEST_TIMEOUT_MILLIS: Long = 15_000
    }
}

internal class RequestFailed(
    val failure: ApiFailure,
) : Exception()

internal fun httpClient(
    engine: HttpClientEngine?,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val block: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = ApiClient.REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = ApiClient.CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = ApiClient.CONNECT_TIMEOUT_MILLIS
        }
        configure()
    }

    return if (engine == null) HttpClient(block) else HttpClient(engine, block)
}

private fun Throwable.isTimeout(): Boolean = this is HttpRequestTimeoutException ||
    this is ConnectTimeoutException ||
    this is SocketTimeoutException
