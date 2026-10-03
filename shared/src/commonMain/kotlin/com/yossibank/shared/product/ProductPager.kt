package com.yossibank.shared.product

import com.yossibank.shared.core.OffsetPager
import com.yossibank.shared.core.Page
import com.yossibank.shared.core.PageResult
import com.yossibank.shared.core.map

class ProductPager internal constructor(
    private val api: ProductApi,
    pageSize: Int,
) {
    constructor() : this(ProductApi(), 20)

    private val pager = OffsetPager(pageSize) { offset, limit ->
        api.fetchPage(limit = limit, skip = offset).map { page ->
            Page(
                items = page.products.map(ProductEntry::of),
                consumed = page.products.size,
                hasMore = page.hasMore,
                total = page.total,
            )
        }
    }

    suspend fun reload(): ProductListResult {
        pager.reset()
        return loadNext()
    }

    suspend fun loadNext(): ProductListResult = when (val result = pager.loadNext()) {
        is PageResult.Loaded -> when (val failure = result.failure) {
            null -> ProductListResult.Loaded(result.items, result.hasMore, result.total)
            else -> ProductListResult.Degraded(result.items, result.hasMore, result.total, failure)
        }

        is PageResult.Failed -> ProductListResult.Failed(result.failure)

        PageResult.Stale -> ProductListResult.Stale
    }
}
