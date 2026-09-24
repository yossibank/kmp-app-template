package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PokemonApiTest {
    @Test
    fun fetchPage_maps_the_response_into_a_page() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v2/pokemon/", request.url.encodedPath)
            assertEquals("2", request.url.parameters["limit"])
            assertEquals("4", request.url.parameters["offset"])

            respond(
                content = pageJson(ids = listOf(4, 5), next = "$TEST_BASE_URL/next", count = 57),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }

        val result = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine).fetchPage(limit = 2, offset = 4)

        val page = assertIs<ApiResult.Ok<PokemonPage>>(result).value
        assertEquals(listOf("p4", "p5"), page.pokemon.map { it.name })
        assertTrue(page.hasMore)
        assertEquals(57, page.total)
    }
}
