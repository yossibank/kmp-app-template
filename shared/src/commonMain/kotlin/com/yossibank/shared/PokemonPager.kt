package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonSummary
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 一覧の追加取得。累積した一覧を返すので、消費側は受け取った一覧を置き換えるだけでよい。
 * 取得中に重ねて呼ばれても 1 件しか走らない。
 */
class PokemonPager internal constructor(
    private val api: PokemonApi,
    private val pageSize: Int,
) {
    constructor() : this(PokemonApi(), PokemonApi.PAGE_SIZE)

    private val mutex = Mutex()
    private val loaded = mutableListOf<PokemonSummary>()
    private var exhausted = false

    suspend fun loadNext(): PokemonListResult = mutex.withLock {
        if (exhausted) {
            return@withLock PokemonListResult.Loaded(loaded.toList(), hasMore = false)
        }

        when (val result = api.fetchPage(limit = pageSize, offset = loaded.size)) {
            is PokemonListResult.Loaded -> {
                loaded += result.pokemon
                exhausted = !result.hasMore
                PokemonListResult.Loaded(loaded.toList(), hasMore = result.hasMore)
            }

            is PokemonListResult.Failed -> result
        }
    }

    suspend fun reset() = mutex.withLock {
        loaded.clear()
        exhausted = false
    }
}
