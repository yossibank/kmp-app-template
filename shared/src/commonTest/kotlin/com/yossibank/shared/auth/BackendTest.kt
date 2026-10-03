package com.yossibank.shared.auth

import com.yossibank.shared.core.ApiFailure
import com.yossibank.shared.core.ApiResult
import com.yossibank.shared.generated.model.ProductList
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private suspend fun Backend.fetch() = api.get<ProductList>("/auth/products")

class BackendTest {
    @Test
    fun logging_in_keeps_the_tokens_and_sends_them_with_later_requests() = runTest {
        val server = FakeServer()
        val store = InMemoryTokenStore()
        val backend = server.backend(store)

        assertEquals(LoginResult.LoggedIn, backend.login("emilys", "emilyspass"))
        assertIs<ApiResult.Ok<ProductList>>(backend.fetch())

        assertTrue(backend.isLoggedIn)
        assertEquals(Tokens("access-1", "refresh-1"), store.tokens)
        assertEquals(listOf<String?>("Bearer access-1"), server.authorizations)
    }

    @Test
    fun a_wrong_password_is_rejected_and_keeps_the_user_logged_out() = runTest {
        val server = FakeServer().apply { loginStatus = HttpStatusCode.BadRequest }
        val backend = server.backend(InMemoryTokenStore())

        assertEquals(LoginResult.Rejected, backend.login("emilys", "wrong"))
        assertFalse(backend.isLoggedIn)
    }

    @Test
    fun a_server_failure_while_logging_in_is_not_mistaken_for_a_wrong_password() = runTest {
        val server = FakeServer().apply { loginStatus = HttpStatusCode.InternalServerError }
        val backend = server.backend(InMemoryTokenStore())

        assertEquals(LoginResult.Failed(ApiFailure.Server(500)), backend.login("emilys", "emilyspass"))
    }

    @Test
    fun an_expired_token_is_refreshed_and_the_request_retried() = runTest {
        val server = FakeServer().apply { validAccess = "access-expired" }
        val store = InMemoryTokenStore(Tokens("access-1", "refresh-1"))
        val backend = server.backend(store)

        assertIs<ApiResult.Ok<ProductList>>(backend.fetch(), "更新後に再試行していない")

        assertEquals(listOf("refresh-1"), server.refreshedWith)
        assertEquals(listOf<String?>("Bearer access-1", "Bearer access-2"), server.authorizations)
        assertEquals(Tokens("access-2", "refresh-2"), store.tokens, "更新したトークンを保存していない")
    }

    @Test
    fun a_rejected_refresh_ends_the_session() = runTest {
        val server = FakeServer().apply {
            validAccess = "access-expired"
            refreshStatus = HttpStatusCode.Forbidden
        }
        val backend = server.backend(InMemoryTokenStore(Tokens("access-1", "refresh-1")))

        val result = assertIs<ApiResult.Err>(backend.fetch())

        assertEquals(ApiFailure.Unauthorized, result.failure)
        assertFalse(backend.isLoggedIn, "使えなくなったトークンを残している")
    }

    @Test
    fun an_unreachable_refresh_keeps_the_session() = runTest {
        val server = FakeServer().apply {
            validAccess = "access-expired"
            refreshUnreachable = true
        }
        val backend = server.backend(InMemoryTokenStore(Tokens("access-1", "refresh-1")))

        val result = assertIs<ApiResult.Err>(backend.fetch())

        assertEquals(ApiFailure.Offline, result.failure, "通信断がログアウト扱いになっている")
        assertTrue(backend.isLoggedIn)
    }

    @Test
    fun a_failing_refresh_server_is_reported_as_a_server_failure() = runTest {
        val server = FakeServer().apply {
            validAccess = "access-expired"
            refreshStatus = HttpStatusCode.ServiceUnavailable
        }
        val backend = server.backend(InMemoryTokenStore(Tokens("access-1", "refresh-1")))

        val result = assertIs<ApiResult.Err>(backend.fetch())

        assertEquals(ApiFailure.Server(503), result.failure)
        assertTrue(backend.isLoggedIn, "更新サーバーの不調でログアウトしている")
    }

    @Test
    fun requests_that_expire_together_refresh_only_once() = runTest {
        val server = FakeServer(StandardTestDispatcher(testScheduler)).apply {
            validAccess = "access-expired"
            expiredRequestsToHold = 2
        }
        val backend = server.backend(InMemoryTokenStore(Tokens("access-1", "refresh-1")))

        val first = async { backend.fetch() }
        val second = async { backend.fetch() }

        assertIs<ApiResult.Ok<ProductList>>(first.await())
        assertIs<ApiResult.Ok<ProductList>>(second.await())
        assertEquals(listOf("refresh-1"), server.refreshedWith, "同時に切れた分だけ更新している")
    }

    @Test
    fun logging_out_stops_sending_the_token() = runTest {
        val server = FakeServer()
        val store = InMemoryTokenStore()
        val backend = server.backend(store)

        backend.login("emilys", "emilyspass")
        backend.logout()
        val result = assertIs<ApiResult.Err>(backend.fetch())

        assertEquals(ApiFailure.Unauthorized, result.failure)
        assertNull(store.tokens)
        assertTrue(server.authorizations.all { it == null }, "ログアウト後もトークンを送っている")
        assertTrue(server.refreshedWith.isEmpty())
    }

    @Test
    fun a_request_without_logging_in_is_unauthorized_without_refreshing() = runTest {
        val server = FakeServer()
        val backend = server.backend(InMemoryTokenStore())

        val result = assertIs<ApiResult.Err>(backend.fetch())

        assertEquals(ApiFailure.Unauthorized, result.failure)
        assertTrue(server.refreshedWith.isEmpty())
    }

    @Test
    fun tokens_saved_before_a_restart_are_used() = runTest {
        val server = FakeServer()
        val backend = server.backend(InMemoryTokenStore(Tokens("access-1", "refresh-1")))

        assertTrue(backend.isLoggedIn)
        assertIs<ApiResult.Ok<ProductList>>(backend.fetch())
        assertEquals(listOf<String?>("Bearer access-1"), server.authorizations)
    }
}
