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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

internal data class RykoAiMessage(
    val text: String,
    val fromUser: Boolean,
    val results: List<AniListMedia> = emptyList()
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
    val error: String? = null
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

    fun ensureReady() {
        if (_state.value.ready || _state.value.downloading || _state.value.initializing) return
        loadJob?.cancel()
        _state.value = _state.value.copy(downloading = true, downloadProgress = 0f, error = null)
        loadJob = downloadScope.launch {
            try {
                val modelFile = RykoAiModelDownloader.ensureModel(getApplication()) { progress ->
                    _state.value = _state.value.copy(downloading = true, downloadProgress = progress, error = null)
                }
                _state.value = _state.value.copy(downloading = false, initializing = true, error = null)
                engineScope.launch {
                    try {
                        if (engine == null) {
                            val candidate = Engine(
                                EngineConfig(
                                    modelPath = modelFile.absolutePath,
                                    backend = Backend.CPU(),
                                    cacheDir = getApplication<Application>().cacheDir.absolutePath
                                )
                            )
                            candidate.initialize()
                            engine = candidate
                            conversation = candidate.createConversation(
                                ConversationConfig(
                                    systemInstruction = Contents.of(
                                        "You are Ryko AI, a friendly anime and manga companion inside RykoCollection. " +
                                            "Answer clearly and naturally. Help with anime, manga, manhwa, manhua, genres, " +
                                            "story explanations, and recommendations. Never invent catalogue facts or claim " +
                                            "a title has a verified AniList page unless the app provides that result. " +
                                            "If unsure, say so. Match the user's language and keep answers concise unless asked for detail."
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
                        _state.value = _state.value.copy(initializing = false, ready = false, error = error.message ?: "Could not start the on-device model.")
                    }
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(downloading = false, initializing = false, ready = false, error = error.message ?: "Model download failed.")
            }
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
        engineScope.launch {
            try {
                val activeConversation = checkNotNull(conversation) { "The model is not ready." }
                activeConversation.sendMessageAsync(prompt).collect { chunk ->
                    val current = _state.value.messages
                    if (current.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            messages = current.dropLast(1) + current.last().copy(text = current.last().text + chunk.toString())
                        )
                    }
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: "The model could not answer.")
            } finally {
                _state.value = _state.value.copy(sending = false)
            }
        }
    }

    fun addCatalogueResults(prompt: String, results: List<AniListMedia>) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage(
                if (results.isEmpty()) "No matching titles were found in AniList."
                else "Verified AniList matches for “$prompt”:",
                false,
                results
            )
        )
    }

    fun reportCatalogueError() {
        _state.value = _state.value.copy(
            messages = _state.value.messages + RykoAiMessage("AniList search needs an internet connection. The on-device chat can still work offline.", false)
        )
    }

    fun releaseModel() {
        engineScope.launch {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
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
