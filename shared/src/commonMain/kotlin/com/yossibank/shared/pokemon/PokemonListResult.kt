package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiFailure

sealed interface PokemonListResult {
    data class Loaded(
        val pokemon: List<PokemonEntry>,
        val hasMore: Boolean,
        val total: Int,
    ) : PokemonListResult

    data class Degraded(
        val pokemon: List<PokemonEntry>,
        val hasMore: Boolean,
        val total: Int,
        val failure: ApiFailure,
    ) : PokemonListResult

    data class Failed(
        val failure: ApiFailure,
    ) : PokemonListResult

    data object Stale : PokemonListResult
}
