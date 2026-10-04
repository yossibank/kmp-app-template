package com.yossibank.shared.product

import com.yossibank.shared.generated.model.ProductSummary

data class CatalogEntry(
    val id: Int,
    val title: String,
    val thumbnailUrl: String,
    val brand: String?,
    val price: Double,
) {
    internal companion object {
        fun of(summary: ProductSummary) = CatalogEntry(
            id = summary.id,
            title = summary.title,
            thumbnailUrl = summary.thumbnail,
            brand = summary.brand,
            price = summary.price,
        )
    }
}
