package com.yossibank.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val PAGE_JSON = """
{
  "count": 1302,
  "next": "https://pokeapi.co/api/v2/pokemon/?offset=2&limit=2",
  "previous": null,
  "results": [
    { "name": "bulbasaur", "url": "https://pokeapi.co/api/v2/pokemon/1/" },
    { "name": "ivysaur", "url": "https://pokeapi.co/api/v2/pokemon/2/" }
  ]
}
"""

private fun api(
    body: String = PAGE_JSON,
    status: HttpStatusCode = HttpStatusCode.OK,
    delayMillis: Long = 0,
): PokemonApi {
    val engine = MockEngine {
        delay(delayMillis)
        respond(
            content = body,
            status = status,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
    }
    return PokemonApi(
        baseUrl = "https://example.test",
        client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class PokemonApiTest {
    @Test
    fun fetchPage_maps_the_response_into_a_loaded_result() = runTest {
        val result = api().fetchPage()
        val loaded = assertIs<PokemonListResult.Loaded>(result)
        assertEquals(listOf("bulbasaur", "ivysaur"), loaded.pokemon.map { it.name })
        assertTrue(loaded.hasMore, "next があるので続きがある")
    }

    @Test
    fun fetchPage_reports_a_failure_instead_of_throwing() = runTest {
        val result = api(body = "not json", status = HttpStatusCode.InternalServerError).fetchPage()
        assertIs<PokemonListResult.Failed>(result)
    }

    @Test
    fun fetchPage_propagates_cancellation_instead_of_reporting_a_failure() = runTest {
        val slowApi = api(delayMillis = 1_000)
        var outcome = "（未到達）"

        val job = launch {
            outcome = try {
                "戻り値 " + slowApi.fetchPage()
            } catch (e: CancellationException) {
                "cancelled"
            }
        }

        testScheduler.advanceTimeBy(100)
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals("cancelled", outcome, "キャンセルが Failed に化けている")
    }
}
