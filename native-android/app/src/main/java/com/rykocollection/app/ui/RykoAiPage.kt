package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rykocollection.app.data.AniListRepository
import kotlinx.coroutines.launch

private val AiViolet = Color(0xFFA99BFF)
private val AiMuted = Color(0xFFAAAAB5)

@Composable
internal fun RykoAiFloatingButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(58.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFE4DEFF), AiViolet, Color(0xFF7664D8))))
            .border(1.dp, Color.White.copy(alpha = 0.62f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = "Open Ryko AI", tint = Color(0xFF171321), modifier = Modifier.size(27.dp))
    }
}

@Composable
internal fun RykoAiPage(
    mangaMode: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    aiViewModel: RykoAiViewModel = viewModel()
) {
    val state by aiViewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var catalogueLoading by remember { mutableStateOf(false) }
    var showModelManager by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { aiViewModel.ensureReady() }
    fun sendPrompt() {
        val prompt = input.trim()
        if (prompt.isEmpty() || !state.ready || state.sending || catalogueLoading) return
        input = ""
        keyboard?.hide()
        val route = RykoAiQueryRouter.route(prompt, mangaMode)
        val requestedMangaMode = if (route.medium == RykoAiMedium.UNKNOWN) mangaMode else route.isManga
        val requestedManhwa = route.medium == RykoAiMedium.MANHWA || route.medium == RykoAiMedium.WEBTOON

        when (route.intent) {
            RykoAiIntent.CHARACTER -> {
                if (route.query.isBlank()) {
                    aiViewModel.send(prompt)
                    return
                }
                catalogueLoading = true
                scope.launch {
                    val repository = AniListRepository()
                    try {
                        val characters = repository.searchCharacters(route.query)
                        aiViewModel.addCharacterResults(prompt, characters)
                    } catch (_: Exception) {
                        aiViewModel.addCharacterResults(prompt, emptyList())
                    } finally {
                        repository.close()
                        catalogueLoading = false
                    }
                }
                return
            }
            RykoAiIntent.DETAILS, RykoAiIntent.TITLE_SEARCH, RykoAiIntent.ENTITY_LOOKUP -> {
                if (route.query.isBlank()) {
                    aiViewModel.send(prompt)
                    return
                }
                catalogueLoading = true
                scope.launch {
                    val repository = AniListRepository()
                    try {
                        val matches = repository.searchMedia(route.query, requestedMangaMode, requestedManhwa)
                        if (matches.isNotEmpty()) {
                            val match = matches.first()
                            val detail = repository.getMedia(match.id)
                            if (detail != null) aiViewModel.addMediaDetails(prompt, match, detail, requestedMangaMode)
                            else aiViewModel.addCatalogueResults(prompt, matches.take(5), requestedMangaMode)
                        } else {
                            val secondary = com.rykocollection.app.data.JikanRepository()
                            try {
                                val externalMatches = secondary.searchMedia(route.query, requestedMangaMode, requestedManhwa)
                                if (externalMatches.isNotEmpty()) {
                                    aiViewModel.addCatalogueResults(prompt, externalMatches.take(5), requestedMangaMode)
                                } else if (route.intent == RykoAiIntent.ENTITY_LOOKUP || route.intent == RykoAiIntent.DETAILS) {
                                    aiViewModel.addCharacterResults(prompt, repository.searchCharacters(route.query))
                                } else {
                                    aiViewModel.addCatalogueResults(prompt, emptyList(), requestedMangaMode)
                                }
                            } finally { secondary.close() }
                        }
                    } catch (_: Exception) {
                        aiViewModel.reportCatalogueError()
                    } finally {
                        repository.close()
                        catalogueLoading = false
                    }
                }
                return
            }
            RykoAiIntent.RECOMMENDATIONS -> {
                val requestedLabel = when (route.medium) {
                    RykoAiMedium.MANHWA -> "manhwa"
                    RykoAiMedium.MANHUA -> "manhua"
                    RykoAiMedium.WEBTOON -> "webtoon"
                    RykoAiMedium.MANGA -> "manga"
                    RykoAiMedium.ANIME -> "anime"
                    RykoAiMedium.UNKNOWN -> if (mangaMode) "manga" else "anime"
                }
                catalogueLoading = true
                scope.launch {
                    val repository = AniListRepository()
                    try {
                        val results = repository.recommendMedia(
                            manga = requestedMangaMode,
                            genre = route.genre,
                            limit = route.requestedCount,
                            manhwa = requestedManhwa
                        )
                        aiViewModel.addRecommendationResults(prompt, results, requestedMangaMode, route.genre, requestedLabel)
                    } catch (_: Exception) {
                        aiViewModel.addCatalogueResults(prompt, emptyList(), requestedMangaMode)
                        aiViewModel.reportCatalogueError()
                    } finally {
                        repository.close()
                        catalogueLoading = false
                    }
                }
                return
            }
            RykoAiIntent.NEWS -> aiViewModel.addNewsUnavailable(prompt)
            in setOf(
                RykoAiIntent.CHAPTER_LOOKUP,
                RykoAiIntent.POWER_SCALING,
                RykoAiIntent.COMPARISON,
                RykoAiIntent.THEORY,
                RykoAiIntent.SOCIAL_DISCUSSION,
                RykoAiIntent.GENERAL
            ) -> Unit
        }
        aiViewModel.send(prompt)
    }

    fun searchCatalogue() {
        val prompt = input.trim()
        if (prompt.isEmpty() || catalogueLoading) return
        val normalized = prompt.lowercase()
        val searchManga = when {
            Regex("\\b(manga|manhwa|manhua|webtoon)\\b").containsMatchIn(normalized) -> true
            Regex("\\banime\\b").containsMatchIn(normalized) -> false
            else -> mangaMode
        }
        val searchManhwa = Regex("\\b(manhwa|webtoon)\\b").containsMatchIn(normalized)
        val searchGenre = listOf("Action", "Romance", "Comedy", "Fantasy", "Adventure", "Horror", "Thriller", "Mystery", "Isekai", "Sports")
            .firstOrNull { normalized.contains(it.lowercase()) }
        val searchTerm = prompt
            .replace(Regex("(?i)\\b(anime|manga|manhwa|manhua|webtoon|search|find|please|title|titles|best|top|popular|good|recommendations?|list|show me|give me)\\b"), " ")
            .replace(Regex("(?i)\\b(action|romance|comedy|fantasy|adventure|horror|thriller|mystery|isekai|sports)\\b"), " ")
            .replace(Regex("\\b\\d+\\b"), " ")
            .trim()
        catalogueLoading = true
        keyboard?.hide()
        scope.launch {
            val repository = AniListRepository()
            try {
                if (searchTerm.isBlank()) {
                    val label = when {
                        normalized.contains("manhwa") -> "manhwa"
                        normalized.contains("manhua") -> "manhua"
                        normalized.contains("webtoon") -> "webtoon"
                        searchManga -> "manga"
                        else -> "anime"
                    }
                    val results = repository.recommendMedia(searchManga, searchGenre, 5, searchManhwa)
                    aiViewModel.addRecommendationResults(prompt, results, searchManga, searchGenre, label)
                } else {
                    val primary = repository.searchMedia(searchTerm, searchManga, searchManhwa)
                    if (primary.isNotEmpty()) {
                        aiViewModel.addCatalogueResults(prompt, primary.take(6), searchManga)
                    } else {
                        val secondary = com.rykocollection.app.data.JikanRepository()
                        try {
                            val external = secondary.searchMedia(searchTerm, searchManga, searchManhwa)
                            aiViewModel.addCatalogueResults(prompt, external.take(6), searchManga)
                        } finally { secondary.close() }
                    }
                }
            } catch (_: Exception) {
                aiViewModel.reportCatalogueError()
            } finally {
                repository.close()
                catalogueLoading = false
            }
        }
    }

    Column(modifier.fillMaxSize().background(Color(0xFF08080B)).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Close Ryko AI", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Ryko AI", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Lightweight on-device AI · live catalogue search", color = AiMuted, fontSize = 10.sp)
            }
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF24212F), modifier = Modifier.clickable { showModelManager = !showModelManager }) {
                Text("MODELS", color = AiViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, AiViolet.copy(alpha = .55f), Color.Transparent))))

        if (showModelManager) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LIGHTWEIGHT ON-DEVICE MODEL", color = AiViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                RykoAiModelDownloader.models.forEach { model ->
                    val installed = model.id in state.installedModelIds
                    val selected = model.id == state.selectedModelId
                    Surface(color = Color(0xFF17171C), shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AiViolet.copy(alpha = .55f) else Color.White.copy(alpha = .06f))) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(model.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(model.description, color = AiMuted, fontSize = 9.sp)
                                Text(if (installed) "Downloaded on this device" else "Approx. " + (model.estimatedBytes / (1024L * 1024L)) + " MB download", color = AiMuted, fontSize = 9.sp)
                            }
                            val action = when { selected && state.ready -> "ACTIVE"; selected -> "SELECTED"; installed -> "USE"; else -> "DOWNLOAD" }
                            Text(action, color = AiViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !state.downloading && !state.initializing && !state.sending && action != "ACTIVE" && action != "SELECTED") {
                                if (installed) aiViewModel.selectModel(model.id) else aiViewModel.downloadModel(model.id)
                            }.padding(7.dp))
                        }
                    }
                }
                Text("The compact model runs locally; catalogue lookups use public online sources.", color = AiMuted, fontSize = 9.sp)
            }
        }
        if (state.downloading) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Downloading " + (RykoAiModelDownloader.models.firstOrNull { it.id == state.downloadingModelId }?.name ?: "Ryko AI model") + " · " + (state.downloadProgress * 100).toInt() + "%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth(), color = AiViolet, trackColor = Color(0xFF302D3A))
                Text("One-time download · under 500 MB estimated.", color = AiMuted, fontSize = 10.sp)
            }
        } else if (state.initializing) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = AiViolet, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Preparing the on-device model…", color = AiMuted, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)
        ) {
            items(state.messages) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
                    if (message.text.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(18.dp), color = if (message.fromUser) Color(0xFF302A48) else Color(0xFF17171C)) {
                            Text(message.text.replace("**", "").replace("*", ""), color = Color(0xFFF4F2FA), fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(14.dp))
                        }
                    }
                    message.results.forEach { media ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                                .clickable { uriHandler.openUri(media.externalUrl ?: ("https://anilist.co/" + (if (message.resultManga ?: mangaMode) "manga/" else "anime/") + media.id)) },
                            color = Color(0xFF17171C), shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                AsyncImage(
                                    model = media.coverImage?.imageUrl,
                                    contentDescription = media.title.display,
                                    modifier = Modifier.width(64.dp).height(88.dp).clip(RoundedCornerShape(9.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(media.title.display, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(listOfNotNull(media.format, media.seasonYear?.toString(), media.averageScore?.let { "Score $it" }).joinToString(" · "), color = AiMuted, fontSize = 10.sp)
                                    Text(if (media.externalUrl != null) "Open MyAnimeList entry ↗" else "Open AniList entry ↗", color = AiViolet, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (state.sending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        CircularProgressIndicator(color = AiViolet, modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                        Text("Ryko AI is thinking…", color = AiMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        state.error?.let { error ->
            Text(error, color = Color(0xFFFFB7B7), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp))
            Text("Retry model setup", color = AiViolet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp).clickable { aiViewModel.ensureReady() })
        }

        Row(Modifier.fillMaxWidth().background(Color(0xFF101014)).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (state.ready) "Ask Ryko AI anything…" else "Preparing model…", color = AiMuted) },
                shape = RoundedCornerShape(18.dp), singleLine = true, enabled = state.ready && !state.sending,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendPrompt() })
            )
            IconButton(onClick = { searchCatalogue() }, enabled = input.isNotBlank() && !catalogueLoading) {
                Icon(Icons.Default.Search, contentDescription = "Search AniList catalogue", tint = AiViolet)
            }
            if (state.sending) {
                IconButton(onClick = { aiViewModel.stopGeneration() }) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop response", tint = Color(0xFFFF9B9B))
                }
            } else {
                IconButton(onClick = { sendPrompt() }, enabled = input.isNotBlank() && state.ready) {
                    Icon(Icons.Default.Send, contentDescription = "Send message", tint = AiViolet)
                }
            }
        }
    }
}
