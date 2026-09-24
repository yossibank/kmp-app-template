package com.yossibank.shared.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Source(
    private val total: Int,
    private val failFrom: Int? = null,
    private val alwaysMore: Boolean = false,
    private val dropped: Set<Int> = emptySet(),
    private val delayMillis: Long = 0,
) {
    val offsets = mutableListOf<Int>()

    val firstPageStarted = CompletableDeferred<Unit>()

    var stallFirstPage: CompletableDeferred<Unit>? = null

    fun pager(pageSize: Int): OffsetPager<Int> = OffsetPager(pageSize) { offset, limit ->
        offsets += offset

        if (offsets.size == 1) {
            firstPageStarted.complete(Unit)
            stallFirstPage?.await()
        }

        delay(delayMillis)

        if (failFrom != null && offset >= failFrom) {
            return@OffsetPager ApiResult.Err(ApiFailure.Server(500))
        }

        val ids = (offset until minOf(offset + limit, total)).toList()

        ApiResult.Ok(
            Page(
                items = ids - dropped,
                consumed = ids.size,
                hasMore = alwaysMore || offset + limit < total,
                total = total,
            ),
        )
    }
}

private fun <T> assertLoaded(
    result: PageResult<T>,
    message: String? = null,
): PageResult.Loaded<T> = assertIs<PageResult.Loaded<T>>(result, message).also {
    assertNull(it.failure, message ?: "失敗を連れた結果になっている")
}

class OffsetPagerTest {
    @Test
    fun loadNext_accumulates_across_pages() = runTest {
        val pager = Source(total = 5).pager(pageSize = 2)

        val first = assertLoaded(pager.loadNext())
        assertEquals(listOf(0, 1), first.items)
        assertTrue(first.hasMore)

        val second = assertLoaded(pager.loadNext())
        assertEquals(listOf(0, 1, 2, 3), second.items)
        assertTrue(second.hasMore)
    }

    @Test
    fun loadNext_stops_asking_once_the_end_is_reached() = runTest {
        val source = Source(total = 3)
        val pager = source.pager(pageSize = 2)

        pager.loadNext()
        val last = assertLoaded(pager.loadNext())
        assertEquals(listOf(0, 1, 2), last.items)
        assertFalse(last.hasMore)

        val requestsAtEnd = source.offsets.size
        pager.loadNext()
        assertEquals(requestsAtEnd, source.offsets.size, "終端に達したあとは問い合わせない")
    }

    @Test
    fun the_next_offset_follows_what_was_consumed_not_what_was_kept() = runTest {
        val source = Source(total = 8, dropped = setOf(1))
        val pager = source.pager(pageSize = 2)

        var items = emptyList<Int>()
        repeat(3) {
            items = assertLoaded(pager.loadNext()).items
        }

        assertEquals(listOf(0, 2, 4), source.offsets, "捨てた行の分だけ次ページの窓がずれている")
        assertEquals(listOf(0, 2, 3, 4, 5), items)
    }

    @Test
    fun reset_starts_the_list_over() = runTest {
        val pager = Source(total = 5).pager(pageSize = 2)

        pager.loadNext()
        pager.reset()

        assertEquals(listOf(0, 1), assertLoaded(pager.loadNext()).items)
    }

    @Test
    fun a_failed_page_keeps_what_was_already_loaded() = runTest {
        val pager = Source(total = 6, failFrom = 2).pager(pageSize = 2)

        assertLoaded(pager.loadNext())

        val degraded = assertIs<PageResult.Loaded<Int>>(pager.loadNext(), "見せる行が残っているのに全滅扱いになっている")
        assertEquals(ApiFailure.Server(500), degraded.failure)
        assertEquals(listOf(0, 1), degraded.items, "失敗時に累積が落ちている")
    }

    @Test
    fun a_first_page_that_fails_has_nothing_to_degrade_to() = runTest {
        val pager = Source(total = 6, failFrom = 0).pager(pageSize = 2)

        val failed = assertIs<PageResult.Failed>(pager.loadNext(), "見せる行が 1 つも無いのに部分成功として返っている")
        assertEquals(ApiFailure.Server(500), failed.failure)
    }

    @Test
    fun the_result_says_how_many_there_are_in_all() = runTest {
        val loaded = assertLoaded(Source(total = 57).pager(pageSize = 2).loadNext())

        assertEquals(57, loaded.total, "応答が持っている全体件数を捨てている")
        assertEquals(2, loaded.items.size)
    }

    @Test
    fun an_empty_page_that_still_claims_more_ends_the_list() = runTest {
        val source = Source(total = 0, alwaysMore = true)
        val pager = source.pager(pageSize = 2)

        assertFalse(assertLoaded(pager.loadNext()).hasMore, "0 件のページを受け取ったのに続きがあると言っている")

        pager.loadNext()
        assertEquals(listOf(0), source.offsets, "同じ offset を問い合わせ続けている")
    }

    @Test
    fun overlapping_calls_do_not_fetch_the_same_page_twice() = runTest {
        val source = Source(total = 100, delayMillis = 100)
        val pager = source.pager(pageSize = 2)

        val results = listOf(
            async { pager.loadNext() },
            async { pager.loadNext() },
        ).awaitAll()

        assertEquals(listOf(0, 2), source.offsets, "重ねて呼んでも 1 ページずつしか取らない")
        assertEquals(listOf(0, 1), assertLoaded(results[0]).items)
        assertEquals(listOf(0, 1, 2, 3), assertLoaded(results[1]).items)
    }

    @Test
    fun reset_does_not_block_behind_a_cancelled_load() = runTest {
        withContext(Dispatchers.Default) {
            val source = Source(total = 100).apply { stallFirstPage = CompletableDeferred() }
            val pager = source.pager(pageSize = 2)

            val inFlight = async { pager.loadNext() }
            source.firstPageStarted.await()
            inFlight.cancel()

            withTimeout(5_000) { pager.reset() }

            val after = withTimeout(5_000) { pager.loadNext() }

            assertEquals(listOf(0, 1), assertLoaded(after).items)
            assertEquals(listOf(0, 0), source.offsets, "reset 後に offset 0 から読み直していない")
        }
    }

    @Test
    fun reset_does_not_wait_for_a_load_that_is_still_running() = runTest {
        withContext(Dispatchers.Default) {
            val stall = CompletableDeferred<Unit>()
            val source = Source(total = 100).apply { stallFirstPage = stall }
            val pager = source.pager(pageSize = 2)

            val inFlight = async { pager.loadNext() }
            source.firstPageStarted.await()

            withTimeout(5_000) { pager.reset() }

            stall.complete(Unit)
            inFlight.await()

            val after = withTimeout(5_000) { pager.loadNext() }

            assertEquals(listOf(0, 1), assertLoaded(after).items, "reset 前に走っていた取得の結果が残っている")
            assertEquals(listOf(0, 0), source.offsets, "reset 後に offset 0 から読み直していない")
        }
    }

    @Test
    fun a_load_overtaken_by_reset_says_its_result_is_stale() = runTest {
        withContext(Dispatchers.Default) {
            val stall = CompletableDeferred<Unit>()
            val source = Source(total = 100).apply { stallFirstPage = stall }
            val pager = source.pager(pageSize = 2)

            val inFlight = async { pager.loadNext() }
            source.firstPageStarted.await()

            withTimeout(5_000) { pager.reset() }
            stall.complete(Unit)

            assertEquals(
                PageResult.Stale,
                withTimeout(5_000) { inFlight.await() },
                "捨てられた取得の結果が、本物の空リストと区別できない",
            )
        }
    }

    @Test
    fun a_cancelled_load_leaves_no_items_behind() = runTest {
        withContext(Dispatchers.Default) {
            val source = Source(total = 100).apply { stallFirstPage = CompletableDeferred() }
            val pager = source.pager(pageSize = 2)

            val inFlight = async { pager.loadNext() }
            source.firstPageStarted.await()
            inFlight.cancel()

            val next = withTimeout(5_000) { pager.loadNext() }

            assertEquals(listOf(0, 1), assertLoaded(next).items, "取り消した取得の結果が残っている")
        }
    }
}
