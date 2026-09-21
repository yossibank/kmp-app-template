package com.yossibank.shared

import com.yossibank.shared.generated.model.PokemonDetail
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val PAGE_JSON = pageJson(
    ids = listOf(1, 2),
    next = "$TEST_BASE_URL/api/v2/pokemon/?offset=2&limit=2",
)

private fun api(
    body: String = PAGE_JSON,
    status: HttpStatusCode = HttpStatusCode.OK,
    delayMillis: Long = 0,
    unreachable: Boolean = false,
): PokemonApi {
    val engine = MockEngine {
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
    return PokemonApi(baseUrl = TEST_BASE_URL, client = testClient(engine))
}

@OptIn(ExperimentalCoroutinesApi::class)
class PokemonApiTest {
    @Test
    fun fetchPage_maps_the_response_into_a_loaded_result() = runTest {
        val result = api().fetchPage()

        val loaded = assertIs<PokemonPageResult.Loaded>(result)
        assertEquals(listOf("p1", "p2"), loaded.pokemon.map { it.name })
        assertTrue(loaded.hasMore)
    }

    @Test
    fun fetchPage_reports_offline_when_the_server_cannot_be_reached() = runTest {
        val result = assertIs<PokemonPageResult.Failed>(api(unreachable = true).fetchPage())

        assertEquals(PokemonListFailure.Offline, result.reason)
        assertTrue(PokemonListFailure.Offline.canRetry)
    }

    @Test
    fun fetchPage_reports_the_status_code_when_the_server_rejects_it() = runTest {
        val result = assertIs<PokemonPageResult.Failed>(
            api(status = HttpStatusCode.InternalServerError).fetchPage(),
        )

        assertEquals(PokemonListFailure.Server(500), result.reason)
        assertTrue(PokemonListFailure.Server(500).canRetry)
    }

    @Test
    fun fetchPage_reports_unexpected_when_the_body_cannot_be_read() = runTest {
        val result = assertIs<PokemonPageResult.Failed>(api(body = "not json").fetchPage())

        assertEquals(PokemonListFailure.Unexpected, result.reason)
        assertFalse(PokemonListFailure.Unexpected.canRetry)
    }

    @Test
    fun fetchPage_propagates_cancellation_instead_of_reporting_a_failure() = runTest {
        val slowApi = api(delayMillis = 1_000)
        var outcome = "（未到達）"

        val job = launch {
            outcome = try {
                "戻り値 " + slowApi.fetchPage()
            } catch (e: CancellationException) {
                "cancelled"
            }
        }

        testScheduler.advanceTimeBy(100)
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals("cancelled", outcome, "キャンセルが Failed に化けている")
    }

    @Test
    fun fetchDetail_reads_the_fields_the_list_shows() = runTest {
        val outcome = api(body = detailJson(id = 1)).fetchDetail(1)

        val detail = assertIs<FetchOutcome.Ok<PokemonDetail>>(outcome).value
        assertEquals(1, detail.id)
        assertEquals("https://img.test/1.png", detail.sprites.frontDefault)
        assertEquals(listOf("grass", "poison"), detail.types.map { it.type.name })
        assertEquals(listOf(45, 49), detail.stats.map { it.baseStat })
    }

    @Test
    fun fetchDetail_says_why_it_failed_instead_of_returning_null() = runTest {
        assertEquals(
            PokemonListFailure.Server(404),
            assertIs<FetchOutcome.Err>(api(status = HttpStatusCode.NotFound).fetchDetail(1)).reason,
        )
        assertEquals(
            PokemonListFailure.Unexpected,
            assertIs<FetchOutcome.Err>(api(body = "not json").fetchDetail(1)).reason,
            "モデルと実レスポンスのずれが通信断と同じ扱いになっている",
        )
        assertEquals(
            PokemonListFailure.Offline,
            assertIs<FetchOutcome.Err>(api(unreachable = true).fetchDetail(1)).reason,
        )
    }

    @Test
    fun a_rejection_is_retryable_only_when_the_server_might_answer_differently() {
        assertFalse(PokemonListFailure.Server(400).canRetry)
        assertFalse(PokemonListFailure.Server(404).canRetry)
        assertTrue(PokemonListFailure.Server(429).canRetry)
        assertTrue(PokemonListFailure.Server(500).canRetry)
        assertTrue(PokemonListFailure.Server(503).canRetry)
    }

    @Test
    fun fetchDetail_propagates_cancellation() = runTest {
        val slowApi = api(body = detailJson(id = 1), delayMillis = 1_000)
        var outcome = "（未到達）"

        val job = launch {
            outcome = try {
                "戻り値 " + slowApi.fetchDetail(1)
            } catch (e: CancellationException) {
                "cancelled"
            }
        }

        testScheduler.advanceTimeBy(100)
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals("cancelled", outcome, "キャンセルが null に化けている")
    }
}
