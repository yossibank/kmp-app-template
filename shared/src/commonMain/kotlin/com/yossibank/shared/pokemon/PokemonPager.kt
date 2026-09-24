package com.yossibank.shared.pokemon

import com.yossibank.shared.core.OffsetPager
import com.yossibank.shared.core.Page
import com.yossibank.shared.core.PageResult
import com.yossibank.shared.core.map

class PokemonPager internal constructor(
    private val api: PokemonApi,
    pageSize: Int,
) {
    constructor() : this(PokemonApi(), PokemonApi.PAGE_SIZE)

    private val pager = OffsetPager(pageSize) { offset, limit ->
        api.fetchPage(limit = limit, offset = offset).map { page ->
            Page(
                items = page.pokemon.mapNotNull(PokemonEntry::of),
                consumed = page.pokemon.size,
                hasMore = page.hasMore,
                total = page.total,
            )
        }
    }

    suspend fun loadNext(): PokemonListResult = when (val result = pager.loadNext()) {
        is PageResult.Loaded -> PokemonListResult.Loaded(result.items, result.hasMore, result.total, result.failure)
        is PageResult.Failed -> PokemonListResult.Failed(result.failure)
        PageResult.Stale -> PokemonListResult.Stale
    }

    suspend fun reset() = pager.reset()

    fun close() = api.close()
}
