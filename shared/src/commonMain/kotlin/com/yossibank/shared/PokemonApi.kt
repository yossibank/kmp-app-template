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

/**
 * 一覧の取得結果。iOS では SKIE が網羅的に switch できる enum に変換する。
 */
sealed interface PokemonListResult {
    data class Loaded(
        val pokemon: List<PokemonSummary>,
        val hasMore: Boolean,
    ) : PokemonListResult

    /**
     * 失敗の分類。文言は消費側が決める。
     * iOS では onEnum(of:) が 2 段目にも生成される。
     */
    sealed interface Failed : PokemonListResult {
        /** サーバーに到達できない。再試行で回復しうる。 */
        data object Offline : Failed

        /** 到達したが 2xx 以外。再試行で回復しうる。 */
        data class Server(
            val statusCode: Int,
        ) : Failed

        /** 応答を解釈できない。再試行しても直らない。 */
        data object Unexpected : Failed
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

    /** iOS では SKIE が async throws に変換する。 */
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
