package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class PokemonPager internal constructor(
    private val api: PokemonApi,
    private val pageSize: Int,
) {
    constructor() : this(PokemonApi(), PokemonApi.PAGE_SIZE)

    private val loadMutex = Mutex()
    private val stateMutex = Mutex()
    private val loaded = mutableListOf<PokemonEntry>()
    private var nextOffset = 0
    private var total = 0
    private var exhausted = false
    private var generation = 0

    suspend fun loadNext(): PokemonListResult = loadMutex.withLock {
        val start = stateMutex.withLock { Snapshot(nextOffset, exhausted, generation) }

        if (start.exhausted) {
            return@withLock stateMutex.withLock { result(failure = null) }
        }

        when (val page = api.fetchPage(limit = pageSize, offset = start.offset)) {
            is FetchOutcome.Ok -> {
                val entries = enrich(page.value.pokemon)

                commit(start.generation) {
                    loaded += entries
                    nextOffset += page.value.pokemon.size
                    total = page.value.total
                    exhausted = !page.value.hasMore || page.value.pokemon.isEmpty()
                    result(failure = null)
                }
            }

            is FetchOutcome.Err -> commit(start.generation) { result(failure = page.reason) }
        }
    }

    suspend fun retryMissingDetails(): PokemonListResult = loadMutex.withLock {
        val (startGeneration, targets) = stateMutex.withLock {
            generation to loaded.filter { it.detail is PokemonEntryDetail.Missing }
        }

        if (targets.isEmpty()) {
            return@withLock stateMutex.withLock { result(failure = null) }
        }

        val repaired = repair(targets)
        val unresolved = repaired.mapNotNull { it.detail as? PokemonEntryDetail.Missing }

        commit(startGeneration) {
            repaired.forEach { entry ->
                val index = loaded.indexOfFirst { it.id == entry.id }

                if (index >= 0) {
                    loaded[index] = entry
                }
            }

            result(
                failure = if (unresolved.size == targets.size) unresolved.first().failure else null,
            )
        }
    }

    suspend fun reset() = stateMutex.withLock {
        loaded.clear()
        nextOffset = 0
        total = 0
        exhausted = false
        generation += 1
    }

    fun close() = api.close()

    private fun result(failure: PokemonFailure?): PokemonListResult {
        val pokemon = loaded.toList()
        val hasMore = !exhausted

        return if (failure != null && pokemon.isEmpty()) {
            PokemonListResult.Failed(failure)
        } else {
            PokemonListResult.Loaded(pokemon, hasMore, total, failure)
        }
    }

    private suspend fun commit(
        startGeneration: Int,
        write: () -> PokemonListResult,
    ): PokemonListResult = stateMutex.withLock {
        if (startGeneration != generation) PokemonListResult.Stale else write()
    }

    private suspend fun enrich(summaries: List<PokemonSummary>): List<PokemonEntry> {
        val named = summaries.mapNotNull { summary -> PokemonEntry.idOf(summary)?.let { it to summary.name } }

        return named.zip(detailsOf(named.map { it.first })) { (id, name), detail ->
            PokemonEntry(id = id, name = name, detail = detail)
        }
    }

    private suspend fun repair(targets: List<PokemonEntry>): List<PokemonEntry> =
        targets.zip(detailsOf(targets.map { it.id })) { entry, detail -> entry.copy(detail = detail) }

    private suspend fun detailsOf(ids: List<Int>): List<PokemonEntryDetail> = coroutineScope {
        val gate = Semaphore(DETAIL_CONCURRENCY)

        ids
            .map { id ->
                async {
                    gate.withPermit {
                        when (val detail = api.fetchDetail(id)) {
                            is FetchOutcome.Ok -> PokemonEntry.detailOf(detail.value)
                            is FetchOutcome.Err -> PokemonEntryDetail.Missing(detail.reason)
                        }
                    }
                }
            }.awaitAll()
    }

    private data class Snapshot(
        val offset: Int,
        val exhausted: Boolean,
        val generation: Int,
    )

    companion object {
        const val PREFETCH_DISTANCE: Int = 8

        internal const val DETAIL_CONCURRENCY: Int = 6
    }
}
