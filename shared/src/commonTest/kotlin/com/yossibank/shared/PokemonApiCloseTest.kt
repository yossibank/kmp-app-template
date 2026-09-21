package com.yossibank.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun engine() = MockEngine {
    respond(
        content = pageJson(ids = listOf(1), next = null),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )
}

class PokemonApiCloseTest {
    @Test
    fun close_leaves_a_client_it_was_handed_alone() = runTest {
        val api = PokemonApi(baseUrl = TEST_BASE_URL, client = testClient(engine()))

        api.close()

        assertIs<PokemonPageResult.Loaded>(api.fetchPage(), "渡されたクライアントまで閉じている")
    }

    @Test
    fun close_shuts_down_the_client_it_created() = runTest {
        val api = PokemonApi(baseUrl = TEST_BASE_URL, client = testClient(engine()), ownsClient = true)

        api.close()

        val outcome = try {
            api.fetchPage()
            "取得できてしまった"
        } catch (e: CancellationException) {
            "閉じている"
        }

        assertEquals("閉じている", outcome, "自分で作ったクライアントを閉じていない")
    }
}
