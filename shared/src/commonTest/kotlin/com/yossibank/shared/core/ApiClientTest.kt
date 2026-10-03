package com.yossibank.shared.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
private data class Probe(
    val name: String,
)

private fun client(
    body: String = """{"name":"probe"}""",
    status: HttpStatusCode = HttpStatusCode.OK,
    delayMillis: Long = 0,
    unreachable: Boolean = false,
    dispatcher: CoroutineDispatcher? = null,
): ApiClient {
    val config = MockEngineConfig().apply {
        dispatcher?.let { this.dispatcher = it }
        addHandler {
            delay(delayMillis)

            if (unreachable) {
                throw IllegalStateException("connection refused")
            }

            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
    }
    return ApiClient("https://example.test", httpClient(MockEngine(config)))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApiClientTest {
    @Test
    fun a_successful_response_is_decoded() = runTest {
        val result = client().get<Probe>("/probe")

        assertEquals(Probe("probe"), assertIs<ApiResult.Ok<Probe>>(result).value)
    }

    @Test
    fun an_unreachable_server_is_offline() = runTest {
        val result = assertIs<ApiResult.Err>(client(unreachable = true).get<Probe>("/probe"))

        assertEquals(ApiFailure.Offline, result.failure)
        assertTrue(ApiFailure.Offline.canRetry)
    }

    @Test
    fun a_slow_server_is_told_apart_from_one_it_cannot_reach() = runTest {
        val result = assertIs<ApiResult.Err>(
            client(
                delayMillis = ApiClient.REQUEST_TIMEOUT_MILLIS * 2,
                dispatcher = StandardTestDispatcher(testScheduler),
            ).get<Probe>("/probe"),
        )

        assertEquals(ApiFailure.Timeout, result.failure, "遅いだけのサーバーがオフライン扱いになっている")
        assertTrue(ApiFailure.Timeout.canRetry)
    }

    @Test
    fun a_rejection_carries_its_status_code() = runTest {
        val result = assertIs<ApiResult.Err>(
            client(status = HttpStatusCode.InternalServerError).get<Probe>("/probe"),
        )

        assertEquals(ApiFailure.Server(500), result.failure)
    }

    @Test
    fun a_body_that_cannot_be_read_is_unreadable() = runTest {
        val result = assertIs<ApiResult.Err>(client(body = "not json").get<Probe>("/probe"))

        assertEquals(ApiFailure.Unreadable, result.failure, "モデルと実レスポンスのずれが通信断と同じ扱いになっている")
        assertFalse(ApiFailure.Unreadable.canRetry)
    }

    @Test
    fun a_rejection_is_retryable_only_when_the_server_might_answer_differently() {
        assertFalse(ApiFailure.Server(400).canRetry)
        assertFalse(ApiFailure.Server(404).canRetry)
        assertTrue(ApiFailure.Server(429).canRetry)
        assertTrue(ApiFailure.Server(500).canRetry)
        assertTrue(ApiFailure.Server(503).canRetry)
    }

    @Test
    fun cancellation_propagates_instead_of_becoming_a_failure() = runTest {
        val slow = client(delayMillis = 1_000)
        var outcome = "（未到達）"

        val job = launch {
            outcome = try {
                "戻り値 " + slow.get<Probe>("/probe")
            } catch (e: CancellationException) {
                "cancelled"
            }
        }

        testScheduler.advanceTimeBy(100)
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals("cancelled", outcome, "キャンセルが Err に化けている")
    }

    @Test
    fun a_rejected_token_is_unauthorized_rather_than_a_server_failure() = runTest {
        val result = assertIs<ApiResult.Err>(client(status = HttpStatusCode.Unauthorized).get<Probe>("/probe"))

        assertEquals(ApiFailure.Unauthorized, result.failure, "認証切れがサーバーの失敗に紛れている")
        assertFalse(ApiFailure.Unauthorized.canRetry)
    }

    @Test
    fun a_body_is_sent_as_json() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            sent = (request.body as TextContent).text
            respond(
                content = """{"name":"probe"}""",
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }

        val result = ApiClient("https://example.test", httpClient(engine)).post<Probe, Probe>("/probe", Probe("sent"))

        assertEquals("""{"name":"sent"}""", sent)
        assertEquals(Probe("probe"), assertIs<ApiResult.Ok<Probe>>(result).value)
    }
}
