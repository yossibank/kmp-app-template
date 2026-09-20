package com.yossibank.shared

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

sealed interface PokemonListResult {
    data class Loaded(
        val pokemon: List<PokemonSummary>,
        val hasMore: Boolean,
    ) : PokemonListResult

    sealed interface Failed : PokemonListResult {
        /**
         * 再試行で回復しうるか。文言と違って消費側の裁量ではない。
         * 分類を増やしたときに消費側ごとに判断が割れるのを防ぐため、ここで決める。
         */
        val canRetry: Boolean

        data object Offline : Failed {
            override val canRetry = true
        }

        data class Server(
            val statusCode: Int,
        ) : Failed {
            override val canRetry = true
        }

        data object Unexpected : Failed {
            override val canRetry = false
        }
    }
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

    suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): PokemonListResult {
        val response = try {
            client.get("$baseUrl/api/v2/pokemon/") {
                parameter("limit", limit)
                parameter("offset", offset)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PokemonListResult.Failed.Offline
        }

        if (!response.status.isSuccess()) {
            return PokemonListResult.Failed.Server(response.status.value)
        }

        return try {
            val page = response.body<ListResponse>()
            PokemonListResult.Loaded(page.results, hasMore = page.next != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PokemonListResult.Failed.Unexpected
        }
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
