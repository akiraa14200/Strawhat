package com.rykocollection.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RykoAiQueryRouterTest {
    @Test
    fun generalQuestionsStayGeneralInMangaMode() {
        val route = RykoAiQueryRouter.route("Hello, can you explain how power systems work?", mangaMode = true)
        assertEquals(RykoAiIntent.GENERAL, route.intent)
        assertEquals(RykoAiMedium.UNKNOWN, route.medium)
    }

    @Test
    fun powerScalingDoesNotBecomeTitleSearch() {
        val route = RykoAiQueryRouter.route("Who would win, Gojo vs Sukuna?", mangaMode = false)
        assertEquals(RykoAiIntent.POWER_SCALING, route.intent)
    }

    @Test
    fun chapterQuestionsAreKeptForSpecialistAnswering() {
        val route = RykoAiQueryRouter.route("Which chapter does Jinwoo fight Beru?", mangaMode = true)
        assertEquals(RykoAiIntent.CHAPTER_LOOKUP, route.intent)
    }

    @Test
    fun manhuaAndManhwaRemainDistinct() {
        assertEquals(RykoAiMedium.MANHUA, RykoAiQueryRouter.route("Recommend manhua", false).medium)
        assertEquals(RykoAiMedium.MANHWA, RykoAiQueryRouter.route("Recommend manhwa", false).medium)
    }

    @Test
    fun titleWordsAreNotAggressivelyRemoved() {
        val query = RykoAiQueryRouter.cleanCatalogueQuery("Tell me about The Story of My Life")
        assertEquals("The Story of My Life", query)
    }


    @Test
    fun tellMeAboutTitleUsesEntityLookup() {
        val route = RykoAiQueryRouter.route("Tell me about Maybe Meant to Be", mangaMode = false)
        assertEquals(RykoAiIntent.ENTITY_LOOKUP, route.intent)
        assertEquals("Maybe Meant to Be", route.query)
    }

    @Test
    fun opinionQuestionIsNotMistakenForTitleDetails() {
        val route = RykoAiQueryRouter.route("What do you think about One Piece?", mangaMode = true)
        assertEquals(RykoAiIntent.GENERAL, route.intent)
    }

    @Test
    fun mangaModeDefaultsOnlyForCatalogueIntent() {
        val title = RykoAiQueryRouter.route("Complete details about Maybe Meant to Be", true)
        assertEquals(RykoAiIntent.DETAILS, title.intent)
        assertEquals(RykoAiMedium.MANGA, title.medium)
        assertTrue(title.query.contains("Maybe Meant to Be"))
    }
}
