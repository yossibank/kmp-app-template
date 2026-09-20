package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PokemonEntryTest {
    @Test
    fun the_id_comes_from_the_url_of_the_summary() {
        assertEquals(
            25,
            PokemonEntry.idOf(PokemonSummary("pikachu", "https://pokeapi.co/api/v2/pokemon/25/")),
        )
        assertEquals(
            25,
            PokemonEntry.idOf(PokemonSummary("pikachu", "https://pokeapi.co/api/v2/pokemon/25")),
        )
    }

    @Test
    fun a_url_without_a_numeric_tail_has_no_id() {
        assertNull(PokemonEntry.idOf(PokemonSummary("pikachu", "https://pokeapi.co/api/v2/pokemon/")))
        assertNull(PokemonEntry.idOf(PokemonSummary("pikachu", "")))
    }

    @Test
    fun unknown_names_fall_back_instead_of_failing() {
        assertEquals(PokemonTypeKind.UNKNOWN, PokemonTypeKind.from("stellar"))
        assertEquals(PokemonStatKind.OTHER, PokemonStatKind.from("accuracy"))
    }

    @Test
    fun every_type_the_api_uses_today_is_mapped() {
        val known = listOf(
            "normal",
            "fire",
            "water",
            "electric",
            "grass",
            "ice",
            "fighting",
            "poison",
            "ground",
            "flying",
            "psychic",
            "bug",
            "rock",
            "ghost",
            "dragon",
            "dark",
            "steel",
            "fairy",
        )

        assertEquals(
            emptyList(),
            known.filter { PokemonTypeKind.from(it) == PokemonTypeKind.UNKNOWN },
            "取りこぼしている型がある",
        )
        assertEquals(known.size + 1, PokemonTypeKind.entries.size, "UNKNOWN 以外に未使用の値がある")
    }
}
