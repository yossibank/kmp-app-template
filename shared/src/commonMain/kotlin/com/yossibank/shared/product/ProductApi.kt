package com.yossibank.shared.product

import com.yossibank.shared.auth.Session
import com.yossibank.shared.core.ApiClient
import com.yossibank.shared.core.ApiResult
import com.yossibank.shared.core.map
import com.yossibank.shared.generated.model.ProductList
import com.yossibank.shared.generated.model.ProductSummary
import io.ktor.client.request.parameter

internal data class ProductPage(
    val products: List<ProductSummary>,
    val hasMore: Boolean,
    val total: Int,
)

internal class ProductApi(
    private val client: () -> ApiClient = { Session.current.api },
) {
    suspend fun fetchPage(
        limit: Int,
        skip: Int,
    ): ApiResult<ProductPage> = client()
        .get<ProductList>("/auth/products") {
            parameter("limit", limit)
            parameter("skip", skip)
            parameter("select", "title,thumbnail,brand,price")
        }.map {
            ProductPage(products = it.products, hasMore = it.skip + it.products.size < it.total, total = it.total)
        }
}
