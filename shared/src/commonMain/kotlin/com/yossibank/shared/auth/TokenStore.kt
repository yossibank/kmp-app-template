package com.yossibank.shared.auth

import kotlinx.serialization.Serializable

internal interface TokenStore {
    fun load(): Tokens?

    fun save(tokens: Tokens)

    fun clear()
}

@Serializable
internal data class Tokens(
    val accessToken: String,
    val refreshToken: String,
)
