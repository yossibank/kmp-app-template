package com.yossibank.shared.product

import com.yossibank.shared.core.ApiFailure

sealed interface ProductListResult {
    data class Loaded(
        val products: List<ProductEntry>,
        val hasMore: Boolean,
        val total: Int,
    ) : ProductListResult

    data class Degraded(
        val products: List<ProductEntry>,
        val hasMore: Boolean,
        val total: Int,
        val failure: ApiFailure,
    ) : ProductListResult

    data class Failed(
        val failure: ApiFailure,
    ) : ProductListResult

    data object Stale : ProductListResult
}
