package com.yossibank.shared.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val TEST_BASE_URL = "https://example.test"

internal class InMemoryTokenStore(
    var tokens: Tokens? = null,
) : TokenStore {
    override fun load() = tokens

    override fun save(tokens: Tokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}

internal class FakeServer(
    dispatcher: CoroutineDispatcher? = null,
) {
    var validAccess = "access-1"
    var loginStatus = HttpStatusCode.OK
    var refreshStatus = HttpStatusCode.OK
    var refreshUnreachable = false
    var expiredRequestsToHold = 0
    var refreshToHold: CompletableDeferred<Unit>? = null
    val refreshArrived = CompletableDeferred<Unit>()

    val refreshedWith = mutableListOf<String>()
    val authorizations = mutableListOf<String?>()

    var productsBody: (skip: Int, limit: Int) -> String = { _, _ -> """{"products":[],"total":0,"skip":0,"limit":0}""" }

    private var issued = 1
    private var held = 0
    private val release = CompletableDeferred<Unit>()

    val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher?.let { this.dispatcher = it }
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/auth/login" -> login()
                    "/auth/refresh" -> refresh(bodyField(request.body as TextContent, "refreshToken"))
                    else -> products(
                        authorization = request.headers[HttpHeaders.Authorization],
                        skip = request.url.parameters["skip"]?.toInt() ?: 0,
                        limit = request.url.parameters["limit"]?.toInt() ?: 0,
                    )
                }
            }
        },
    )

    fun backend(store: TokenStore) = Backend(TEST_BASE_URL, store, engine)

    private fun MockRequestHandleScope.login(): HttpResponseData = if (loginStatus == HttpStatusCode.OK) {
        json(tokens(access = "access-1", refresh = "refresh-1"))
    } else {
        json("""{"message":"Invalid credentials"}""", loginStatus)
    }

    private suspend fun MockRequestHandleScope.refresh(refreshToken: String): HttpResponseData {
        refreshedWith += refreshToken

        check(!refreshUnreachable) { "connection refused" }

        refreshArrived.complete(Unit)
        refreshToHold?.await()

        if (refreshStatus != HttpStatusCode.OK) {
            return json("""{"message":"Invalid refresh token"}""", refreshStatus)
        }

        issued += 1
        validAccess = "access-$issued"
        return json(tokens(access = validAccess, refresh = "refresh-$issued"))
    }

    private suspend fun MockRequestHandleScope.products(
        authorization: String?,
        skip: Int,
        limit: Int,
    ): HttpResponseData {
        authorizations += authorization

        if (authorization == "Bearer $validAccess") {
            return json(productsBody(skip, limit))
        }

        if (authorization != null && held < expiredRequestsToHold) {
            held += 1
            if (held == expiredRequestsToHold) release.complete(Unit)
            release.await()
        }

        return json("""{"message":"Invalid/Expired Token!"}""", HttpStatusCode.Unauthorized)
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun tokens(
        access: String,
        refresh: String,
    ) = """{"id":1,"username":"emilys","accessToken":"$access","refreshToken":"$refresh"}"""

    private fun bodyField(
        body: TextContent,
        name: String,
    ) = Json
        .parseToJsonElement(body.text)
        .jsonObject
        .getValue(name)
        .jsonPrimitive.content
}
