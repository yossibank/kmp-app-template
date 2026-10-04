package com.yossibank.shared.product

import com.yossibank.shared.core.ApiFailure

sealed interface CatalogResult {
    data class Loaded(
        val entries: List<CatalogEntry>,
        val hasMore: Boolean,
        val total: Int,
    ) : CatalogResult

    data class Degraded(
        val entries: List<CatalogEntry>,
        val hasMore: Boolean,
        val total: Int,
        val failure: ApiFailure,
    ) : CatalogResult

    data class Failed(
        val failure: ApiFailure,
    ) : CatalogResult

    data object Stale : CatalogResult
}
