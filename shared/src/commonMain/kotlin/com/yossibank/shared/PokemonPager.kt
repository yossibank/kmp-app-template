package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 累積した一覧を返すので、消費側は受け取った一覧を置き換えるだけでよい。
 * 取得中に重ねて呼ばれても 1 件しか走らない。
 */
class PokemonPager internal constructor(
    private val api: PokemonApi,
    private val pageSize: Int,
) {
    constructor() : this(PokemonApi(), PokemonApi.PAGE_SIZE)

    private val mutex = Mutex()
    private val loaded = mutableListOf<PokemonEntry>()
    private var exhausted = false

    suspend fun loadNext(): PokemonListResult = mutex.withLock {
        if (exhausted) {
            return@withLock result(failure = null)
        }

        when (val page = api.fetchPage(limit = pageSize, offset = loaded.size)) {
            is PokemonPageResult.Loaded -> {
                loaded += enrich(page.pokemon)
                exhausted = !page.hasMore
                result(failure = null)
            }

            is PokemonPageResult.Failed -> result(failure = page.reason)
        }
    }

    private fun result(failure: PokemonListFailure?) = PokemonListResult(
        pokemon = loaded.toList(),
        hasMore = !exhausted,
        failure = failure,
    )

    suspend fun reset() = mutex.withLock {
        loaded.clear()
        exhausted = false
    }

    private suspend fun enrich(summaries: List<PokemonSummary>): List<PokemonEntry> = coroutineScope {
        summaries
            .map { summary ->
                async {
                    val id = PokemonEntry.idOf(summary) ?: return@async null
                    val detail = async { api.fetchDetail(id) }
                    val species = async { api.fetchSpecies(id) }

                    when (val loaded = detail.await()) {
                        null -> PokemonEntry.nameOnly(id, summary)
                        else -> PokemonEntry.from(id, summary, loaded, species.await())
                    }
                }
            }.awaitAll()
            .filterNotNull()
    }

    companion object {
        const val PREFETCH_DISTANCE: Int = 3
    }
}
