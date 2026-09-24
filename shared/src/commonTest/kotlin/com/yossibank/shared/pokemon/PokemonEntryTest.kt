package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonDetail
import com.yossibank.shared.generated.model.PokemonSummary
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PokemonEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadedFrom(detail: String) = PokemonEntry.detailOf(json.decodeFromString<PokemonDetail>(detail))

    @Test
    fun the_image_is_the_artwork_when_there_is_one() {
        assertEquals("https://img.test/artwork/1.png", loadedFrom(detailJson(1)).imageUrl)
    }

    @Test
    fun the_image_falls_back_to_the_sprite_without_artwork() {
        assertEquals("https://img.test/1.png", loadedFrom(detailJson(1, artwork = null)).imageUrl)
    }

    @Test
    fun there_is_no_image_when_neither_exists() {
        assertNull(loadedFrom(detailJson(1, sprite = null, artwork = null)).imageUrl)
    }

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
