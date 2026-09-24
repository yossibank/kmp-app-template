package com.yossibank.shared.pokemon

import com.yossibank.shared.core.ApiResult
import com.yossibank.shared.core.OffsetPager
import com.yossibank.shared.core.Page
import com.yossibank.shared.core.PageResult
import com.yossibank.shared.core.Revision
import com.yossibank.shared.core.map
import com.yossibank.shared.core.mapConcurrently
import com.yossibank.shared.pokemon.generated.model.PokemonSummary

class PokemonPager internal constructor(
    private val api: PokemonApi,
    pageSize: Int,
) {
    constructor() : this(PokemonApi(), PokemonApi.PAGE_SIZE)

    private val pager = OffsetPager(pageSize) { offset, limit ->
        api.fetchPage(limit = limit, offset = offset).map { page ->
            Page(
                items = entriesOf(page.pokemon),
                consumed = page.pokemon.size,
                hasMore = page.hasMore,
                total = page.total,
            )
        }
    }

    suspend fun loadNext(): PokemonListResult = pager.loadNext().toListResult()

    suspend fun retryMissingDetails(): PokemonListResult = pager
        .revise { entries ->
            val targets = entries.filter { it.detail is PokemonEntryDetail.Missing }

            if (targets.isEmpty()) {
                return@revise Revision(entries, failure = null)
            }

            val repaired = targets
                .zip(detailsOf(targets.map { it.id })) { entry, detail -> entry.copy(detail = detail) }
                .associateBy { it.id }
            val unresolved = repaired.values.mapNotNull { it.detail as? PokemonEntryDetail.Missing }

            Revision(
                items = entries.map { repaired[it.id] ?: it },
                failure = if (unresolved.size == targets.size) unresolved.first().failure else null,
            )
        }.toListResult()

    suspend fun reset() = pager.reset()

    fun close() = api.close()

    private suspend fun entriesOf(summaries: List<PokemonSummary>): List<PokemonEntry> {
        val named = summaries.mapNotNull { summary -> PokemonEntry.idOf(summary)?.let { it to summary.name } }

        return named.zip(detailsOf(named.map { it.first })) { (id, name), detail ->
            PokemonEntry(id = id, name = name, detail = detail)
        }
    }

    private suspend fun detailsOf(ids: List<Int>): List<PokemonEntryDetail> = ids.mapConcurrently(DETAIL_CONCURRENCY) { id ->
        when (val detail = api.fetchDetail(id)) {
            is ApiResult.Ok -> PokemonEntry.detailOf(detail.value)
            is ApiResult.Err -> PokemonEntryDetail.Missing(detail.failure)
        }
    }

    internal companion object {
        const val DETAIL_CONCURRENCY: Int = 6
    }
}

private fun PageResult<PokemonEntry>.toListResult(): PokemonListResult = when (this) {
    is PageResult.Loaded -> PokemonListResult.Loaded(items, hasMore, total, failure)
    is PageResult.Failed -> PokemonListResult.Failed(failure)
    PageResult.Stale -> PokemonListResult.Stale
}
