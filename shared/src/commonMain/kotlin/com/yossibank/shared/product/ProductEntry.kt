package com.yossibank.shared.product

import com.yossibank.shared.generated.model.ProductSummary

data class ProductEntry(
    val id: Int,
    val title: String,
    val thumbnailUrl: String,
) {
    val initial: String
        get() = title.take(1).uppercase()

    internal companion object {
        fun of(summary: ProductSummary) = ProductEntry(
            id = summary.id,
            title = summary.title,
            thumbnailUrl = summary.thumbnail,
        )
    }
}
