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

private fun engine() = MockEngine {
    respond(
        content = pageJson(ids = listOf(1), next = null),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )
}

class PokemonApiCloseTest {
    @Test
    fun close_shuts_down_the_client() = runTest {
        val api = PokemonApi(baseUrl = TEST_BASE_URL, engine = engine())

        api.close()

        val outcome = try {
            api.fetchPage()
            "取得できてしまった"
        } catch (e: CancellationException) {
            "閉じている"
        }

        assertEquals("閉じている", outcome, "close が効いていない")
    }
}
