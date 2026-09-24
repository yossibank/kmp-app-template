package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class HealingServer(
    private val total: Int,
    private val failingIds: Set<Int> = emptySet(),
    private val healsAfter: Int = 1,
) {
    var detailRequests = 0
        private set

    private val attempts = mutableMapOf<Int, Int>()

    private val counters = Mutex()

    fun pager(pageSize: Int): PokemonPager {
        val engine = MockEngine { request ->
            val json = headersOf("Content-Type", ContentType.Application.Json.toString())
            val offsetParam = request.url.parameters["offset"]

            if (offsetParam == null) {
                val id = request.url.encodedPath
                    .trimEnd('/')
                    .substringAfterLast('/')
                    .toInt()

                val attempt = counters.withLock {
                    detailRequests += 1
                    val count = (attempts[id] ?: 0) + 1
                    attempts[id] = count
                    count
                }

                if (id in failingIds && attempt <= healsAfter) {
                    respond(content = "", status = HttpStatusCode.InternalServerError, headers = json)
                } else {
                    respond(content = detailJson(id), status = HttpStatusCode.OK, headers = json)
                }
            } else {
                val offset = offsetParam.toInt()
                val limit = request.url.parameters["limit"]!!.toInt()
                val ids = (offset until minOf(offset + limit, total)).toList()
                val next = if (offset + limit < total) "$TEST_BASE_URL/next" else null
                respond(content = pageJson(ids, next), status = HttpStatusCode.OK, headers = json)
            }
        }

        return PokemonPager(
            api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine),
            pageSize = pageSize,
        )
    }
}

class PokemonPagerRetryTest {
    @Test
    fun a_row_that_failed_can_be_filled_without_reloading_the_list() = runTest {
        val pager = HealingServer(total = 3, failingIds = setOf(1)).pager(pageSize = 3)

        val before = assertLoaded(pager.loadNext())
        assertEquals(1, before.incompleteCount)

        val after = assertLoaded(pager.retryMissingDetails())

        assertEquals(0, after.incompleteCount, "再試行しても詳細が埋まっていない")
        assertEquals(listOf("p0", "p1", "p2"), after.pokemon.map { it.name }, "再試行で並び順が変わっている")
        assertIs<PokemonEntryDetail.Loaded>(after.pokemon.single { it.id == 1 }.detail)
    }

    @Test
    fun a_retry_only_asks_for_the_rows_that_are_missing() = runTest {
        val server = HealingServer(total = 4, failingIds = setOf(2))
        val pager = server.pager(pageSize = 4)

        pager.loadNext()
        val afterFirstPage = server.detailRequests

        pager.retryMissingDetails()

        assertEquals(
            afterFirstPage + 1,
            server.detailRequests,
            "埋まっている行まで取り直している",
        )
    }

    @Test
    fun a_retry_that_fixes_nothing_says_why() = runTest {
        val pager = HealingServer(total = 3, failingIds = setOf(1), healsAfter = 99).pager(pageSize = 3)

        pager.loadNext()

        val degraded = assertIs<PokemonListResult.Loaded>(
            pager.retryMissingDetails(),
            "何も直らなかったのに成功として返っている",
        )

        assertEquals(ApiFailure.Server(500), degraded.failure)
        assertEquals(listOf("p0", "p1", "p2"), degraded.pokemon.map { it.name }, "再試行の失敗で行が消えている")
        assertEquals(1, degraded.incompleteCount)
        assertTrue(assertNotNull(degraded.failure).canRetry)
    }

    @Test
    fun a_retry_with_nothing_missing_asks_for_nothing() = runTest {
        val server = HealingServer(total = 3)
        val pager = server.pager(pageSize = 3)

        pager.loadNext()
        val afterFirstPage = server.detailRequests

        val result = assertLoaded(pager.retryMissingDetails())

        assertEquals(afterFirstPage, server.detailRequests, "埋める対象が無いのに問い合わせている")
        assertEquals(0, result.incompleteCount)
        assertEquals(listOf("p0", "p1", "p2"), result.pokemon.map { it.name })
    }
}
