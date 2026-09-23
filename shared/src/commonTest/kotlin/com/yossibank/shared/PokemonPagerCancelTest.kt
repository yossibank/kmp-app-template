package com.yossibank.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class StallingServer {
    val firstPageStarted = CompletableDeferred<Unit>()

    val release = CompletableDeferred<Unit>()

    val pageOffsets = mutableListOf<Int>()

    private var pageRequests = 0

    fun pager(): PokemonPager {
        val engine = MockEngine { request ->
            val json = headersOf("Content-Type", ContentType.Application.Json.toString())
            val path = request.url.encodedPath
            val offsetParam = request.url.parameters["offset"]

            when {
                offsetParam == null -> {
                    val id = path.trimEnd('/').substringAfterLast('/').toInt()
                    respond(content = detailJson(id), status = HttpStatusCode.OK, headers = json)
                }

                else -> {
                    val offset = offsetParam.toInt()
                    pageOffsets += offset
                    pageRequests += 1

                    if (pageRequests == 1) {
                        firstPageStarted.complete(Unit)
                        release.await()
                    }

                    respond(
                        content = pageJson(listOf(offset, offset + 1), "$TEST_BASE_URL/next"),
                        status = HttpStatusCode.OK,
                        headers = json,
                    )
                }
            }
        }

        return PokemonPager(
            api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine),
            pageSize = 2,
        )
    }
}

class PokemonPagerCancelTest {
    @Test
    fun reset_does_not_block_behind_a_cancelled_load() = runTest {
        withContext(Dispatchers.Default) {
            val server = StallingServer()
            val pager = server.pager()

            val inFlight = async { pager.loadNext() }
            server.firstPageStarted.await()
            inFlight.cancel()

            withTimeout(5_000) { pager.reset() }

            val after = withTimeout(5_000) { pager.loadNext() }

            assertEquals(listOf("p0", "p1"), assertIs<PokemonListResult.Loaded>(after).pokemon.map { it.name })
            assertEquals(listOf(0, 0), server.pageOffsets, "reset 後に offset 0 から読み直していない")
        }
    }

    @Test
    fun reset_does_not_wait_for_a_load_that_is_still_running() = runTest {
        withContext(Dispatchers.Default) {
            val server = StallingServer()
            val pager = server.pager()

            val inFlight = async { pager.loadNext() }
            server.firstPageStarted.await()

            withTimeout(5_000) { pager.reset() }

            server.release.complete(Unit)
            inFlight.await()

            val after = withTimeout(5_000) { pager.loadNext() }

            assertEquals(
                listOf("p0", "p1"),
                assertIs<PokemonListResult.Loaded>(after).pokemon.map { it.name },
                "reset 前に走っていた取得の結果が残っている",
            )
            assertEquals(listOf(0, 0), server.pageOffsets, "reset 後に offset 0 から読み直していない")
        }
    }

    @Test
    fun a_load_overtaken_by_reset_says_its_result_is_stale() = runTest {
        withContext(Dispatchers.Default) {
            val server = StallingServer()
            val pager = server.pager()

            val inFlight = async { pager.loadNext() }
            server.firstPageStarted.await()

            withTimeout(5_000) { pager.reset() }
            server.release.complete(Unit)

            assertEquals(
                PokemonListResult.Stale,
                withTimeout(5_000) { inFlight.await() },
                "捨てられた取得の結果が、本物の空リストと区別できない",
            )
        }
    }

    @Test
    fun a_cancelled_load_leaves_no_entries_behind() = runTest {
        withContext(Dispatchers.Default) {
            val server = StallingServer()
            val pager = server.pager()

            val inFlight = async { pager.loadNext() }
            server.firstPageStarted.await()
            inFlight.cancel()

            val next = withTimeout(5_000) { pager.loadNext() }

            assertEquals(
                listOf("p0", "p1"),
                assertIs<PokemonListResult.Loaded>(next).pokemon.map { it.name },
                "取り消した取得の結果が残っている",
            )
        }
    }
}
