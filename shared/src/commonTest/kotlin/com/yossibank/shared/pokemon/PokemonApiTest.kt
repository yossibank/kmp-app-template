package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiFailure
import com.yossibank.shared.core.ApiResult
import com.yossibank.shared.pokemon.generated.model.PokemonDetail
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PAGE_JSON = pageJson(
    ids = listOf(1, 2),
    next = "$TEST_BASE_URL/api/v2/pokemon/?offset=2&limit=2",
)

private fun api(
    body: String = PAGE_JSON,
    status: HttpStatusCode = HttpStatusCode.OK,
): PokemonApi {
    val engine = MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
    }
    return PokemonApi(baseUrl = TEST_BASE_URL, engine = engine)
}

class PokemonApiTest {
    @Test
    fun fetchPage_maps_the_response_into_a_page() = runTest {
        val result = api().fetchPage()

        val page = assertIs<ApiResult.Ok<PokemonPage>>(result).value
        assertEquals(listOf("p1", "p2"), page.pokemon.map { it.name })
        assertTrue(page.hasMore)
    }

    @Test
    fun fetchDetail_reads_the_fields_the_list_shows() = runTest {
        val outcome = api(body = detailJson(id = 1)).fetchDetail(1)

        val detail = assertIs<ApiResult.Ok<PokemonDetail>>(outcome).value
        assertEquals(1, detail.id)
        assertEquals("https://img.test/1.png", detail.sprites.frontDefault)
        assertEquals(listOf("grass", "poison"), detail.types.map { it.type.name })
        assertEquals(listOf(45, 49), detail.stats.map { it.baseStat })
    }

    @Test
    fun fetchDetail_reads_the_large_artwork_beside_the_small_sprite() = runTest {
        val outcome = api(body = detailJson(id = 1)).fetchDetail(1)

        val detail = assertIs<ApiResult.Ok<PokemonDetail>>(outcome).value

        assertEquals(
            "https://img.test/artwork/1.png",
            detail.sprites.other
                ?.officialArtwork
                ?.frontDefault,
            "同じ応答に入っている大きい画像を読み落としている",
        )
    }

    @Test
    fun a_detail_without_artwork_still_decodes() = runTest {
        val outcome = api(body = detailJson(id = 1, artwork = null)).fetchDetail(1)

        val detail = assertIs<ApiResult.Ok<PokemonDetail>>(outcome, "画像が無いだけで応答全体が読めなくなっている").value

        assertEquals("https://img.test/1.png", detail.sprites.frontDefault)
        assertNull(
            detail.sprites.other
                ?.officialArtwork
                ?.frontDefault,
        )
    }

    @Test
    fun fetchDetail_says_why_it_failed_instead_of_returning_null() = runTest {
        assertEquals(
            ApiFailure.Server(404),
            assertIs<ApiResult.Err>(api(status = HttpStatusCode.NotFound).fetchDetail(1)).failure,
        )
    }
}
