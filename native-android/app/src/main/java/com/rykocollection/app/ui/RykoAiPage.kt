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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { aiViewModel.ensureReady() }
    fun sendPrompt() {
        val prompt = input.trim()
        if (prompt.isEmpty() || !state.ready || state.sending) return
        input = ""
        keyboard?.hide()
        aiViewModel.send(prompt)
    }

    fun searchCatalogue() {
        val prompt = input.trim()
        if (prompt.isEmpty() || catalogueLoading) return
        catalogueLoading = true
        keyboard?.hide()
        scope.launch {
            val repository = AniListRepository()
            try {
                aiViewModel.addCatalogueResults(prompt, repository.searchMedia(prompt, mangaMode).take(6))
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
                Text("Private on-device anime & manga assistant", color = AiMuted, fontSize = 10.sp)
            }
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF24212F)) {
                Text("ON-DEVICE", color = AiViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, AiViolet.copy(alpha = .55f), Color.Transparent))))

        if (state.downloading) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Installing Ryko AI model · " + (state.downloadProgress * 100).toInt() + "%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth(), color = AiViolet, trackColor = Color(0xFF302D3A))
                Text("About 614 MB. This is a one-time download; it stays on this device.", color = AiMuted, fontSize = 10.sp)
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
                            Text(message.text, color = Color(0xFFF4F2FA), fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(14.dp))
                        }
                    }
                    message.results.forEach { media ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                                .clickable { uriHandler.openUri("https://anilist.co/" + (if (mangaMode) "manga/" else "anime/") + media.id) },
                            color = Color(0xFF17171C), shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(media.title.display, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(listOfNotNull(media.format, media.seasonYear?.toString(), media.averageScore?.let { "Score $it" }).joinToString(" · "), color = AiMuted, fontSize = 10.sp)
                                }
                                Text("AniList ↗", color = AiViolet, fontSize = 11.sp)
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
            IconButton(onClick = { sendPrompt() }, enabled = input.isNotBlank() && state.ready && !state.sending) {
                Icon(Icons.Default.Send, contentDescription = "Send message", tint = AiViolet)
            }
        }
    }
}
