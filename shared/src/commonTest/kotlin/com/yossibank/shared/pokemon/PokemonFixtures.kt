package com.yossibank.shared.pokemon

import kotlin.test.assertIs
import kotlin.test.assertNull

internal const val TEST_BASE_URL = "https://example.test"

internal fun summaryUrl(id: Int) = "$TEST_BASE_URL/api/v2/pokemon/$id/"

internal fun pageJson(
    ids: List<Int>,
    next: String?,
    idless: Set<Int> = emptySet(),
    count: Int = 1302,
): String {
    val results = ids.joinToString(",") {
        val url = if (it in idless) "$TEST_BASE_URL/api/v2/pokemon/" else summaryUrl(it)
        """{"name":"p$it","url":"$url"}"""
    }
    val nextValue = next?.let { "\"$it\"" } ?: "null"
    return """{"count":$count,"next":$nextValue,"previous":null,"results":[$results]}"""
}

internal fun assertLoaded(
    result: PokemonListResult,
    message: String? = null,
): PokemonListResult.Loaded = assertIs<PokemonListResult.Loaded>(result, message).also {
    assertNull(it.failure, message ?: "失敗を連れた結果になっている")
}
