package com.yossibank.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

private fun engine() = MockEngine { request ->
    val json = headersOf("Content-Type", ContentType.Application.Json.toString())

    if (request.url.parameters["offset"] == null) {
        respond(content = detailJson(id = 1), status = HttpStatusCode.OK, headers = json)
    } else {
        respond(content = pageJson(ids = listOf(1), next = null), status = HttpStatusCode.OK, headers = json)
    }
}

class PokemonApiCloseTest {
    @Test
    fun a_closed_api_says_so_instead_of_throwing() = runTest {
        val api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine())

        api.close()

        val outcome = assertIs<FetchOutcome.Err>(api.fetchPage(), "close 後の取得が成功している")
        assertEquals(PokemonFailure.Closed, outcome.reason, "close 後の取得が例外で返っている")
        assertFalse(PokemonFailure.Closed.canRetry, "閉じた後の再試行を勧めている")
    }

    @Test
    fun a_closed_pager_reports_a_failure_instead_of_throwing() = runTest {
        val pager = PokemonPager(
            api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine()),
            pageSize = 1,
        )

        pager.close()

        val result = assertIs<PokemonListResult.Failed>(pager.loadNext(), "close 後の loadNext が例外で返っている")
        assertEquals(PokemonFailure.Closed, result.failure)
    }
}
