package com.yossibank.shared.product

import com.yossibank.shared.auth.FakeServer
import com.yossibank.shared.auth.InMemoryTokenStore
import com.yossibank.shared.auth.Tokens
import com.yossibank.shared.core.ApiFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CatalogPagerTest {
    private fun pager(
        products: String,
        total: Int,
        failFrom: Int? = null,
    ): CatalogPager {
        val server = FakeServer()
        server.productsBody = { skip, limit ->
            if (failFrom != null && skip >= failFrom) error("unreachable")
            """{"products":[$products],"total":$total,"skip":$skip,"limit":$limit}"""
        }
        val backend = server.backend(InMemoryTokenStore(Tokens("access-1", "refresh-1")))
        return CatalogPager(ProductApi { backend.api }, 20)
    }

    @Test
    fun an_entry_carries_its_brand_and_price() = runTest {
        val loaded = assertIs<CatalogResult.Loaded>(
            pager(
                """{"id":6,"title":"Calvin Klein CK One","thumbnail":"https://example.test/6.webp","brand":"Calvin Klein","price":49.99}""",
                total = 1,
            ).loadNext(),
        )

        val entry = loaded.entries.single()
        assertEquals(6, entry.id)
        assertEquals("Calvin Klein CK One", entry.title)
        assertEquals("https://example.test/6.webp", entry.thumbnailUrl)
        assertEquals("Calvin Klein", entry.brand)
        assertEquals(49.99, entry.price)
    }

    @Test
    fun a_missing_brand_becomes_null() = runTest {
        val loaded = assertIs<CatalogResult.Loaded>(
            pager(
                """{"id":16,"title":"Apple","thumbnail":"https://example.test/16.webp","price":1.99}""",
                total = 1,
            ).loadNext(),
        )

        assertNull(loaded.entries.single().brand)
    }

    @Test
    fun the_page_size_is_what_the_apps_divide_chapters_by() {
        assertEquals(20, CatalogPager().pageSize)
    }

    @Test
    fun a_first_page_that_fails_is_reported_as_failed() = runTest {
        val failed = assertIs<CatalogResult.Failed>(pager("", total = 0, failFrom = 0).loadNext())

        assertEquals(ApiFailure.Offline, failed.failure)
    }
}
