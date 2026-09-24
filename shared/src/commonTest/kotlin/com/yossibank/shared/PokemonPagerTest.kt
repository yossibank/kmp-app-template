package com.yossibank.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class PageServer(
    private val total: Int,
    private val failFrom: Int? = null,
    private val delayMillis: Long = 0,
    private val detailFailsFor: Set<Int> = emptySet(),
    private val idlessAt: Set<Int> = emptySet(),
    private val alwaysMore: Boolean = false,
) {
    val pageOffsets = mutableListOf<Int>()

    var detailRequests = 0
        private set

    var maxConcurrentDetails = 0
        private set

    private var inFlightDetails = 0

    private val counters = Mutex()

    fun pager(pageSize: Int): PokemonPager {
        val engine = MockEngine { request ->
            val json = headersOf("Content-Type", ContentType.Application.Json.toString())
            val offsetParam = request.url.parameters["offset"]

            if (offsetParam == null) {
                counters.withLock {
                    detailRequests += 1
                    inFlightDetails += 1
                    maxConcurrentDetails = maxOf(maxConcurrentDetails, inFlightDetails)
                }
                delay(delayMillis)
                counters.withLock { inFlightDetails -= 1 }

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
                counters.withLock { pageOffsets += offset }

                if (failFrom != null && offset >= failFrom) {
                    respond(content = "", status = HttpStatusCode.InternalServerError, headers = json)
                } else {
                    val ids = (offset until minOf(offset + limit, total)).toList()
                    val next = if (alwaysMore || offset + limit < total) "$TEST_BASE_URL/next" else null
                    respond(
                        content = pageJson(ids, next, idlessAt, count = total),
                        status = HttpStatusCode.OK,
                        headers = json,
                    )
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

        val first = assertLoaded(pager.loadNext())
        assertEquals(listOf("p0", "p1"), first.pokemon.map { it.name })
        assertTrue(first.hasMore)

        val second = assertLoaded(pager.loadNext())
        assertEquals(listOf("p0", "p1", "p2", "p3"), second.pokemon.map { it.name })
        assertTrue(second.hasMore)
    }

    @Test
    fun loadNext_fills_each_row_from_the_detail_endpoint() = runTest {
        val pager = PageServer(total = 1).pager(pageSize = 1)

        val loaded = assertLoaded(pager.loadNext())
        val entry = loaded.pokemon.single()

        assertEquals(0, entry.id)
        assertEquals(0, loaded.incompleteCount)
        assertEquals("p0", entry.name)

        val detail = assertIs<PokemonEntryDetail.Loaded>(entry.detail)
        assertEquals("https://img.test/0.png", detail.spriteUrl)
        assertEquals("https://img.test/artwork/0.png", detail.artworkUrl, "大きく出せる画像を捨てている")
        assertEquals(listOf(PokemonTypeKind.GRASS, PokemonTypeKind.POISON), detail.types)
        assertEquals(
            listOf(
                PokemonBaseStat(PokemonStatKind.HP, 45),
                PokemonBaseStat(PokemonStatKind.ATTACK, 49),
            ),
            detail.baseStats,
        )
        assertEquals(94, detail.totalBaseStat)
    }

    @Test
    fun a_row_whose_detail_fails_still_appears_with_its_name() = runTest {
        val pager = PageServer(total = 2, detailFailsFor = setOf(1)).pager(pageSize = 2)

        val loaded = assertLoaded(pager.loadNext())

        assertEquals(listOf("p0", "p1"), loaded.pokemon.map { it.name }, "詳細の失敗で行が消えている")
        assertEquals(1, loaded.incompleteCount, "詳細を取れなかった行が数に出ていない")

        val degraded = loaded.pokemon.single { it.id == 1 }
        assertEquals("p1", degraded.name)
        assertIs<PokemonEntryDetail.Missing>(degraded.detail)
    }

    @Test
    fun a_row_says_why_its_detail_is_missing() = runTest {
        val pager = PageServer(total = 2, detailFailsFor = setOf(1)).pager(pageSize = 2)

        val loaded = assertLoaded(pager.loadNext())
        val missing = assertIs<PokemonEntryDetail.Missing>(loaded.pokemon.single { it.id == 1 }.detail)

        assertEquals(PokemonFailure.Server(500), missing.failure, "詳細の失敗理由が消えている")
        assertTrue(missing.failure.canRetry, "再試行できる失敗かどうかを消費側が判断できない")
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
    fun a_page_does_not_put_every_detail_on_the_wire_at_once() = runTest {
        val size = PokemonPager.DETAIL_CONCURRENCY * 4
        val server = PageServer(total = size, delayMillis = 100)
        val pager = server.pager(pageSize = size)

        pager.loadNext()

        assertEquals(size, server.detailRequests)
        assertEquals(
            PokemonPager.DETAIL_CONCURRENCY,
            server.maxConcurrentDetails,
            "1 ページ分の詳細が同時に $size 本まで出ている",
        )
    }

    @Test
    fun loadNext_stops_asking_once_the_end_is_reached() = runTest {
        val server = PageServer(total = 3)
        val pager = server.pager(pageSize = 2)

        pager.loadNext()
        val last = assertLoaded(pager.loadNext())
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
            names = assertLoaded(pager.loadNext()).pokemon.map { it.name }
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

        val afterReset = assertLoaded(pager.loadNext())
        assertEquals(listOf("p0", "p1"), afterReset.pokemon.map { it.name })
    }

    @Test
    fun a_failed_page_keeps_what_was_already_loaded() = runTest {
        val pager = PageServer(total = 6, failFrom = 2).pager(pageSize = 2)

        val ok = assertLoaded(pager.loadNext())
        assertEquals(listOf("p0", "p1"), ok.pokemon.map { it.name })

        val degraded = assertIs<PokemonListResult.Loaded>(
            pager.loadNext(),
            "見せる行が残っているのに全滅扱いになっている",
        )
        assertEquals(PokemonFailure.Server(500), degraded.failure)
        assertEquals(
            listOf("p0", "p1"),
            degraded.pokemon.map { it.name },
            "失敗時に累積が落ちている",
        )
    }

    @Test
    fun the_result_says_how_many_there_are_in_all() = runTest {
        val pager = PageServer(total = 57).pager(pageSize = 2)

        val loaded = assertLoaded(pager.loadNext())

        assertEquals(57, loaded.total, "応答が持っている全体件数を捨てている")
        assertEquals(2, loaded.pokemon.size)
    }

    @Test
    fun a_first_page_that_fails_has_nothing_to_degrade_to() = runTest {
        val pager = PageServer(total = 6, failFrom = 0).pager(pageSize = 2)

        val failed = assertIs<PokemonListResult.Failed>(
            pager.loadNext(),
            "見せる行が 1 つも無いのに部分成功として返っている",
        )
        assertEquals(PokemonFailure.Server(500), failed.failure)
    }

    @Test
    fun an_empty_page_that_still_claims_more_ends_the_list() = runTest {
        val server = PageServer(total = 0, alwaysMore = true)
        val pager = server.pager(pageSize = 2)

        val first = assertLoaded(pager.loadNext())
        assertFalse(first.hasMore, "0 件のページを受け取ったのに続きがあると言っている")

        pager.loadNext()
        assertEquals(listOf(0), server.pageOffsets, "同じ offset を問い合わせ続けている")
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
        val names = results.map { assertLoaded(it).pokemon.map { p -> p.name } }
        assertEquals(listOf("p0", "p1"), names[0])
        assertEquals(listOf("p0", "p1", "p2", "p3"), names[1])
    }
}
