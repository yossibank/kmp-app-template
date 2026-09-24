package com.yossibank.shared.pokemon

import com.yossibank.shared.pokemon.generated.model.PokemonSummary
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
    fun the_image_is_the_official_artwork_for_the_id() {
        val entry = PokemonEntry.of(PokemonSummary("pikachu", "https://pokeapi.co/api/v2/pokemon/25/"))

        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png",
            entry?.imageUrl,
        )
    }

    @Test
    fun a_summary_without_an_id_has_no_entry() {
        assertNull(PokemonEntry.of(PokemonSummary("pikachu", "")))
    }
}
