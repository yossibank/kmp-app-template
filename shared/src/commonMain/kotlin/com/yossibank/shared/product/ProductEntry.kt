package com.yossibank.shared.product

import com.yossibank.shared.generated.model.ProductSummary

data class ProductEntry(
    val id: Int,
    val name: String,
    val thumbnailUrl: String,
) {
    internal companion object {
        fun of(summary: ProductSummary) = ProductEntry(
            id = summary.id,
            name = summary.title,
            thumbnailUrl = summary.thumbnail,
        )
    }
}
