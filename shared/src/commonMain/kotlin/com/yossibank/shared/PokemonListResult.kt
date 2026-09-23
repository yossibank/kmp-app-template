package com.yossibank.shared

sealed interface PokemonListResult {
    data class Loaded(
        val pokemon: List<PokemonEntry>,
        val hasMore: Boolean,
    ) : PokemonListResult {
        val incompleteCount: Int get() = pokemon.count { it.detail is PokemonEntryDetail.Missing }
    }

    data class Degraded(
        val pokemon: List<PokemonEntry>,
        val hasMore: Boolean,
        val failure: PokemonFailure,
    ) : PokemonListResult {
        val incompleteCount: Int get() = pokemon.count { it.detail is PokemonEntryDetail.Missing }
    }

    data class Failed(
        val failure: PokemonFailure,
    ) : PokemonListResult

    data object Stale : PokemonListResult
}
