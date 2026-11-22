package com.rykocollection.app.ui

/**
 * Lightweight deterministic routing for common anime/manga requests.
 * This runs before local inference and does not require another model.
 */
internal enum class RykoAiIntent {
    CHARACTER, DETAILS, RECOMMENDATIONS, TITLE_SEARCH, NEWS, GENERAL
}

internal enum class RykoAiMedium { ANIME, MANGA, MANHWA, MANHUA, WEBTOON, UNKNOWN }

internal data class RykoAiRoute(
    val intent: RykoAiIntent,
    val medium: RykoAiMedium,
    val query: String,
    val genre: String? = null,
    val requestedCount: Int = 5
) {
    val isManga: Boolean get() = medium in setOf(
        RykoAiMedium.MANGA, RykoAiMedium.MANHWA, RykoAiMedium.MANHUA, RykoAiMedium.WEBTOON
    )
}

internal object RykoAiQueryRouter {
    private val genres = listOf(
        "Action", "Romance", "Comedy", "Fantasy", "Adventure",
        "Horror", "Thriller", "Mystery", "Isekai", "Sports"
    )

    fun route(raw: String, mangaMode: Boolean): RykoAiRoute {
        val text = raw.trim()
        val normalized = text.lowercase()
        val medium = when {
            Regex("\\b(manhwa)\\b").containsMatchIn(normalized) -> RykoAiMedium.MANHWA
            Regex("\\b(manhua)\\b").containsMatchIn(normalized) -> RykoAiMedium.MANHUA
            Regex("\\b(webtoon)\\b").containsMatchIn(normalized) -> RykoAiMedium.WEBTOON
            Regex("\\b(manga)\\b").containsMatchIn(normalized) -> RykoAiMedium.MANGA
            Regex("\\b(anime)\\b").containsMatchIn(normalized) -> RykoAiMedium.ANIME
            mangaMode -> RykoAiMedium.MANGA
            else -> RykoAiMedium.UNKNOWN
        }
        val genre = genres.firstOrNull { normalized.contains(it.lowercase()) }
        val count = Regex("\\btop\\s*(\\d{1,2})\\b").find(normalized)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 5

        val news = Regex("\\b(news|latest updates|announcements|trailer news|industry news)\\b").containsMatchIn(normalized)
        val character = Regex("\\b(who is|who's|character|characters|powers of|abilities of|belongs to|from which (anime|manga|series))\\b")
            .containsMatchIn(normalized)
        val recommendation = Regex("\\b(recommend|recommendations|suggest|suggestions|similar to|like (this|it)|top\\s*\\d+|best|underrated|must read|must watch)\\b")
            .containsMatchIn(normalized)
        val details = Regex("\\b(details?|story|plot|synopsis|summary|about|information|chapters?|volumes?|status|author|artist|release date|published|completed|ending|adaptation|episodes?)\\b")
            .containsMatchIn(normalized)

        val intent = when {
            news -> RykoAiIntent.NEWS
            character -> RykoAiIntent.CHARACTER
            recommendation -> RykoAiIntent.RECOMMENDATIONS
            details -> RykoAiIntent.DETAILS
            medium != RykoAiMedium.UNKNOWN -> RykoAiIntent.TITLE_SEARCH
            else -> RykoAiIntent.GENERAL
        }

        val query = text
            .replace(Regex("(?i)\\b(anime|manga|manhwa|manhua|webtoon|search|find|please|title|titles|details?|story|plot|synopsis|summary|about|information|chapters?|volumes?|status|author|artist|release date|published|completed|ending|adaptation|episodes?|recommendations?|recommend|suggestions?|suggest|top\\s*\\d+|best|underrated|must read|must watch|who is|who's|character|characters|powers of|abilities of|belongs to|from which anime|from which manga|series|latest|news|updates|announcements)\\b"), " ")
            .replace(Regex("(?i)\\b(action|romance|comedy|fantasy|adventure|horror|thriller|mystery|isekai|sports)\\b"), " ")
            .replace(Regex("[?!.:,;]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return RykoAiRoute(intent, medium, query, genre, count)
    }
}
