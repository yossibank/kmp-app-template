package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonDetail
import com.yossibank.shared.generated.model.PokemonSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import com.yossibank.shared.generated.model.PaginatedPokemonSummaryList as ListResponse

sealed interface PokemonListFailure {
    val canRetry: Boolean

    data object Offline : PokemonListFailure {
        override val canRetry = true
    }

    data class Server(
        val statusCode: Int,
    ) : PokemonListFailure {
        override val canRetry = statusCode == 429 || statusCode >= 500
    }

    data object Unexpected : PokemonListFailure {
        override val canRetry = false
    }
}

sealed interface PokemonListResult {
    data class Loaded(
        val pokemon: List<PokemonEntry>,
        val hasMore: Boolean,
    ) : PokemonListResult {
        val incompleteCount: Int = pokemon.count { !it.hasDetail }
    }

    data class Failed(
        val pokemon: List<PokemonEntry>,
        val hasMore: Boolean,
        val failure: PokemonListFailure,
    ) : PokemonListResult {
        val incompleteCount: Int = pokemon.count { !it.hasDetail }
    }
}

internal sealed interface FetchOutcome<out T> {
    data class Ok<T>(
        val value: T,
    ) : FetchOutcome<T>

    data class Err(
        val reason: PokemonListFailure,
    ) : FetchOutcome<Nothing>
}

internal inline fun <T, R> FetchOutcome<T>.map(transform: (T) -> R): FetchOutcome<R> = when (this) {
    is FetchOutcome.Ok -> FetchOutcome.Ok(transform(value))
    is FetchOutcome.Err -> this
}

internal data class PokemonPage(
    val pokemon: List<PokemonSummary>,
    val hasMore: Boolean,
)

class PokemonApi internal constructor(
    private val baseUrl: String,
    private val client: HttpClient,
    private val ownsClient: Boolean = false,
) {
    constructor() : this(DEFAULT_BASE_URL, defaultClient(), ownsClient = true)

    internal suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): FetchOutcome<PokemonPage> = fetch<ListResponse>("$baseUrl/api/v2/pokemon/") {
        parameter("limit", limit)
        parameter("offset", offset)
    }.map { PokemonPage(pokemon = it.results, hasMore = it.next != null) }

    internal suspend fun fetchDetail(id: Int): FetchOutcome<PokemonDetail> = fetch("$baseUrl/api/v2/pokemon/$id/")

    fun close() {
        if (ownsClient) {
            client.close()
        }
    }

    private suspend inline fun <reified T> fetch(
        url: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
    ): FetchOutcome<T> {
        val response = try {
            client.get(url) { configure() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchOutcome.Err(PokemonListFailure.Offline)
        }

        if (!response.status.isSuccess()) {
            return FetchOutcome.Err(PokemonListFailure.Server(response.status.value))
        }

        return try {
            FetchOutcome.Ok(response.body<T>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchOutcome.Err(PokemonListFailure.Unexpected)
        }
    }

    companion object {
        const val PAGE_SIZE: Int = 20

        const val CONNECT_TIMEOUT_MILLIS: Long = 10_000

        const val REQUEST_TIMEOUT_MILLIS: Long = 15_000

        private const val DEFAULT_BASE_URL = "https://pokeapi.co"

        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                socketTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            }
        }
    }
}
