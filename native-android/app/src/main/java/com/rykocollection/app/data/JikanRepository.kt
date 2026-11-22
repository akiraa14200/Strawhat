package com.rykocollection.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Secondary catalogue adapter for Jikan (unofficial MyAnimeList data).
 * Results retain their external URL so callers never mistake a MAL ID for an AniList ID.
 */
class JikanRepository {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun searchMedia(
        term: String,
        manga: Boolean,
        manhwa: Boolean = false,
        limit: Int = 8
    ): List<AniListMedia> {
        val query = term.trim()
        if (query.isBlank()) return emptyList()
        val response: JsonObject = client.get("https://api.jikan.moe/v4/${if (manga) "manga" else "anime"}") {
            parameter("q", query)
            parameter("limit", limit.coerceIn(1, 10))
            parameter("sfw", true)
            if (manga && manhwa) parameter("type", "manhwa")
        }.body()
        return response["data"]?.jsonArray.orEmpty().mapNotNull { element ->
            runCatching { element.jsonObject.toAniListMedia(manga) }.getOrNull()
        }
    }

    private fun JsonObject.toAniListMedia(manga: Boolean): AniListMedia? {
        val malId = this["mal_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        val titleObject = this["titles"]?.jsonArray?.firstOrNull { item ->
            runCatching { item.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "English" }.getOrDefault(false)
        }?.jsonObject
        val english = titleObject?.get("title")?.jsonPrimitive?.contentOrNull
        val title = this["title"]?.jsonPrimitive?.contentOrNull
            ?: english
            ?: this["title_english"]?.jsonPrimitive?.contentOrNull
            ?: "Untitled"
        val images = this["images"]?.jsonObject?.get("jpg")?.jsonObject
        val cover = images?.get("large_image_url")?.jsonPrimitive?.contentOrNull
            ?: images?.get("image_url")?.jsonPrimitive?.contentOrNull
        val year = this["year"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val score = this["score"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.times(10)?.toInt()
        val format = this["type"]?.jsonPrimitive?.contentOrNull
        val chapters = this["chapters"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val episodes = this["episodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val status = this["status"]?.jsonPrimitive?.contentOrNull
        val url = this["url"]?.jsonPrimitive?.contentOrNull
        return AniListMedia(
            id = malId,
            title = AniListTitle(english = english, romaji = title),
            coverImage = cover?.let { AniListCover(large = it) },
            averageScore = score,
            format = format,
            seasonYear = year,
            episodes = episodes,
            chapters = chapters,
            status = status,
            countryOfOrigin = if (manga) null else null,
            externalUrl = url,
            sourceLabel = "MyAnimeList via Jikan"
        )
    }

    fun close() = client.close()
}
