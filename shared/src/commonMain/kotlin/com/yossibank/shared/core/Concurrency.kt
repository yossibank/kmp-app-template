package com.yossibank.shared.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal suspend fun <T, R> List<T>.mapConcurrently(
    limit: Int,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    val gate = Semaphore(limit)

    map { item -> async { gate.withPermit { transform(item) } } }.awaitAll()
}
