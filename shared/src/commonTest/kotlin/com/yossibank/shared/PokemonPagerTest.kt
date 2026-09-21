package com.yossibank.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class PageServer(
    private val total: Int,
    private val failFrom: Int? = null,
    private val delayMillis: Long = 0,
    private val detailFailsFor: Set<Int> = emptySet(),
    private val idlessAt: Set<Int> = emptySet(),
) {
    val pageOffsets = mutableListOf<Int>()

    var detailRequests = 0
        private set

    var maxConcurrentDetails = 0
        private set

    private var inFlightDetails = 0

    fun pager(pageSize: Int): PokemonPager {
        val engine = MockEngine { request ->
            val json = headersOf("Content-Type", ContentType.Application.Json.toString())
            val offsetParam = request.url.parameters["offset"]

            if (offsetParam == null) {
                detailRequests += 1
                inFlightDetails += 1
                maxConcurrentDetails = maxOf(maxConcurrentDetails, inFlightDetails)
                delay(delayMillis)
                inFlightDetails -= 1

                val id = request.url.encodedPath
                    .trimEnd('/')
                    .substringAfterLast('/')
                    .toInt()

                if (id in detailFailsFor) {
                    respond(content = "", status = HttpStatusCode.InternalServerError, headers = json)
                } else {
                    respond(content = detailJson(id), status = HttpStatusCode.OK, headers = json)
                }
            } else {
                delay(delayMillis)
                val offset = offsetParam.toInt()
                val limit = request.url.parameters["limit"]!!.toInt()
                pageOffsets += offset

                if (failFrom != null && offset >= failFrom) {
                    respond(content = "", status = HttpStatusCode.InternalServerError, headers = json)
                } else {
                    val ids = (offset until minOf(offset + limit, total)).toList()
                    val next = if (offset + limit < total) "$TEST_BASE_URL/next" else null
                    respond(content = pageJson(ids, next, idlessAt), status = HttpStatusCode.OK, headers = json)
                }
            }
        }

        return PokemonPager(
            api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine),
            pageSize = pageSize,
        )
    }
}

class PokemonPagerTest {
    @Test
    fun loadNext_accumulates_across_pages() = runTest {
        val pager = PageServer(total = 5).pager(pageSize = 2)

        val first = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1"), first.pokemon.map { it.name })
        assertTrue(first.hasMore)

        val second = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1", "p2", "p3"), second.pokemon.map { it.name })
        assertTrue(second.hasMore)
    }

    @Test
    fun loadNext_fills_each_row_from_the_detail_endpoint() = runTest {
        val pager = PageServer(total = 1).pager(pageSize = 1)

        val loaded = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        val entry = loaded.pokemon.single()

        assertEquals(0, entry.id)
        assertTrue(entry.hasDetail)
        assertEquals(0, loaded.incompleteCount)
        assertEquals("p0", entry.name)
        assertEquals("https://img.test/0.png", entry.spriteUrl)
        assertEquals(listOf(PokemonTypeKind.GRASS, PokemonTypeKind.POISON), entry.types)
        assertEquals(
            listOf(
                PokemonBaseStat(PokemonStatKind.HP, 45),
                PokemonBaseStat(PokemonStatKind.ATTACK, 49),
            ),
            entry.baseStats,
        )
        assertEquals(94, entry.totalBaseStat)
    }

    @Test
    fun a_row_whose_detail_fails_still_appears_with_its_name() = runTest {
        val pager = PageServer(total = 2, detailFailsFor = setOf(1)).pager(pageSize = 2)

        val loaded = assertIs<PokemonListResult.Loaded>(pager.loadNext())

        assertEquals(listOf("p0", "p1"), loaded.pokemon.map { it.name }, "詳細の失敗で行が消えている")
        assertEquals(1, loaded.incompleteCount, "詳細を取れなかった行が数に出ていない")

        val degraded = loaded.pokemon.single { it.id == 1 }
        assertEquals("p1", degraded.name)
        assertFalse(degraded.hasDetail)
        assertNull(degraded.spriteUrl)
        assertTrue(degraded.types.isEmpty())
        assertTrue(degraded.baseStats.isEmpty())
    }

    @Test
    fun the_details_of_one_page_are_fetched_in_parallel() = runTest {
        val server = PageServer(total = 4, delayMillis = 100)
        val pager = server.pager(pageSize = 4)

        pager.loadNext()

        assertEquals(4, server.detailRequests)
        assertTrue(server.maxConcurrentDetails > 1, "同時実行数=${server.maxConcurrentDetails} 詳細が 1 件ずつ直列に走っている")
    }

    @Test
    fun loadNext_stops_asking_once_the_end_is_reached() = runTest {
        val server = PageServer(total = 3)
        val pager = server.pager(pageSize = 2)

        pager.loadNext()
        val last = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1", "p2"), last.pokemon.map { it.name })
        assertFalse(last.hasMore)

        val requestsAtEnd = server.pageOffsets.size
        pager.loadNext()
        assertEquals(requestsAtEnd, server.pageOffsets.size, "終端に達したあとは問い合わせない")
    }

    @Test
    fun a_summary_without_an_id_does_not_shift_the_page_window() = runTest {
        val server = PageServer(total = 8, idlessAt = setOf(1))
        val pager = server.pager(pageSize = 2)

        var names = emptyList<String>()
        repeat(3) {
            names = assertIs<PokemonListResult.Loaded>(pager.loadNext()).pokemon.map { it.name }
        }

        assertEquals(listOf(0, 2, 4), server.pageOffsets, "id を取れない行の分だけ次ページの窓がずれている")
        assertEquals(names.distinct(), names, "同じ行が二重に積まれている")
        assertEquals(listOf("p0", "p2", "p3", "p4", "p5"), names)
    }

    @Test
    fun reset_starts_the_list_over() = runTest {
        val server = PageServer(total = 5)
        val pager = server.pager(pageSize = 2)

        pager.loadNext()
        pager.reset()

        val afterReset = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1"), afterReset.pokemon.map { it.name })
    }

    @Test
    fun a_failed_page_keeps_what_was_already_loaded() = runTest {
        val pager = PageServer(total = 6, failFrom = 2).pager(pageSize = 2)

        val ok = assertIs<PokemonListResult.Loaded>(pager.loadNext())
        assertEquals(listOf("p0", "p1"), ok.pokemon.map { it.name })

        val failed = assertIs<PokemonListResult.Failed>(pager.loadNext())
        assertEquals(PokemonFailure.Server(500), failed.failure)
        assertEquals(
            listOf("p0", "p1"),
            failed.pokemon.map { it.name },
            "失敗時に累積が落ちている",
        )
    }

    @Test
    fun overlapping_calls_do_not_fetch_the_same_page_twice() = runTest {
        val server = PageServer(total = 100, delayMillis = 100)
        val pager = server.pager(pageSize = 2)

        val results = listOf(
            async { pager.loadNext() },
            async { pager.loadNext() },
        ).awaitAll()

        assertEquals(2, server.pageOffsets.size, "重ねて呼んでも 1 ページずつしか取らない")
        val names = results.map { assertIs<PokemonListResult.Loaded>(it).pokemon.map { p -> p.name } }
        assertEquals(listOf("p0", "p1"), names[0])
        assertEquals(listOf("p0", "p1", "p2", "p3"), names[1])
    }
}
