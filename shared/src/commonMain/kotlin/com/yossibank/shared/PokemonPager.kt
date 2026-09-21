package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PokemonPager internal constructor(
    private val api: PokemonApi,
    private val pageSize: Int,
) {
    constructor() : this(PokemonApi(), PokemonApi.PAGE_SIZE)

    private val loadMutex = Mutex()
    private val stateMutex = Mutex()
    private val loaded = mutableListOf<PokemonEntry>()
    private var exhausted = false
    private var generation = 0

    suspend fun loadNext(): PokemonListResult = loadMutex.withLock {
        val start = stateMutex.withLock { Snapshot(loaded.size, exhausted, generation) }

        if (start.exhausted) {
            return@withLock stateMutex.withLock { loaded() }
        }

        when (val page = api.fetchPage(limit = pageSize, offset = start.offset)) {
            is PokemonPageResult.Loaded -> {
                val entries = enrich(page.pokemon)

                stateMutex.withLock {
                    if (start.generation == generation) {
                        loaded += entries
                        exhausted = !page.hasMore
                    }
                    loaded()
                }
            }

            is PokemonPageResult.Failed -> stateMutex.withLock { failed(page.reason) }
        }
    }

    suspend fun reset() = stateMutex.withLock {
        loaded.clear()
        exhausted = false
        generation += 1
    }

    fun close() = api.close()

    private fun loaded() = PokemonListResult.Loaded(
        pokemon = loaded.toList(),
        hasMore = !exhausted,
    )

    private fun failed(reason: PokemonListFailure) = PokemonListResult.Failed(
        pokemon = loaded.toList(),
        hasMore = !exhausted,
        failure = reason,
    )

    private suspend fun enrich(summaries: List<PokemonSummary>): List<PokemonEntry> = coroutineScope {
        summaries
            .map { summary ->
                async {
                    val id = PokemonEntry.idOf(summary) ?: return@async null

                    when (val detail = api.fetchDetail(id)) {
                        is FetchOutcome.Ok -> PokemonEntry.from(id, summary, detail.value)
                        is FetchOutcome.Err -> PokemonEntry.nameOnly(id, summary)
                    }
                }
            }.awaitAll()
            .filterNotNull()
    }

    private data class Snapshot(
        val offset: Int,
        val exhausted: Boolean,
        val generation: Int,
    )

    companion object {
        const val PREFETCH_DISTANCE: Int = 3
    }
}
