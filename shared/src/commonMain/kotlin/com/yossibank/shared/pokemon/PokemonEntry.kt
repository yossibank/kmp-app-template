package com.yossibank.shared.pokemon

import com.yossibank.shared.pokemon.generated.model.PokemonSummary

data class PokemonEntry(
    val id: Int,
    val name: String,
    val imageUrl: String,
) {
    internal companion object {
        private const val ARTWORK_BASE_URL =
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork"

        fun of(summary: PokemonSummary): PokemonEntry? = idOf(summary)?.let { id ->
            PokemonEntry(id = id, name = summary.name, imageUrl = "$ARTWORK_BASE_URL/$id.png")
        }

        fun idOf(summary: PokemonSummary): Int? = summary.url
            .trimEnd('/')
            .substringAfterLast('/')
            .toIntOrNull()
    }
}
