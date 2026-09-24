package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun loadNext_fills_each_row_from_the_detail_endpoint() = runTest {
        val pager = PageServer(total = 1).pager(pageSize = 1)

        val loaded = assertLoaded(pager.loadNext())
        val entry = loaded.pokemon.single()

        assertEquals(0, entry.id)
        assertEquals(0, loaded.incompleteCount)
        assertEquals("p0", entry.name)

        val detail = assertIs<PokemonEntryDetail.Loaded>(entry.detail)
        assertEquals("https://img.test/artwork/0.png", detail.imageUrl, "大きく出せる画像を捨てている")
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

        assertEquals(ApiFailure.Server(500), missing.failure, "詳細の失敗理由が消えている")
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
    fun a_closed_pager_reports_a_failure_instead_of_throwing() = runTest {
        val pager = PageServer(total = 1).pager(pageSize = 1)

        pager.close()

        val result = assertIs<PokemonListResult.Failed>(pager.loadNext(), "close 後の loadNext が例外で返っている")
        assertEquals(ApiFailure.Closed, result.failure)
    }
}
