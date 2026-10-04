package com.yossibank.shared.product

import com.yossibank.shared.core.OffsetPager
import com.yossibank.shared.core.Page
import com.yossibank.shared.core.PageResult
import com.yossibank.shared.core.map

class CatalogPager internal constructor(
    private val api: ProductApi,
    val pageSize: Int,
) {
    constructor() : this(ProductApi(), 20)

    private val pager = OffsetPager(pageSize) { offset, limit ->
        api.fetchPage(limit = limit, skip = offset).map { page ->
            Page(
                items = page.products.map(CatalogEntry::of),
                consumed = page.products.size,
                hasMore = page.hasMore,
                total = page.total,
            )
        }
    }

    suspend fun reload(): CatalogResult {
        pager.reset()
        return loadNext()
    }

    suspend fun loadNext(): CatalogResult = when (val result = pager.loadNext()) {
        is PageResult.Loaded -> when (val failure = result.failure) {
            null -> CatalogResult.Loaded(result.items, result.hasMore, result.total)
            else -> CatalogResult.Degraded(result.items, result.hasMore, result.total, failure)
        }

        is PageResult.Failed -> CatalogResult.Failed(result.failure)

        PageResult.Stale -> CatalogResult.Stale
    }
}
