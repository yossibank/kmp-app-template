package com.yossibank.shared

internal const val TEST_BASE_URL = "https://example.test"

internal fun detailJson(
    id: Int,
    name: String = "p$id",
    sprite: String? = "https://img.test/$id.png",
    artwork: String? = "https://img.test/artwork/$id.png",
    types: List<String> = listOf("grass", "poison"),
    stats: List<Pair<String, Int>> = listOf("hp" to 45, "attack" to 49),
    height: Int? = 7,
    weight: Int? = 69,
): String {
    val spriteValue = sprite?.let { "\"$it\"" } ?: "null"
    val artworkBlock = artwork
        ?.let { ""","other":{"official-artwork":{"front_default":"$it"}}""" }
        ?: ""
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
          "past_abilities": [
            {
              "generation": { "name": "generation-iv", "url": "$TEST_BASE_URL/api/v2/generation/4/" },
              "abilities": [{ "is_hidden": true, "slot": 3, "ability": null }]
            }
          ],
          "forms": [],
          "game_indices": [],
          "held_items": [
            {
              "item": { "name": "oran-berry", "url": "$TEST_BASE_URL/api/v2/item/132/" },
              "version_details": [
                { "rarity": 50, "version": { "name": "ruby", "url": "$TEST_BASE_URL/api/v2/version/7/" } }
              ]
            }
          ],
          "location_area_encounters": "",
          "species": { "name": "$name", "url": "$TEST_BASE_URL/api/v2/pokemon-species/$id/" },
          "sprites": { "front_default": $spriteValue$artworkBlock },
          "cries": { "latest": null, "legacy": null },
          "stats": [${statItems.joinToString(",")}],
          "past_stats": [],
          "types": [${typeItems.joinToString(",")}],
          "past_types": [
            {
              "generation": { "name": "generation-v", "url": "$TEST_BASE_URL/api/v2/generation/5/" },
              "types": [
                { "slot": 1, "type": { "name": "normal", "url": "$TEST_BASE_URL/api/v2/type/1/" } }
              ]
            }
          ],
          "height": ${height ?: "null"},
          "weight": ${weight ?: "null"}
        }
        """.trimIndent()
}

internal fun summaryUrl(id: Int) = "$TEST_BASE_URL/api/v2/pokemon/$id/"

internal fun pageJson(
    ids: List<Int>,
    next: String?,
    idless: Set<Int> = emptySet(),
): String {
    val results = ids.joinToString(",") {
        val url = if (it in idless) "$TEST_BASE_URL/api/v2/pokemon/" else summaryUrl(it)
        """{"name":"p$it","url":"$url"}"""
    }
    val nextValue = next?.let { "\"$it\"" } ?: "null"
    return """{"count":1302,"next":$nextValue,"previous":null,"results":[$results]}"""
}
