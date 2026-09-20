package com.yossibank.shared

import kotlinx.serialization.Serializable

@Serializable
internal data class PokemonSpecies(
    val names: List<LocalizedName>,
) {
    fun japaneseName(): String? {
        val byLanguage = names.associate { it.language.name to it.name }
        return byLanguage["ja-hrkt"] ?: byLanguage["ja"]
    }

    @Serializable
    internal data class LocalizedName(
        val name: String,
        val language: Language,
    )

    @Serializable
    internal data class Language(
        val name: String,
    )
}
