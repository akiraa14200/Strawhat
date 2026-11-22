package com.rykocollection.app.ui

/**
 * Lightweight deterministic routing for common anime/manga requests.
 * It classifies specialist questions before catalogue lookup so questions
 * about battles, chapters, theories and fandom discussion are not mistaken
 * for title-search queries. It never attempts to answer factual questions.
 */
internal enum class RykoAiIntent {
    CHARACTER, CHAPTER_LOOKUP, POWER_SCALING, COMPARISON,
    THEORY, SOCIAL_DISCUSSION, ENTITY_LOOKUP, DETAILS,
    RECOMMENDATIONS, TITLE_SEARCH, NEWS, GENERAL
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

    private val mediumPatterns = listOf(
        RykoAiMedium.MANHWA to Regex("\\bmanhwa\\b", RegexOption.IGNORE_CASE),
        RykoAiMedium.MANHUA to Regex("\\bmanhua\\b", RegexOption.IGNORE_CASE),
        RykoAiMedium.WEBTOON to Regex("\\bwebtoon\\b", RegexOption.IGNORE_CASE),
        RykoAiMedium.MANGA to Regex("\\bmanga\\b", RegexOption.IGNORE_CASE),
        RykoAiMedium.ANIME to Regex("\\banime\\b", RegexOption.IGNORE_CASE)
    )

    fun route(raw: String, mangaMode: Boolean): RykoAiRoute {
        val text = raw.trim()
        val normalized = text.lowercase()
        val explicitMedium = mediumPatterns.firstOrNull { it.second.containsMatchIn(text) }?.first
        val genre = genres.firstOrNull { normalized.contains(it.lowercase()) }
        val count = Regex("\\b(?:top|best)\\s*(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 5

        val news = Regex("\\b(news|latest updates|announcements|trailer news|industry news)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val chapter = Regex("\\b(chapter|chapters|episode|episodes|which ep|read after|start reading|adaptation endpoint)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val power = Regex("\\b(strongest|stronger|power.?scal(?:e|ing)|who would win|who wins|beat |vs\\b|versus|feats|rank)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val comparison = Regex("\\b(compare|comparison|difference between|which is better|anime vs|manga vs|adaptation changes)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val social = Regex("\\b(fans|fandom|reddit|twitter|x\\.com|tiktok|online|trending|viral|controversy|arguing|people saying|community opinion)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val theory = Regex("\\b(theory|meaning|symbolism|foreshadow|mystery|why did|why does|what does .* mean|explain the scene)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val character = Regex("\\b(who is|who's|character|characters|powers of|abilities of|belongs to|from which (anime|manga|series))\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val recommendation = Regex("\\b(recommend|recommendations|suggest|suggestions|similar to|like (this|it)|top\\s*\\d+|best|underrated|must read|must watch)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val details = Regex("\\b(details?|story|plot|synopsis|summary|about|information|volumes?|status|author|artist|release date|published|completed|ending|adaptation)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

        val looksLikeStandaloneEntity = text.matches(
            Regex("^[A-Z][\\p{L}’'-]+(?:\\s+[A-Z][\\p{L}’'-]+){1,5}$")
        ) && normalized.split(Regex("\\s+")).firstOrNull() !in setOf("i", "the", "what", "who", "why", "how")

        val intent = when {
            news -> RykoAiIntent.NEWS
            chapter -> RykoAiIntent.CHAPTER_LOOKUP
            power -> RykoAiIntent.POWER_SCALING
            comparison -> RykoAiIntent.COMPARISON
            social -> RykoAiIntent.SOCIAL_DISCUSSION
            theory -> RykoAiIntent.THEORY
            character -> RykoAiIntent.CHARACTER
            recommendation -> RykoAiIntent.RECOMMENDATIONS
            details -> RykoAiIntent.DETAILS
            looksLikeStandaloneEntity -> RykoAiIntent.ENTITY_LOOKUP
            explicitMedium != null -> RykoAiIntent.TITLE_SEARCH
            else -> RykoAiIntent.GENERAL
        }

        // The page's manga tab is only a default for catalogue-oriented requests.
        // It must not turn greetings, general questions or debate prompts into title searches.
        val medium = explicitMedium ?: if (
            mangaMode && intent in setOf(
                RykoAiIntent.CHARACTER, RykoAiIntent.ENTITY_LOOKUP, RykoAiIntent.DETAILS,
                RykoAiIntent.RECOMMENDATIONS, RykoAiIntent.TITLE_SEARCH
            )
        ) RykoAiMedium.MANGA else RykoAiMedium.UNKNOWN

        val query = cleanCatalogueQuery(text)
        return RykoAiRoute(intent, medium, query, genre, count)
    }

    /**
     * Remove request framing, not ordinary title words. In particular, words
     * such as "the", "story", "ending", "is", "it" and "been" can be title text.
     */
    internal fun cleanCatalogueQuery(raw: String): String = raw
        .replace(
            Regex("(?i)^\\s*(?:please\\s+)?(?:tell me about|tell me|give me|show me|find|search for|search|look up)\\s+"),
            ""
        )
        .replace(
            Regex("(?i)^\\s*(?:what is|what's|who is|who's|complete details about|details about|details of|information about|about|the plot of|the story of)\\s+"),
            ""
        )
        .replace(Regex("[?!.:,;]+$"), "")
        .replace(Regex("(?i)\\s+(?:anime|manga|manhwa|manhua|webtoon)$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
