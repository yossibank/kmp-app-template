package com.yossibank.shared.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class Page<T>(
    val items: List<T>,
    val consumed: Int,
    val hasMore: Boolean,
    val total: Int,
)

internal data class Revision<T>(
    val items: List<T>,
    val failure: ApiFailure?,
)

internal sealed interface PageResult<out T> {
    data class Loaded<T>(
        val items: List<T>,
        val hasMore: Boolean,
        val total: Int,
        val failure: ApiFailure?,
    ) : PageResult<T>

    data class Failed(
        val failure: ApiFailure,
    ) : PageResult<Nothing>

    data object Stale : PageResult<Nothing>
}

internal class OffsetPager<T>(
    private val pageSize: Int,
    private val fetchPage: suspend (offset: Int, limit: Int) -> ApiResult<Page<T>>,
) {
    private val loadMutex = Mutex()
    private val stateMutex = Mutex()
    private val loaded = mutableListOf<T>()
    private var nextOffset = 0
    private var total = 0
    private var exhausted = false
    private var generation = 0

    suspend fun loadNext(): PageResult<T> = loadMutex.withLock {
        val start = stateMutex.withLock { Snapshot(nextOffset, exhausted, generation) }

        if (start.exhausted) {
            return@withLock stateMutex.withLock { result(failure = null) }
        }

        when (val page = fetchPage(start.offset, pageSize)) {
            is ApiResult.Ok -> commit(start.generation) {
                loaded += page.value.items
                nextOffset += page.value.consumed
                total = page.value.total
                exhausted = !page.value.hasMore || page.value.consumed == 0
                result(failure = null)
            }

            is ApiResult.Err -> commit(start.generation) { result(failure = page.failure) }
        }
    }

    suspend fun revise(transform: suspend (List<T>) -> Revision<T>): PageResult<T> = loadMutex.withLock {
        val (startGeneration, items) = stateMutex.withLock { generation to loaded.toList() }
        val revision = transform(items)

        commit(startGeneration) {
            loaded.clear()
            loaded += revision.items
            result(failure = revision.failure)
        }
    }

    suspend fun reset() = stateMutex.withLock {
        loaded.clear()
        nextOffset = 0
        total = 0
        exhausted = false
        generation += 1
    }

    private fun result(failure: ApiFailure?): PageResult<T> {
        val items = loaded.toList()

        return if (failure != null && items.isEmpty()) {
            PageResult.Failed(failure)
        } else {
            PageResult.Loaded(items, hasMore = !exhausted, total = total, failure = failure)
        }
    }

    private suspend fun commit(
        startGeneration: Int,
        write: () -> PageResult<T>,
    ): PageResult<T> = stateMutex.withLock {
        if (startGeneration != generation) PageResult.Stale else write()
    }

    private data class Snapshot(
        val offset: Int,
        val exhausted: Boolean,
        val generation: Int,
    )
}
