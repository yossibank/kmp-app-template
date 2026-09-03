package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
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

    data class Failed(
        val message: String,
    ) : PokemonListResult
}

class PokemonApi(
    private val baseUrl: String = "https://pokeapi.co",
    private val client: HttpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        },
) {
    /** iOS では SKIE が async throws に変換する。 */
    suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): PokemonListResult =
        try {
            val response: ListResponse =
                client
                    .get("$baseUrl/api/v2/pokemon/") {
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.body()
            PokemonListResult.Loaded(response.results, response.next != null)
        } catch (e: Exception) {
            PokemonListResult.Failed(e.message ?: "unknown error")
        }

    companion object {
        const val PAGE_SIZE: Int = 20
    }
}
