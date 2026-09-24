package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiClient
import com.yossibank.shared.core.ApiResult
import com.yossibank.shared.core.map
import com.yossibank.shared.pokemon.generated.model.PokemonSummary
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.parameter
import com.yossibank.shared.pokemon.generated.model.PaginatedPokemonSummaryList as ListResponse

internal data class PokemonPage(
    val pokemon: List<PokemonSummary>,
    val hasMore: Boolean,
    val total: Int,
)

internal class PokemonApi(
    baseUrl: String = DEFAULT_BASE_URL,
    engine: HttpClientEngine? = null,
) {
    private val client = ApiClient(baseUrl, engine)

    suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): ApiResult<PokemonPage> = client
        .get<ListResponse>("/api/v2/pokemon/") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.map { PokemonPage(pokemon = it.results, hasMore = it.next != null, total = it.count) }

    fun close() = client.close()

    companion object {
        const val PAGE_SIZE: Int = 20

        private const val DEFAULT_BASE_URL = "https://pokeapi.co"
    }
}
