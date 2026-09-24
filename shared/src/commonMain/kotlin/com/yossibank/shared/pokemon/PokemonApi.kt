package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonDetail
import com.yossibank.shared.generated.model.PokemonSummary
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
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import com.yossibank.shared.generated.model.PaginatedPokemonSummaryList as ListResponse

internal sealed interface FetchOutcome<out T> {
    data class Ok<T>(
        val value: T,
    ) : FetchOutcome<T>

    data class Err(
        val reason: PokemonFailure,
    ) : FetchOutcome<Nothing>
}

internal inline fun <T, R> FetchOutcome<T>.map(transform: (T) -> R): FetchOutcome<R> = when (this) {
    is FetchOutcome.Ok -> FetchOutcome.Ok(transform(value))
    is FetchOutcome.Err -> this
}

internal data class PokemonPage(
    val pokemon: List<PokemonSummary>,
    val hasMore: Boolean,
    val total: Int,
)

internal class PokemonApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
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

    suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): FetchOutcome<PokemonPage> = fetch<ListResponse>("$baseUrl/api/v2/pokemon/") {
        parameter("limit", limit)
        parameter("offset", offset)
    }.map { PokemonPage(pokemon = it.results, hasMore = it.next != null, total = it.count) }

    suspend fun fetchDetail(id: Int): FetchOutcome<PokemonDetail> = fetch("$baseUrl/api/v2/pokemon/$id/")

    fun close() {
        closed = true
        client.close()
    }

    private suspend inline fun <reified T> fetch(
        url: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
    ): FetchOutcome<T> {
        if (closed) {
            return FetchOutcome.Err(PokemonFailure.Closed)
        }

        val response = try {
            client.get(url) { configure() }
        } catch (e: CancellationException) {
            return closedOrRethrow(e)
        } catch (e: Exception) {
            return FetchOutcome.Err(if (e.isTimeout()) PokemonFailure.Timeout else PokemonFailure.Offline)
        }

        if (!response.status.isSuccess()) {
            return FetchOutcome.Err(PokemonFailure.Server(response.status.value))
        }

        return try {
            FetchOutcome.Ok(response.body<T>())
        } catch (e: CancellationException) {
            closedOrRethrow(e)
        } catch (e: Exception) {
            FetchOutcome.Err(PokemonFailure.Unexpected)
        }
    }

    private suspend fun closedOrRethrow(e: CancellationException): FetchOutcome.Err {
        currentCoroutineContext().ensureActive()

        if (closed) {
            return FetchOutcome.Err(PokemonFailure.Closed)
        }

        throw e
    }

    companion object {
        const val PAGE_SIZE: Int = 20

        const val CONNECT_TIMEOUT_MILLIS: Long = 10_000

        const val REQUEST_TIMEOUT_MILLIS: Long = 15_000

        private const val DEFAULT_BASE_URL = "https://pokeapi.co"
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
        requestTimeoutMillis = PokemonApi.REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = PokemonApi.CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = PokemonApi.CONNECT_TIMEOUT_MILLIS
    }
}
