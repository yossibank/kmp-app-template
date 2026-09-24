package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class PageServer(
    private val total: Int,
    private val idlessAt: Set<Int> = emptySet(),
) {
    val requestedPaths = mutableListOf<String>()

    val pageOffsets = mutableListOf<Int>()

    fun pager(pageSize: Int): PokemonPager {
        val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath

            val offset = request.url.parameters["offset"]!!.toInt()
            val limit = request.url.parameters["limit"]!!.toInt()
            pageOffsets += offset

            val ids = (offset until minOf(offset + limit, total)).toList()
            val next = if (offset + limit < total) "$TEST_BASE_URL/next" else null

            respond(
                content = pageJson(ids, next, idlessAt, count = total),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }

        return PokemonPager(
            api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine),
            pageSize = pageSize,
        )
    }
}

class PokemonPagerTest {
    @Test
    fun a_page_becomes_entries_without_asking_for_anything_else() = runTest {
        val server = PageServer(total = 2)
        val pager = server.pager(pageSize = 2)

        val loaded = assertLoaded(pager.loadNext())

        assertEquals(listOf(0, 1), loaded.pokemon.map { it.id })
        assertEquals(listOf("p0", "p1"), loaded.pokemon.map { it.name })
        assertEquals(listOf("/api/v2/pokemon/"), server.requestedPaths, "一覧以外を問い合わせている")
    }

    @Test
    fun a_summary_without_an_id_does_not_shift_the_page_window() = runTest {
        val server = PageServer(total = 8, idlessAt = setOf(1))
        val pager = server.pager(pageSize = 2)

        var names = emptyList<String>()
        repeat(3) {
            names = assertLoaded(pager.loadNext()).pokemon.map { it.name }
        }

        assertEquals(listOf(0, 2, 4), server.pageOffsets, "id を取れない行の分だけ次ページの窓がずれている")
        assertEquals(listOf("p0", "p2", "p3", "p4", "p5"), names)
    }

    @Test
    fun a_closed_pager_reports_a_failure_instead_of_throwing() = runTest {
        val pager = PageServer(total = 1).pager(pageSize = 1)

        pager.close()

        val result = assertIs<PokemonListResult.Failed>(pager.loadNext(), "close 後の loadNext が例外で返っている")
        assertEquals(ApiFailure.Closed, result.failure)
    }
}
