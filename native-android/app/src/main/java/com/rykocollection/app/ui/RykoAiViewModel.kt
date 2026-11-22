package com.rykocollection.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.rykocollection.app.data.AniListMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

internal data class RykoAiMessage(
    val text: String,
    val fromUser: Boolean,
    val results: List<AniListMedia> = emptyList(),
    val resultManga: Boolean? = null
)

internal data class RykoAiUiState(
    val messages: List<RykoAiMessage> = listOf(
        RykoAiMessage("Hi! I’m Ryko AI. I run on your device, so once the model is installed you can chat without an internet connection. You can also search the live AniList catalogue.", false)
    ),
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val initializing: Boolean = false,
    val ready: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val selectedModelId: String = "qwen3.5-4b-mixed-int4",
    val installedModelIds: Set<String> = emptySet(),
    val downloadingModelId: String? = null
)

internal class RykoAiViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(RykoAiUiState())
    val state: StateFlow<RykoAiUiState> = _state.asStateFlow()

    private val engineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engineScope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadJob: Job? = null
    private var generationJob: Job? = null
    private var loadedModelId: String? = null

    init {
        val prefs = getApplication<Application>().getSharedPreferences("ryko_ai", 0)
        val saved = prefs.getString("selected_model_id", "qwen3.5-4b-mixed-int4") ?: "qwen3.5-4b-mixed-int4"
        // Upgrade users who were still on the old default; explicit model choices remain intact.
        val migrated = if (saved == "qwen3-0.6b-int4") "qwen3.5-4b-mixed-int4" else saved
        val selected = RykoAiModelDownloader.models.firstOrNull { it.id == migrated }?.id ?: "qwen3.5-4b-mixed-int4"
        if (selected != saved) prefs.edit().putString("selected_model_id", selected).apply()
        _state.value = _state.value.copy(selectedModelId = selected, installedModelIds = RykoAiModelDownloader.models.filter { RykoAiModelDownloader.hasModelFile(getApplication(), it.id) }.map { it.id }.toSet())
    }

    fun ensureReady() {
        if (_state.value.ready || _state.value.downloading || _state.value.initializing) return
        loadJob?.cancel()
        _state.value = _state.value.copy(downloading = true, downloadProgress = 0f, error = null)
        loadJob = downloadScope.launch {
            try {
                val modelId = _state.value.selectedModelId
                _state.value = _state.value.copy(downloadingModelId = modelId)
                val modelFile = RykoAiModelDownloader.ensureModel(getApplication(), modelId) { progress ->
                    _state.value = _state.value.copy(downloading = true, downloadProgress = progress, downloadingModelId = modelId, error = null)
                }
                _state.value = _state.value.copy(installedModelIds = _state.value.installedModelIds + modelId, downloadingModelId = null)
                _state.value = _state.value.copy(downloading = false, initializing = true, error = null)
                engineScope.launch {
                    try {
                        if (engine == null || loadedModelId != modelId) {
                            runCatching { conversation?.close() }
                            conversation = null
                            runCatching { engine?.close() }
                            engine = null
                            val cachePath = getApplication<Application>().cacheDir.absolutePath
                            val modelSpec = RykoAiModelDownloader.models.first { it.id == modelId }
                            // Qwen3.5 4B Mixed INT4 is intentionally CPU-only on 8 GB phones:
                            // its GPU path can exceed the available memory budget. Smaller models
                            // still try GPU first and safely fall back to CPU.
                            val candidate = if (!modelSpec.useGpu) {
                                Engine(
                                    EngineConfig(
                                        modelPath = modelFile.absolutePath,
                                        backend = Backend.CPU(),
                                        cacheDir = cachePath
                                    )
                                ).also { it.initialize() }
                            } else try {
                                val gpuEngine = Engine(
                                    EngineConfig(
                                        modelPath = modelFile.absolutePath,
                                        backend = Backend.GPU(),
                                        cacheDir = cachePath
                                    )
                                )
                                try {
                                    gpuEngine.initialize()
                                    gpuEngine
                                } catch (gpuError: Exception) {
                                    runCatching { gpuEngine.close() }
                                    throw gpuError
                                }
                            } catch (_: Exception) {
                                Engine(
                                    EngineConfig(
                                        modelPath = modelFile.absolutePath,
                                        backend = Backend.CPU(),
                                        cacheDir = cachePath
                                    )
                                ).also { it.initialize() }
                            }
                            engine = candidate
                            loadedModelId = modelId
                            conversation = candidate.createConversation(
                                ConversationConfig(
                                    systemInstruction = Contents.of(
                                        "You are Ryko AI, the specialist anime, manga, manhwa and manhua assistant inside RykoCollection. " +
                                            "First understand exactly what the user is asking; do not answer a different or merely related question. " +
                                            "Preserve exact title names, character names, spelling, dates and requested constraints. " +
                                            "If a request has multiple parts, answer every part in order. If the question is ambiguous, ask one short clarification instead of guessing. " +
                                            "Separate verified facts from interpretation and clearly say when a fact cannot be verified. " +
                                            "Never invent chapters, release dates, character relationships, plot events, scores or catalogue links. " +
                                            "For recommendations, explain briefly why each pick matches the user's stated preferences. " +
                                            "Match the user's language (including natural Hinglish when used), give useful detail, and avoid generic filler."
                                    )
                                )
                            )
                        }
                        _state.value = _state.value.copy(initializing = false, ready = true, error = null)
                    } catch (error: Exception) {
                        runCatching { conversation?.close() }
                        conversation = null
                        runCatching { engine?.close() }
                        engine = null
                        loadedModelId = null
                        _state.value = _state.value.copy(initializing = false, ready = false, error = error.message ?: "Could not start the on-device model.")
                    }
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(downloading = false, initializing = false, ready = false, downloadingModelId = null, error = error.message ?: "Model download failed.")
            }
        }
    }

    fun downloadModel(modelId: String) {
        if (modelId == _state.value.selectedModelId) { ensureReady(); return }
        if (_state.value.downloading || _state.value.initializing) return
        downloadScope.launch {
            try {
                _state.value = _state.value.copy(downloading = true, downloadProgress = 0f, downloadingModelId = modelId, error = null)
                RykoAiModelDownloader.download(getApplication(), modelId) { progress ->
                    _state.value = _state.value.copy(downloadProgress = progress, downloading = true, downloadingModelId = modelId)
                }
                _state.value = _state.value.copy(downloading = false, downloadingModelId = null, installedModelIds = _state.value.installedModelIds + modelId)
            } catch (error: Exception) {
                _state.value = _state.value.copy(downloading = false, downloadingModelId = null, error = error.message ?: "Model download failed.")
            }
        }
    }

    fun selectModel(modelId: String) {
        if (RykoAiModelDownloader.models.none { it.id == modelId }) return
        if (_state.value.downloading || _state.value.initializing || _state.value.sending) return
        getApplication<Application>().getSharedPreferences("ryko_ai", 0).edit().putString("selected_model_id", modelId).apply()
        _state.value = _state.value.copy(selectedModelId = modelId, ready = false, error = null)
        loadJob?.cancel()
        engineScope.launch {
            runCatching { conversation?.close() }; conversation = null
            runCatching { engine?.close() }; engine = null; loadedModelId = null
            ensureReady()
        }
    }
    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || !_state.value.ready || _state.value.sending) return
        val placeholder = RykoAiMessage("", false)
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage(prompt, true) + placeholder,
            sending = true,
            error = null
        )
        generationJob = engineScope.launch {
            try {
                val activeConversation = checkNotNull(conversation) { "The model is not ready." }
                // Accumulate tokens off the UI state and publish at a capped cadence.
                // Updating StateFlow for every tiny token causes excessive Compose recomposition
                // and repeated String/list allocations on slower phones.
                val response = StringBuilder()
                var lastPublishedAt = 0L
                activeConversation.sendMessageAsync(prompt).collect { chunk ->
                    response.append(chunk.toString())
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastPublishedAt >= 50L) {
                        publishStreamingResponse(response.toString())
                        lastPublishedAt = now
                    }
                }
                // Always publish the final tail, including short answers that never hit 50 ms. 
                publishStreamingResponse(response.toString())
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: "The model could not answer.")
            } finally {
                _state.value = _state.value.copy(sending = false)
            }
        }
    }

    private fun publishStreamingResponse(text: String) {
        val current = _state.value.messages
        if (current.isNotEmpty()) {
            _state.value = _state.value.copy(
                messages = current.dropLast(1) + current.last().copy(text = text)
            )
        }
    }

    fun attachCatalogueResults(results: List<AniListMedia>) {
        val updated = _state.value.messages.toMutableList()
        val index = updated.indexOfLast { !it.fromUser }
        if (index >= 0) {
            val knownIds = updated[index].results.map { it.id }.toSet()
            updated[index] = updated[index].copy(results = updated[index].results + results.filterNot { it.id in knownIds })
            _state.value = _state.value.copy(messages = updated)
        }
    }

    fun addRecommendationResults(prompt: String, results: List<AniListMedia>, manga: Boolean, genre: String? = null, displayLabel: String? = null) {
        val category = displayLabel ?: if (manga) "manga" else "anime"
        val intro = when {
            results.isEmpty() -> "I couldn't find matching $category titles in AniList. Try another genre or search term."
            genre != null -> "Here are ${results.size} popular $genre $category picks from AniList:"
            else -> "Here are ${results.size} popular $category picks from AniList:"
        }
        _state.value = _state.value.copy(messages = _state.value.messages + RykoAiMessage(prompt, true) + RykoAiMessage(intro, false, results, manga))
    }

    fun addCatalogueResults(prompt: String, results: List<AniListMedia>, manga: Boolean? = null) {
        val sources = results.mapNotNull { it.sourceLabel }.distinct()
        val sourceIntro = when {
            sources.isEmpty() -> "AniList"
            sources.size == 1 -> sources.first()
            else -> sources.joinToString(" and ")
        }
        val intro = when {
            results.isEmpty() -> "No matching titles were found in the available catalogues. Try the alternate title or spelling."
            else -> "Matches from $sourceIntro for “$prompt”:"
        }
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage(prompt, true) + RykoAiMessage(
                intro,
                false,
                results,
                manga
            )
        )
    }

    fun addMediaDetails(prompt: String, media: AniListMedia, detail: com.rykocollection.app.data.AniListDetail, manga: Boolean) {
        val description = detail.description
            ?.replace(Regex("<[^>]*>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(900)
            ?.takeIf { it.isNotBlank() } ?: "AniList has no synopsis available for this title."
        val facts = listOfNotNull(
            detail.format,
            detail.seasonYear?.toString(),
            detail.status,
            detail.chapters?.let { "$it chapters" },
            detail.volumes?.let { "$it volumes" },
            detail.averageScore?.let { "Score $it/100" },
            detail.countryOfOrigin
        ).joinToString(" · ")
        val answer = "${media.title.display}\n$facts\n\n$description"
        _state.value = _state.value.copy(messages = _state.value.messages + RykoAiMessage(prompt, true) + RykoAiMessage(answer, false, listOf(media), manga))
    }

    fun addCharacterResults(prompt: String, characters: List<com.rykocollection.app.data.AniListCharacter>) {
        val answer = when {
            characters.isEmpty() -> "I couldn't verify a matching character in the available catalogue. Try the character's alternate spelling or tell me the series."
            characters.size > 1 -> "I found multiple possible character matches. Here are the closest catalogue matches:\n" +
                characters.take(5).joinToString("\n") { "• ${it.name.full}" } +
                "\nTell me which series you mean so I don't give you information about the wrong character."
            else -> {
                val character = characters.first()
                val description = character.description
                    ?.replace(Regex("<[^>]*>"), " ")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.take(850)
                    ?.takeIf { it.isNotBlank() }
                    ?: "The catalogue does not provide a character description."
                buildString {
                    append(character.name.full)
                    append("\n\n")
                    append(description)
                    append("\n\nSource: AniList")
                    character.siteUrl?.takeIf { it.startsWith("https://") }?.let { append("\n").append(it) }
                }
            }
        }
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage(prompt, true) + RykoAiMessage(answer, false)
        )
    }

    fun addNewsUnavailable(prompt: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage(prompt, true) +
                RykoAiMessage("I don't have a verified live-news result for that request yet, so I won't invent a headline. Open Ryko Newsroom for current stories from their original publishers.", false)
        )
    }

    fun reportCatalogueError() {
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage("AniList search needs an internet connection. The on-device chat can still work offline.", false)
        )
    }

    fun stopGeneration() {
        runCatching { conversation?.cancelProcess() }
        generationJob?.cancel()
        generationJob = null
        _state.value = _state.value.copy(sending = false)
    }

    fun releaseModel() {
        engineScope.launch {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            loadedModelId = null
            _state.value = _state.value.copy(ready = false, initializing = false, sending = false)
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        engineScope.launch {
            try {
                conversation?.close()
                conversation = null
                engine?.close()
                engine = null
            } finally {
                engineDispatcher.close()
                downloadScope.cancel()
                engineScope.cancel()
            }
        }
        super.onCleared()
    }
}
