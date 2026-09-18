package com.yossibank.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class PageServer(
    private val total: Int,
    private val failFrom: Int? = null,
    private val delayMillis: Long = 0,
) {
    var requests = 0
        private set

    fun api(pageSize: Int): PokemonPager {
        val engine = MockEngine { request ->
            requests += 1
            delay(delayMillis)

            val offset = request.url.parameters["offset"]!!.toInt()
            val limit = request.url.parameters["limit"]!!.toInt()

            if (failFrom != null && offset >= failFrom) {
                respond(
                    content = "",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            } else {
                val names = (offset until minOf(offset + limit, total)).map { "p$it" }
                val results = names.joinToString(",") { """{"name":"$it","url":"u/$it"}""" }
                val next = if (offset + limit < total) """"https://example.test/next"""" else "null"
                respond(
                    content = """{"count":$total,"next":$next,"previous":null,"results":[$results]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        }
        return PokemonPager(
            api = PokemonApi(
                baseUrl = "https://example.test",
                client = HttpClient(engine) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                },
            ),
            pageSize = pageSize,
        )
    }
}

class PokemonPagerTest {
    @Test
    fun loadNext_accumulates_across_pages() = runTest {
        val pager = PageServer(total = 5).api(pageSize = 2)

        val first = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1"), first.pokemon.map { it.name })
        assertTrue(first.hasMore)

        val second = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1", "p2", "p3"), second.pokemon.map { it.name })
        assertTrue(second.hasMore)
    }

    @Test
    fun loadNext_stops_asking_once_the_end_is_reached() = runTest {
        val server = PageServer(total = 3)
        val pager = server.api(pageSize = 2)

        pager.loadNext()
        val last = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1", "p2"), last.pokemon.map { it.name })
        assertFalse(last.hasMore)

        val requestsAtEnd = server.requests
        pager.loadNext()
        assertEquals(requestsAtEnd, server.requests, "終端に達したあとは問い合わせない")
    }

    @Test
    fun reset_starts_the_list_over() = runTest {
        val server = PageServer(total = 5)
        val pager = server.api(pageSize = 2)

        pager.loadNext()
        pager.reset()

        val afterReset = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1"), afterReset.pokemon.map { it.name })
    }

    @Test
    fun a_failed_page_keeps_what_was_already_loaded() = runTest {
        val pager = PageServer(total = 6, failFrom = 2).api(pageSize = 2)

        assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertIs<PokemonListResult.Failed.Server>(pager.loadNext())

        val retried = assertIs<PokemonListResult.Failed.Server>(pager.loadNext())
        assertEquals(500, retried.statusCode)
    }

    @Test
    fun overlapping_calls_do_not_fetch_the_same_page_twice() = runTest {
        val server = PageServer(total = 100, delayMillis = 100)
        val pager = server.api(pageSize = 2)

        val results = listOf(
            async { pager.loadNext() },
            async { pager.loadNext() },
        ).awaitAll()

        assertEquals(2, server.requests, "重ねて呼んでも 1 ページずつしか取らない")
        val names = results.map { assertIs<PokemonListResult.Loaded>(it).pokemon.map { p -> p.name } }
        assertEquals(listOf("p0", "p1"), names[0])
        assertEquals(listOf("p0", "p1", "p2", "p3"), names[1])
    }
}
