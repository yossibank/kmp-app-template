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

                stateMutex.withLock {
                    if (start.generation != generation) {
                        PokemonListResult.Stale
                    } else {
                        loaded += entries
                        nextOffset += page.value.pokemon.size
                        exhausted = !page.value.hasMore || page.value.pokemon.isEmpty()
                        result(failure = null)
                    }
                }
            }

            is FetchOutcome.Err -> stateMutex.withLock {
                if (start.generation != generation) {
                    PokemonListResult.Stale
                } else {
                    result(failure = page.reason)
                }
            }
        }
    }

    suspend fun retryMissingDetails(): PokemonListResult = loadMutex.withLock {
        val start = stateMutex.withLock { Snapshot(nextOffset, exhausted, generation) }
        val targets = stateMutex.withLock {
            loaded.filter { it.detail is PokemonEntryDetail.Missing }
        }

        if (targets.isEmpty()) {
            return@withLock stateMutex.withLock { result(failure = null) }
        }

        val repaired = repair(targets)
        val unresolved = repaired.mapNotNull { it.detail as? PokemonEntryDetail.Missing }

        stateMutex.withLock {
            if (start.generation != generation) {
                PokemonListResult.Stale
            } else {
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
    }

    suspend fun reset() = stateMutex.withLock {
        loaded.clear()
        nextOffset = 0
        exhausted = false
        generation += 1
    }

    fun close() = api.close()

    private fun result(failure: PokemonFailure?): PokemonListResult {
        val pokemon = loaded.toList()
        val hasMore = !exhausted

        return when {
            failure == null -> PokemonListResult.Loaded(pokemon, hasMore)
            pokemon.isEmpty() -> PokemonListResult.Failed(failure)
            else -> PokemonListResult.Degraded(pokemon, hasMore, failure)
        }
    }

    private suspend fun enrich(summaries: List<PokemonSummary>): List<PokemonEntry> = coroutineScope {
        val gate = Semaphore(DETAIL_CONCURRENCY)

        summaries
            .map { summary ->
                async {
                    val id = PokemonEntry.idOf(summary) ?: return@async null

                    gate.withPermit {
                        when (val detail = api.fetchDetail(id)) {
                            is FetchOutcome.Ok -> PokemonEntry.from(id, summary, detail.value)
                            is FetchOutcome.Err -> PokemonEntry.nameOnly(id, summary, detail.reason)
                        }
                    }
                }
            }.awaitAll()
            .filterNotNull()
    }

    private suspend fun repair(targets: List<PokemonEntry>): List<PokemonEntry> = coroutineScope {
        val gate = Semaphore(DETAIL_CONCURRENCY)

        targets
            .map { entry ->
                async {
                    gate.withPermit {
                        when (val detail = api.fetchDetail(entry.id)) {
                            is FetchOutcome.Ok -> entry.copy(detail = PokemonEntry.detailOf(detail.value))
                            is FetchOutcome.Err -> entry.copy(detail = PokemonEntryDetail.Missing(detail.reason))
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
        const val PREFETCH_DISTANCE: Int = 3

        internal const val DETAIL_CONCURRENCY: Int = 6
    }
}
