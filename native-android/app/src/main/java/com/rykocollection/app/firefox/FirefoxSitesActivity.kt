package com.rykocollection.app.firefox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rykocollection.app.ui.theme.RykoTheme
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

private const val SITE_PREFS = "firefox_sites"
private const val SITE_LIST_KEY = "saved_sites"

private data class SavedSite(val id: String, val name: String, val url: String)

private object GeckoRuntimeHolder {
    @Volatile private var instance: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        instance ?: synchronized(this) {
            instance ?: GeckoRuntime.create(context.applicationContext).also { instance = it }
        }
}

class FirefoxSitesActivity : ComponentActivity() {
    private var geckoSession: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 8, 8)
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 8)

        val session = GeckoSession()
        session.setContentDelegate(object : GeckoSession.ContentDelegate {})
        session.open(GeckoRuntimeHolder.get(this))
        geckoSession = session

        setContent {
            RykoTheme {
                FirefoxSitesScreen(session = session, onExit = { finish() })
            }
        }
    }

    override fun onDestroy() {
        geckoSession?.close()
        geckoSession = null
        super.onDestroy()
    }
}

@Composable
private fun FirefoxSitesScreen(session: GeckoSession, onExit: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(SITE_PREFS, Context.MODE_PRIVATE)
    }
    val sites = remember {
        mutableStateListOf<SavedSite>().apply {
            addAll(readSavedSites(preferences.getString(SITE_LIST_KEY, null)))
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<SavedSite?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf("") }
    var siteUrl by remember { mutableStateOf("") }

    fun persistSites() {
        val json = JSONArray()
        sites.forEach { item ->
            json.put(JSONObject().put("id", item.id).put("name", item.name).put("url", item.url))
        }
        preferences.edit().putString(SITE_LIST_KEY, json.toString()).apply()
    }

    fun openSite(url: String) {
        currentUrl = url
        session.loadUri(url)
        showBrowser = true
    }

    fun saveSite() {
        val name = siteName.trim()
        val normalizedUrl = normalizeWebsite(siteUrl)
        if (name.isBlank() || normalizedUrl == null) {
            Toast.makeText(context, "Enter a site name and a valid HTTP or HTTPS address", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = editingSite
        if (existing == null) {
            sites.add(SavedSite(System.currentTimeMillis().toString(), name, normalizedUrl))
        } else {
            val index = sites.indexOfFirst { it.id == existing.id }
            if (index >= 0) sites[index] = existing.copy(name = name, url = normalizedUrl)
        }
        persistSites()
        showAddDialog = false
        editingSite = null
        siteName = ""
        siteUrl = ""
    }

    BackHandler(enabled = showBrowser) { showBrowser = false }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF08090D)) {
        if (showBrowser) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF11131A)).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showBrowser = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to sites", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Firefox Sites", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(currentUrl, color = Color(0xFFB8BBC5), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { openInFirefox(context, currentUrl) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Firefox", tint = Color.White)
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext -> GeckoView(viewContext).apply { setSession(session) } },
                    update = { it.setSession(session) }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().background(Color(0xFF08090D)).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FIREFOX SITES", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                        Text("Your websites, one tap away", color = Color(0xFF9DA3B2), fontSize = 12.sp)
                    }
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171A23))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(Color(0xFF5B4BCE), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Powered by GeckoView", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Mozilla's Firefox browser engine, embedded in this app", color = Color(0xFFADB2C0), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR SITES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        editingSite = null
                        siteName = ""
                        siteUrl = ""
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Add site")
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (sites.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF737B8C), modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No websites added yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Add a website to create your first shortcut", color = Color(0xFF9299A8), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sites, key = { it.id }) { site ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF171A23))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { openSite(site.url) }
                                        .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(40.dp).background(Color(0xFF252A38), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFFB9B0FF))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(site.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text(site.url, color = Color(0xFF9DA3B2), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = {
                                        editingSite = site
                                        siteName = site.name
                                        siteUrl = site.url
                                        showAddDialog = true
                                    }) {
                                        Text("Edit", color = Color(0xFFC6C0FF), fontSize = 11.sp)
                                    }
                                    IconButton(onClick = {
                                        sites.removeAll { it.id == site.id }
                                        persistSites()
                                    }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete website", tint = Color(0xFFE59A9A))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    "Sites are saved on this device. GeckoView does not share Firefox's signed-in sync profile.",
                    color = Color(0xFF737B8C),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingSite = null },
            title = { Text(if (editingSite == null) "Add website" else "Edit website") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = siteName, onValueChange = { siteName = it }, label = { Text("Site name") }, singleLine = true)
                    OutlinedTextField(
                        value = siteUrl,
                        onValueChange = { siteUrl = it },
                        label = { Text("Website address") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { saveSite() }) { Text("Save") } },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; editingSite = null }) { Text("Cancel") }
            }
        )
    }
}

private fun readSavedSites(raw: String?): List<SavedSite> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.optString("id")
                val name = item.optString("name")
                val url = normalizeWebsite(item.optString("url"))
                if (id.isNotBlank() && name.isNotBlank() && url != null) add(SavedSite(id, name, url))
            }
        }
    }.getOrDefault(emptyList())
}

private fun normalizeWebsite(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
    return uri.toString()
}

private fun openInFirefox(context: Context, url: String) {
    val uri = Uri.parse(url)
    val firefoxIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("org.mozilla.firefox") }
    runCatching {
        context.startActivity(firefoxIntent)
    }.onFailure {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open website with")
        runCatching { context.startActivity(chooser) }.onFailure {
            Toast.makeText(context, "No browser is available to open this site", Toast.LENGTH_SHORT).show()
        }
    }
}
