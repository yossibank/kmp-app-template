package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonDetail
import com.yossibank.shared.generated.model.PokemonSummary

enum class PokemonTypeKind {
    NORMAL,
    FIRE,
    WATER,
    ELECTRIC,
    GRASS,
    ICE,
    FIGHTING,
    POISON,
    GROUND,
    FLYING,
    PSYCHIC,
    BUG,
    ROCK,
    GHOST,
    DRAGON,
    DARK,
    STEEL,
    FAIRY,
    UNKNOWN,
    ;

    internal companion object {
        fun from(raw: String): PokemonTypeKind = when (raw) {
            "normal" -> NORMAL
            "fire" -> FIRE
            "water" -> WATER
            "electric" -> ELECTRIC
            "grass" -> GRASS
            "ice" -> ICE
            "fighting" -> FIGHTING
            "poison" -> POISON
            "ground" -> GROUND
            "flying" -> FLYING
            "psychic" -> PSYCHIC
            "bug" -> BUG
            "rock" -> ROCK
            "ghost" -> GHOST
            "dragon" -> DRAGON
            "dark" -> DARK
            "steel" -> STEEL
            "fairy" -> FAIRY
            else -> UNKNOWN
        }
    }
}

enum class PokemonStatKind {
    HP,
    ATTACK,
    DEFENSE,
    SPECIAL_ATTACK,
    SPECIAL_DEFENSE,
    SPEED,
    OTHER,
    ;

    internal companion object {
        fun from(raw: String): PokemonStatKind = when (raw) {
            "hp" -> HP
            "attack" -> ATTACK
            "defense" -> DEFENSE
            "special-attack" -> SPECIAL_ATTACK
            "special-defense" -> SPECIAL_DEFENSE
            "speed" -> SPEED
            else -> OTHER
        }
    }
}

data class PokemonBaseStat(
    val kind: PokemonStatKind,
    val value: Int,
)

sealed interface PokemonEntryDetail {
    data class Loaded(
        val spriteUrl: String?,
        val artworkUrl: String?,
        val types: List<PokemonTypeKind>,
        val baseStats: List<PokemonBaseStat>,
    ) : PokemonEntryDetail {
        val totalBaseStat: Int = baseStats.sumOf { it.value }
    }

    data class Missing(
        val failure: PokemonFailure,
    ) : PokemonEntryDetail
}

data class PokemonEntry(
    val id: Int,
    val name: String,
    val detail: PokemonEntryDetail,
) {
    internal companion object {
        fun idOf(summary: PokemonSummary): Int? = summary.url
            .trimEnd('/')
            .substringAfterLast('/')
            .toIntOrNull()

        fun detailOf(detail: PokemonDetail): PokemonEntryDetail.Loaded = PokemonEntryDetail.Loaded(
            spriteUrl = detail.sprites.frontDefault,
            artworkUrl = detail.sprites.other
                ?.officialArtwork
                ?.frontDefault,
            types = detail.types
                .sortedBy { it.slot }
                .map { PokemonTypeKind.from(it.type.name) },
            baseStats = detail.stats.map {
                PokemonBaseStat(
                    kind = PokemonStatKind.from(it.stat.name),
                    value = it.baseStat,
                )
            },
        )
    }
}
