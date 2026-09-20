package com.yossibank.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal const val TEST_BASE_URL = "https://example.test"

internal fun detailJson(
    id: Int,
    name: String = "p$id",
    sprite: String? = "https://img.test/$id.png",
    types: List<String> = listOf("grass", "poison"),
    stats: List<Pair<String, Int>> = listOf("hp" to 45, "attack" to 49),
    height: Int? = 7,
    weight: Int? = 69,
): String {
    val spriteValue = sprite?.let { "\"$it\"" } ?: "null"
    val typeItems = types.mapIndexed { index, type ->
        """{"slot":${index + 1},"type":{"name":"$type","url":"$TEST_BASE_URL/api/v2/type/$type/"}}"""
    }
    val statItems = stats.map { (statName, base) ->
        """{"base_stat":$base,"effort":0,"stat":{"name":"$statName","url":"$TEST_BASE_URL/api/v2/stat/$statName/"}}"""
    }

    return """
        {
          "id": $id,
          "name": "$name",
          "abilities": [],
          "past_abilities": [],
          "forms": [],
          "game_indices": [],
          "held_items": [],
          "location_area_encounters": "",
          "moves": [],
          "species": { "name": "$name", "url": "$TEST_BASE_URL/api/v2/pokemon-species/$id/" },
          "sprites": { "front_default": $spriteValue },
          "cries": { "latest": null, "legacy": null },
          "stats": [${statItems.joinToString(",")}],
          "past_stats": [],
          "types": [${typeItems.joinToString(",")}],
          "past_types": [],
          "height": ${height ?: "null"},
          "weight": ${weight ?: "null"}
        }
        """.trimIndent()
}

internal fun speciesJson(
    id: Int,
    japanese: String? = "にほんご$id",
): String {
    val entries = buildList {
        add("""{"name":"p$id","language":{"name":"en"}}""")
        if (japanese != null) {
            add("""{"name":"$japanese","language":{"name":"ja-hrkt"}}""")
        }
    }
    return """{"names":[${entries.joinToString(",")}]}"""
}

internal fun summaryUrl(id: Int) = "$TEST_BASE_URL/api/v2/pokemon/$id/"

internal fun pageJson(
    ids: List<Int>,
    next: String?,
): String {
    val results = ids.joinToString(",") { """{"name":"p$it","url":"${summaryUrl(it)}"}""" }
    val nextValue = next?.let { "\"$it\"" } ?: "null"
    return """{"count":1302,"next":$nextValue,"previous":null,"results":[$results]}"""
}

internal fun testClient(engine: MockEngine) = HttpClient(engine) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}
