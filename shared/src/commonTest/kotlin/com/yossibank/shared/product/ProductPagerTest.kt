package com.yossibank.shared.product

import com.yossibank.shared.auth.FakeServer
import com.yossibank.shared.auth.InMemoryTokenStore
import com.yossibank.shared.auth.Tokens
import com.yossibank.shared.core.ApiFailure
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

private class Catalog(
    private val total: Int,
    private val failFrom: Int? = null,
) {
    val skips = mutableListOf<Int>()

    val server = FakeServer()

    fun pager(
        pageSize: Int,
        store: InMemoryTokenStore = InMemoryTokenStore(Tokens("access-1", "refresh-1")),
    ): ProductPager {
        server.productsBody = { skip, limit ->
            skips += skip
            val ids = (skip until minOf(skip + limit, total)).toList()
            val products = ids.joinToString(",") { """{"id":$it,"title":"p$it","thumbnail":"https://example.test/$it.webp","price":1.5}""" }
            """{"products":[$products],"total":$total,"skip":$skip,"limit":$limit}"""
        }
        failFrom?.let { from ->
            val body = server.productsBody
            server.productsBody = { skip, limit -> if (skip >= from) error("unreachable") else body(skip, limit) }
        }
        val backend = server.backend(store)
        return ProductPager(ProductApi { backend.api }, pageSize)
    }
}

class ProductPagerTest {
    @Test
    fun a_page_becomes_entries() = runTest {
        val loaded = assertIs<ProductListResult.Loaded>(Catalog(total = 2).pager(pageSize = 2).loadNext())

        assertEquals(listOf(0, 1), loaded.products.map { it.id })
        assertEquals(listOf("p0", "p1"), loaded.products.map { it.title })
        assertEquals("https://example.test/0.webp", loaded.products.first().thumbnailUrl)
        assertFalse(loaded.hasMore)
    }

    @Test
    fun pages_follow_on_from_what_was_already_loaded() = runTest {
        val catalog = Catalog(total = 5)
        val pager = catalog.pager(pageSize = 2)

        var loaded = assertIs<ProductListResult.Loaded>(pager.loadNext())
        repeat(2) { loaded = assertIs(pager.loadNext()) }

        assertEquals(listOf(0, 2, 4), catalog.skips)
        assertEquals(listOf(0, 1, 2, 3, 4), loaded.products.map { it.id })
        assertFalse(loaded.hasMore, "最後のページの後も続きがあることになっている")
        assertEquals(5, loaded.total)
    }

    @Test
    fun reload_starts_the_list_over_from_the_first_page() = runTest {
        val catalog = Catalog(total = 8)
        val pager = catalog.pager(pageSize = 2)

        pager.loadNext()
        pager.loadNext()
        val reloaded = assertIs<ProductListResult.Loaded>(pager.reload())

        assertEquals(listOf(0, 1), reloaded.products.map { it.id }, "読み込んだ分が残っている")
        assertEquals(listOf(0, 2, 0), catalog.skips, "先頭から読み直していない")
    }

    @Test
    fun a_later_page_that_fails_keeps_what_was_loaded_and_says_why() = runTest {
        val pager = Catalog(total = 8, failFrom = 2).pager(pageSize = 2)

        pager.loadNext()
        val degraded = assertIs<ProductListResult.Degraded>(pager.loadNext(), "一部の失敗が全体の失敗か成功に紛れている")

        assertEquals(listOf(0, 1), degraded.products.map { it.id })
        assertEquals(ApiFailure.Offline, degraded.failure)
    }

    @Test
    fun an_ended_session_is_reported_as_unauthorized() = runTest {
        val catalog = Catalog(total = 8)
        catalog.server.validAccess = "access-expired"
        catalog.server.refreshStatus = HttpStatusCode.Forbidden

        val failed = assertIs<ProductListResult.Failed>(catalog.pager(pageSize = 2).reload())

        assertEquals(ApiFailure.Unauthorized, failed.failure)
    }
}
