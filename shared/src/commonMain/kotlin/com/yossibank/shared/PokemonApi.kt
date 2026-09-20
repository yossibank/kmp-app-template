package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonDetail
import com.yossibank.shared.generated.model.PokemonSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import com.yossibank.shared.generated.model.PaginatedPokemonSummaryList as ListResponse

sealed interface PokemonListFailure {
    /**
     * 文言と違って消費側の裁量ではない。分類を増やしたときに
     * 消費側ごとに判断が割れるのを防ぐため、ここで決める。
     */
    val canRetry: Boolean

    data object Offline : PokemonListFailure {
        override val canRetry = true
    }

    data class Server(
        val statusCode: Int,
    ) : PokemonListFailure {
        override val canRetry = true
    }

    data object Unexpected : PokemonListFailure {
        override val canRetry = false
    }
}

/**
 * 失敗しても pokemon には直前までの累積が入る。消費側が「失敗したら前の値を残す」
 * を各自で実装すると OS ごとにずれるため、ここで一本化する。
 */
data class PokemonListResult(
    val pokemon: List<PokemonEntry>,
    val hasMore: Boolean,
    val failure: PokemonListFailure?,
)

internal sealed interface PokemonPageResult {
    data class Loaded(
        val pokemon: List<PokemonSummary>,
        val hasMore: Boolean,
    ) : PokemonPageResult

    data class Failed(
        val reason: PokemonListFailure,
    ) : PokemonPageResult
}

/**
 * Kotlin の引数既定値は Swift に渡らないため、iOS からは全引数を要求する
 * イニシャライザしか見えない。Ktor の HttpClient を Swift 側で組めないので、
 * 既定の依存だけで作れる経路を用意する。
 */
class PokemonApi internal constructor(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    constructor() : this(DEFAULT_BASE_URL, defaultClient())

    internal suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): PokemonPageResult {
        val response = try {
            client.get("$baseUrl/api/v2/pokemon/") {
                parameter("limit", limit)
                parameter("offset", offset)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PokemonPageResult.Failed(PokemonListFailure.Offline)
        }

        if (!response.status.isSuccess()) {
            return PokemonPageResult.Failed(PokemonListFailure.Server(response.status.value))
        }

        return try {
            val page = response.body<ListResponse>()
            PokemonPageResult.Loaded(page.results, hasMore = page.next != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PokemonPageResult.Failed(PokemonListFailure.Unexpected)
        }
    }

    internal suspend fun fetchDetail(id: Int): PokemonDetail? = optional("$baseUrl/api/v2/pokemon/$id/")

    internal suspend fun fetchSpecies(id: Int): PokemonSpecies? = optional("$baseUrl/api/v2/pokemon-species/$id/")

    private suspend inline fun <reified T> optional(url: String): T? = try {
        val response = client.get(url)

        if (response.status.isSuccess()) {
            response.body<T>()
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val PAGE_SIZE: Int = 20

        private const val DEFAULT_BASE_URL = "https://pokeapi.co"

        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
