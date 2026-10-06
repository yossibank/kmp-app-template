package com.yossibank.shared.auth

import com.yossibank.shared.core.ApiClient
import com.yossibank.shared.core.ApiFailure
import com.yossibank.shared.core.ApiResult
import com.yossibank.shared.core.RequestFailed
import com.yossibank.shared.core.httpClient
import com.yossibank.shared.generated.model.AuthTokens
import com.yossibank.shared.generated.model.LoginRequest
import com.yossibank.shared.generated.model.RefreshRequest
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.clearAuthTokens
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal class Backend(
    private val baseUrl: String,
    private val store: TokenStore,
    engine: HttpClientEngine? = null,
) {
    private val client = httpClient(engine) {
        install(Auth) {
            bearer {
                loadTokens { store.load()?.let { BearerTokens(it.accessToken, it.refreshToken) } }
                refreshTokens { refresh() }
                sendWithoutRequest { true }
            }
        }
    }

    val api = ApiClient(baseUrl, client)

    val isLoggedIn: Boolean
        get() = store.load() != null

    suspend fun login(
        username: String,
        password: String,
    ): LoginResult = when (
        val result = api.post<LoginRequest, AuthTokens>("/auth/login", LoginRequest(username, password))
    ) {
        is ApiResult.Ok -> {
            store.save(Tokens(result.value.accessToken, result.value.refreshToken))
            client.clearAuthTokens()
            LoginResult.LoggedIn
        }

        is ApiResult.Err -> {
            val failure = result.failure
            if (failure == ApiFailure.Server(400)) LoginResult.Rejected else LoginResult.Failed(failure)
        }
    }

    fun logout() {
        store.clear()
        client.clearAuthTokens()
    }

    private suspend fun RefreshTokensParams.refresh(): BearerTokens? {
        val refreshToken = oldTokens?.refreshToken ?: return null

        val response = client.post("$baseUrl/auth/refresh") {
            markAsRefreshTokenRequest()
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }

        if (response.status in sessionEnded) {
            if (isCurrent(refreshToken)) store.clear()
            return null
        }

        if (!response.status.isSuccess()) {
            throw RequestFailed(ApiFailure.Server(response.status.value))
        }

        val tokens = try {
            response.body<AuthTokens>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RequestFailed(ApiFailure.Unreadable)
        }

        if (!isCurrent(refreshToken)) return null

        store.save(Tokens(tokens.accessToken, tokens.refreshToken))
        return BearerTokens(tokens.accessToken, tokens.refreshToken)
    }

    private fun isCurrent(refreshToken: String) = store.load()?.refreshToken == refreshToken

    private companion object {
        val sessionEnded = setOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
        )
    }
}
