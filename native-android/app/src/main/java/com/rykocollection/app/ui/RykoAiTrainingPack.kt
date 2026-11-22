package com.rykocollection.app.ui

/**
 * Compact, on-device specialist instruction pack.
 *
 * These are synthetic fan-question patterns, not copied social posts or a factual
 * manga database. The pack teaches response behaviour and is selected per question
 * so the small local model receives only a few relevant examples.
 */
internal object RykoAiTrainingPack {
    private data class Pattern(
        val tags: Set<String>,
        val question: String,
        val responseGuide: String
    )

    private val patterns = listOf(
        Pattern(setOf("title", "metadata"), "What is [title]? Is it manga, manhwa or manhua?",
            "Identify the exact work first. Give alternate titles, origin/format, author and artist only when verified. If multiple works share the name, ask which one."),
        Pattern(setOf("plot", "summary"), "Explain the story without spoiling the ending.",
            "Give the premise, protagonist, central conflict and tone. Respect the requested spoiler level; do not reveal later twists."),
        Pattern(setOf("ending", "spoiler"), "I finished [title]. Explain the ending and what it means.",
            "Warn that the answer contains spoilers. Separate events explicitly shown from interpretation. Do not invent an epilogue."),
        Pattern(setOf("character", "identity"), "Who is [character] and which series are they from?",
            "Confirm the series before describing the character. Include role and relevant traits; distinguish same-name characters."),
        Pattern(setOf("power", "battle"), "Could [character A] beat [character B]?",
            "State versions, forms and assumptions. Compare demonstrated feats, abilities, limits and matchup conditions. Label speculation; avoid declaring fan opinion as canon."),
        Pattern(setOf("power", "ranking"), "Rank the strongest characters in [series].",
            "Define the ranking criterion and story point. Use demonstrated feats and explain close placements; note uncertainty and avoid fabricated official rankings."),
        Pattern(setOf("chapter", "episode"), "Which chapter/episode does [event] happen in?",
            "Give an exact number only when a reliable source confirms it. Mention edition/platform differences; otherwise say it is unverified and offer a way to narrow it down."),
        Pattern(setOf("adaptation", "chapter"), "Where should I start the manga after the anime?",
            "Verify the anime's adaptation endpoint and whether it skipped or rearranged material. Give a chapter range only when supported; recommend starting earlier if continuity differs."),
        Pattern(setOf("recommendation", "taste"), "Recommend manhwa like [title], with a smart and funny MC.",
            "Extract the requested traits. Return distinct matches, explain the specific similarity and flag differences; do not list titles just because they are popular."),
        Pattern(setOf("recommendation", "romance"), "Suggest completed romance manhwa with a bittersweet ending.",
            "Prioritise completion and ending tone. Do not spoil the ending unless asked; clearly mark uncertain completion or ending information."),
        Pattern(setOf("recommendation", "filter"), "Give me 10 action manga with academy setting and student MC.",
            "Apply every constraint: medium, genre, academy setting, student protagonist and count. If fewer verified matches exist, say so rather than padding."),
        Pattern(setOf("genre", "tropes"), "Is this enemies-to-lovers, fake dating or friends-to-lovers?",
            "Classify using the actual relationship progression. Explain briefly and distinguish a temporary trope from the central romance."),
        Pattern(setOf("relationship", "character"), "Do these two characters end up together?",
            "Answer according to the requested canon point and spoiler preference. Distinguish canon, implication, adaptation-only changes and fan theories."),
        Pattern(setOf("theory", "mystery"), "What is the real meaning behind this scene?",
            "Offer the strongest text-supported interpretation first, then plausible alternatives. Cite scene/context in prose and label theories clearly."),
        Pattern(setOf("continuity", "timeline"), "Explain the timeline and the order I should watch/read it.",
            "Separate release order from chronological order. Include only confirmed entries and identify optional, recap or non-canon material."),
        Pattern(setOf("status", "release"), "Is [title] completed? When is the next chapter?",
            "Use current catalogue or official publisher information for status and dates. State timezone/platform caveats; never guess a release schedule."),
        Pattern(setOf("news", "release"), "What are the latest anime and manga announcements?",
            "Retrieve current news from dated, attributable sources. Summarise each item with date and source; distinguish rumours from official announcements."),
        Pattern(setOf("creator", "metadata"), "Who wrote and illustrated [title]?",
            "Keep story writer, artist, original author and adaptation studio roles distinct. If credits differ by edition, explain the discrepancy."),
        Pattern(setOf("comparison", "opinion"), "Which is better, the anime or manga?",
            "Compare pacing, art/animation, adaptation changes, soundtrack and completeness. Make the recommendation conditional on reader preference."),
        Pattern(setOf("translation", "title"), "What is the Korean/Japanese/Chinese title and its romanization?",
            "Give native title and romanization only when supported. Distinguish official English title from fan-translated alternatives."),
        Pattern(setOf("social", "discussion"), "Why are fans arguing about this character online?",
            "Summarise the main positions fairly. Do not claim to have read live posts unless current sources were retrieved; separate recurring opinions from verified facts."),
        Pattern(setOf("social", "recommendation"), "What are people recommending lately in this genre?",
            "Treat this as a current-trend question. Retrieve recent discussions or lists, show dates/platforms where available and avoid presenting a few posts as consensus."),
        Pattern(setOf("definition", "fandom"), "What does peak fiction / fraud / aura farming mean in anime discussions?",
            "Explain the fandom slang in context, note that usage varies by community and give a short original example without mocking users."),
        Pattern(setOf("spoiler", "comparison"), "Who had the saddest backstory in [series]?",
            "Define the comparison as subjective. Use story evidence, avoid graphic detail and warn before revealing major plot information."),
        Pattern(setOf("correction", "uncertainty"), "You gave me the wrong chapter number. Check again.",
            "Acknowledge the possible error, re-check an attributable source, correct the claim plainly and do not defend an unsupported previous answer."),
        Pattern(setOf("ambiguous", "title"), "Tell me about The Beginning.",
            "Ask which work or request one identifying detail. Never silently choose the most popular title."),
        Pattern(setOf("multilingual", "language"), "Bhai ye manhwa ka plot simple Hinglish me samjha.",
            "Reply naturally in Hinglish, retain official names and terminology, and explain difficult terms simply without changing the plot."),
        Pattern(setOf("format", "multi-part"), "Tell me the author, status, chapters, genre and main characters.",
            "Answer every requested field in the same order. Mark unavailable fields as not verified instead of omitting or inventing them."),
        Pattern(setOf("source", "fact-check"), "Is this viral anime announcement real?",
            "Check the official publisher, studio or platform first. State what is confirmed, what is rumour and when the information was checked."),
        Pattern(setOf("recommendation", "avoid"), "Recommend something like this but no harem and no tragic ending.",
            "Treat exclusions as hard filters. Do not include a title with a known excluded trope or ending just to fill the list.")
    )

    private val tagRules = linkedMapOf(
        "chapter" to Regex("\\b(chapter|chapters|episode|episodes|which ep|which chapter|start reading|after the anime)\\b", RegexOption.IGNORE_CASE),
        "ending" to Regex("\\b(ending|endings|finale|final chapter|spoiler|dies|survive)\\b", RegexOption.IGNORE_CASE),
        "character" to Regex("\\b(character|who is|who's|belongs to|from which|backstory|waifu|villain)\\b", RegexOption.IGNORE_CASE),
        "power" to Regex("\\b(strongest|stronger|power scale|powerscaling|beat |vs\\b|versus|feats|rank)\\b", RegexOption.IGNORE_CASE),
        "recommendation" to Regex("\\b(recommend|recommendations|similar|like this|suggest|top \\d+|best |underrated|completed)\\b", RegexOption.IGNORE_CASE),
        "romance" to Regex("\\b(romance|romantic|couple|ship|dating|relationship|love interest|bittersweet)\\b", RegexOption.IGNORE_CASE),
        "news" to Regex("\\b(latest|news|announcement|announced|release date|upcoming|trailer|season \\d+)\\b", RegexOption.IGNORE_CASE),
        "theory" to Regex("\\b(theory|meaning|symbolism|foreshadow|mystery|why did|what does .* mean)\\b", RegexOption.IGNORE_CASE),
        "metadata" to Regex("\\b(author|artist|writer|status|completed|chapters|volumes|genre|format|korean title|japanese title)\\b", RegexOption.IGNORE_CASE),
        "social" to Regex("\\b(fans|fandom|reddit|twitter|x.com|tiktok|online|trending|viral|controversy|arguing|people saying)\\b", RegexOption.IGNORE_CASE),
        "adaptation" to Regex("\\b(adaptation|adapted|anime|manga|watch order|read order|season)\\b", RegexOption.IGNORE_CASE),
        "comparison" to Regex("\\b(which is better|compare|difference|versus|vs\\b)\\b", RegexOption.IGNORE_CASE),
        "multilingual" to Regex("\\b(hinglish|hindi|bhai|simple words|explain simply)\\b", RegexOption.IGNORE_CASE),
        "ambiguous" to Regex("\\b(this title|that one|the beginning|tell me about it)\\b", RegexOption.IGNORE_CASE)
    )

    fun contextFor(prompt: String): String {
        val tags = tagRules.filterValues { it.containsMatchIn(prompt) }.keys
        val selected = patterns
            .map { pattern -> pattern to pattern.tags.count { it in tags } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        if (selected.isEmpty()) return ""
        return buildString {
            append("SPECIALIST RESPONSE EXAMPLES (behaviour patterns; do not treat placeholders as facts):\n")
            selected.forEachIndexed { index, pattern ->
                append(index + 1).append(") Fan question: ").append(pattern.question)
                    .append("\nResponse method: ").append(pattern.responseGuide).append('\n')
            }
            append("Apply the relevant method to the user's exact question. Verify factual details with retrieved sources; examples are not evidence.")
        }
    }

    fun guidedPrompt(prompt: String): String {
        val context = contextFor(prompt)
        return if (context.isBlank()) prompt else "$context\n\nUSER QUESTION: $prompt"
    }
}
