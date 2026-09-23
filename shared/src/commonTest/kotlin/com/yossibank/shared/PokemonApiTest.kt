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
import kotlin.test.assertNull
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
    return PokemonApi(baseUrl = TEST_BASE_URL, engine = engine)
}

@OptIn(ExperimentalCoroutinesApi::class)
class PokemonApiTest {
    @Test
    fun fetchPage_maps_the_response_into_a_loaded_result() = runTest {
        val result = api().fetchPage()

        val page = assertIs<FetchOutcome.Ok<PokemonPage>>(result).value
        assertEquals(listOf("p1", "p2"), page.pokemon.map { it.name })
        assertTrue(page.hasMore)
    }

    @Test
    fun fetchPage_reports_offline_when_the_server_cannot_be_reached() = runTest {
        val result = assertIs<FetchOutcome.Err>(api(unreachable = true).fetchPage())

        assertEquals(PokemonFailure.Offline, result.reason)
        assertTrue(PokemonFailure.Offline.canRetry)
    }

    @Test
    fun fetchPage_separates_a_slow_server_from_one_it_cannot_reach() = runTest {
        val result = assertIs<FetchOutcome.Err>(
            api(delayMillis = PokemonApi.REQUEST_TIMEOUT_MILLIS * 2).fetchPage(),
        )

        assertEquals(PokemonFailure.Timeout, result.reason, "遅いだけのサーバーがオフライン扱いになっている")
        assertTrue(PokemonFailure.Timeout.canRetry)
    }

    @Test
    fun fetchPage_reports_the_status_code_when_the_server_rejects_it() = runTest {
        val result = assertIs<FetchOutcome.Err>(
            api(status = HttpStatusCode.InternalServerError).fetchPage(),
        )

        assertEquals(PokemonFailure.Server(500), result.reason)
        assertTrue(PokemonFailure.Server(500).canRetry)
    }

    @Test
    fun fetchPage_reports_unexpected_when_the_body_cannot_be_read() = runTest {
        val result = assertIs<FetchOutcome.Err>(api(body = "not json").fetchPage())

        assertEquals(PokemonFailure.Unexpected, result.reason)
        assertFalse(PokemonFailure.Unexpected.canRetry)
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
    fun fetchDetail_reads_the_large_artwork_beside_the_small_sprite() = runTest {
        val outcome = api(body = detailJson(id = 1)).fetchDetail(1)

        val detail = assertIs<FetchOutcome.Ok<PokemonDetail>>(outcome).value

        assertEquals(
            "https://img.test/artwork/1.png",
            detail.sprites.other
                ?.officialArtwork
                ?.frontDefault,
            "同じ応答に入っている大きい画像を読み落としている",
        )
    }

    @Test
    fun a_detail_without_artwork_still_decodes() = runTest {
        val outcome = api(body = detailJson(id = 1, artwork = null)).fetchDetail(1)

        val detail = assertIs<FetchOutcome.Ok<PokemonDetail>>(outcome, "画像が無いだけで応答全体が読めなくなっている").value

        assertEquals("https://img.test/1.png", detail.sprites.frontDefault)
        assertNull(
            detail.sprites.other
                ?.officialArtwork
                ?.frontDefault,
        )
    }

    @Test
    fun fetchDetail_says_why_it_failed_instead_of_returning_null() = runTest {
        assertEquals(
            PokemonFailure.Server(404),
            assertIs<FetchOutcome.Err>(api(status = HttpStatusCode.NotFound).fetchDetail(1)).reason,
        )
        assertEquals(
            PokemonFailure.Unexpected,
            assertIs<FetchOutcome.Err>(api(body = "not json").fetchDetail(1)).reason,
            "モデルと実レスポンスのずれが通信断と同じ扱いになっている",
        )
        assertEquals(
            PokemonFailure.Offline,
            assertIs<FetchOutcome.Err>(api(unreachable = true).fetchDetail(1)).reason,
        )
    }

    @Test
    fun a_rejection_is_retryable_only_when_the_server_might_answer_differently() {
        assertFalse(PokemonFailure.Server(400).canRetry)
        assertFalse(PokemonFailure.Server(404).canRetry)
        assertTrue(PokemonFailure.Server(429).canRetry)
        assertTrue(PokemonFailure.Server(500).canRetry)
        assertTrue(PokemonFailure.Server(503).canRetry)
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
