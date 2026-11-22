package com.rykocollection.app.safeguard

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.net.http.SslError
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rykocollection.app.ui.LocalRykoHazeState
import com.rykocollection.app.ui.rykoGlass
import com.rykocollection.app.ui.theme.RykoTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeguardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 8, 8)
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { RykoTheme { SafeguardPage(onExit = { finish() }) } }
    }
}

private enum class SafeguardTab(val title: String) {
    OVERVIEW("Overview"), ACTIVITY("Activity"), SETTINGS("Settings"), TRUSTED("Trusted")
}

private data class PendingNavigation(val url: String, val host: String, val reason: String)
private data class SafeguardEvent(val time: String, val host: String, val reason: String)
private data class CatalogueSite(val name: String, val url: String)

private const val SAFEGUARD_PREFS = "safeguard_local"
private const val ANIME_SHEET_URL = "https://docs.google.com/spreadsheets/d/1VLCJE_YxSQlPbjQojaFTy1ZzrmpitetsAXDgTUpbHzs/export?format=csv"
private const val MANGA_SHEET_URL = "https://docs.google.com/spreadsheets/d/1VLCJE_YxSQlPbjQojaFTy1ZzrmpitetsAXDgTUpbHzs/gviz/tq?tqx=out:csv&sheet=Sheet2"

@Composable
private fun SafeguardPage(onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SAFEGUARD_PREFS, Activity.MODE_PRIVATE) }
    val hazeState = rememberHazeState()
    var selectedTab by remember { mutableStateOf(SafeguardTab.OVERVIEW) }
    var protectionEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var browserVisible by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingNavigation by remember { mutableStateOf<PendingNavigation?>(null) }
    var showCatalogue by remember { mutableStateOf(false) }
    var catalogueCategory by remember { mutableStateOf("Anime") }
    var catalogueLoading by remember { mutableStateOf(false) }
    var catalogueError by remember { mutableStateOf<String?>(null) }
    var catalogueSites by remember { mutableStateOf<List<CatalogueSite>>(emptyList()) }
    var trustedInput by remember { mutableStateOf("") }
    var showTrustDialog by remember { mutableStateOf(false) }
    val trustedDomains = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("trusted_domains", emptySet()).orEmpty())
        }
    }
    val events = remember {
        mutableStateListOf<SafeguardEvent>().apply {
            addAll(
                prefs.getString("events", "").orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { row ->
                    val parts = row.split("\t", limit = 3)
                    if (parts.size == 3) SafeguardEvent(parts[0], parts[1], parts[2]) else null
                }
            )
        }
    }
    val enabledState by rememberUpdatedState(protectionEnabled)
    val trustedState by rememberUpdatedState(trustedDomains.toSet())
    val currentUrlState by rememberUpdatedState(currentUrl)
    val addEvent by rememberUpdatedState<(String, String) -> Unit> { host, reason ->
        val event = SafeguardEvent(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            host.ifBlank { "Unknown destination" },
            reason
        )
        events.add(0, event)
        while (events.size > 60) events.removeAt(events.lastIndex)
        prefs.edit().putString("events", events.joinToString("\n") { "${it.time}\t${it.host}\t${it.reason}" }).apply()
    }

    fun saveTrusted() {
        prefs.edit().putStringSet("trusted_domains", trustedDomains.toSet()).apply()
    }

    fun loadAddress(raw: String) {
        val normalized = NavigationPolicy.normalize(raw)
        if (normalized == null) {
            Toast.makeText(context, "Enter a valid website address", Toast.LENGTH_SHORT).show()
            return
        }
        address = normalized
        currentUrl = normalized
        browserVisible = true
        webView?.loadUrl(normalized)
    }

    fun loadCatalogue(category: String) {
        catalogueCategory = category
        catalogueLoading = true
        catalogueError = null
        val cachedKey = if (category == "Anime") "anime_csv" else "manga_csv"
        val sheetUrl = if (category == "Anime") ANIME_SHEET_URL else MANGA_SHEET_URL
        val cached = prefs.getString(cachedKey, null)
        if (!cached.isNullOrBlank()) catalogueSites = parseCatalogue(cached)
        Thread {
            try {
                val connection = (URL(sheetUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 9000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "RykoCollection-Safeguard/1.0")
                }
                val csv = try {
                    if (connection.responseCode !in 200..299) error("Catalogue returned HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally { connection.disconnect() }
                val parsed = parseCatalogue(csv)
                if (parsed.isEmpty()) error("No website entries were found in this sheet")
                prefs.edit().putString(cachedKey, csv).putLong("${cachedKey}_updated", System.currentTimeMillis()).apply()
                (context as? Activity)?.runOnUiThread {
                    if (catalogueCategory == category) {
                        catalogueSites = parsed
                        catalogueLoading = false
                    }
                }
            } catch (error: Exception) {
                (context as? Activity)?.runOnUiThread {
                    if (catalogueCategory == category) {
                        catalogueLoading = false
                        catalogueError = if (catalogueSites.isEmpty()) error.message ?: "Could not load the catalogue" else "Showing saved catalogue. Refresh failed."
                    }
                }
            }
        }.start()
    }

    BackHandler(enabled = true) {
        when {
            pendingNavigation != null -> pendingNavigation = null
            browserVisible && webView?.canGoBack() == true -> webView?.goBack()
            browserVisible -> browserVisible = false
            else -> onExit()
        }
    }

    CompositionLocalProvider(LocalRykoHazeState provides hazeState) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF080808)).hazeSource(hazeState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().rykoGlass(22.dp, Color(0xF01A1A1D), 0.42f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("SAFEGUARD", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                    Text("Browsing protection", color = Color(0xFFBDBDC2), fontSize = 11.sp)
                }
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Safeguard",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp).size(25.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().rykoGlass(20.dp, Color(0xD91A1A1D), 0.32f).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SafeguardTab.entries.forEach { item ->
                    val active = selectedTab == item
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (active) Color(0x32A99BFF) else Color.Transparent)
                            .clickable { selectedTab = item }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.title, color = if (active) Color.White else Color(0xFFBDBDC2), fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                SafeguardTab.OVERVIEW -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().rykoGlass(22.dp, Color(0xE31B1B1F), 0.40f).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (protectionEnabled) Color(0x332BCB91) else Color(0x33BDBDC2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = if (protectionEnabled) Color(0xFF68D7A3) else Color.LightGray, modifier = Modifier.size(25.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (protectionEnabled) "Protection is active" else "Protection is paused", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Balanced protection · Ask on uncertain redirects", color = Color(0xFFBDBDC2), fontSize = 11.sp)
                            }
                            Switch(
                                checked = protectionEnabled,
                                onCheckedChange = {
                                    protectionEnabled = it
                                    prefs.edit().putBoolean("enabled", it).apply()
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Blocked today", events.count { it.reason.contains("blocked", true) || it.reason.contains("ad", true) }.toString(), Modifier.weight(1f))
                            MetricCard("Activity", events.size.toString(), Modifier.weight(1f))
                        }
                        Row(
                            Modifier.fillMaxWidth().rykoGlass(20.dp, Color(0xD91A1A1D), 0.34f).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Website or search") },
                                placeholder = { Text("example.com") }
                            )
                            IconButton(onClick = { loadAddress(address) }) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open website", tint = Color.White)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { loadCatalogue("Anime"); showCatalogue = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Site catalogue", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { browserVisible = true; webView?.reload() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reopen browser", fontSize = 12.sp)
                            }
                        }
                        if (browserVisible) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(currentUrl.ifBlank { "Browser" }, color = Color(0xFFBDBDC2), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                TextButton(onClick = { browserVisible = false }) { Text("Close") }
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)),
                                factory = { viewContext ->
                                    WebView(viewContext).apply {
                                        webView = this
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            javaScriptCanOpenWindowsAutomatically = false
                                            setSupportMultipleWindows(false)
                                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                                        }
                                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                                val uri = request?.url ?: return null
                                                val host = NavigationPolicy.hostOf(uri.toString())
                                                if (enabledState && NavigationPolicy.isBlockedHost(host)) {
                                                    return WebResourceResponse("text/plain", "utf-8", 204, "Blocked by Safeguard", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                                                }
                                                return super.shouldInterceptRequest(view, request)
                                            }

                                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                val uri = request?.url ?: return true
                                                val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return true
                                                if (scheme !in setOf("http", "https")) return true
                                                if (!request.isForMainFrame) return false
                                                val decision = NavigationPolicy.decide(uri.toString(), currentUrlState, request.hasGesture(), trustedState, enabledState)
                                                when (decision.action) {
                                                    NavigationAction.ALLOW -> return false
                                                    NavigationAction.BLOCK -> {
                                                        addEvent(decision.host, "Blocked: ${decision.reason}")
                                                        return true
                                                    }
                                                    NavigationAction.ASK -> {
                                                        pendingNavigation = PendingNavigation(uri.toString(), decision.host, decision.reason)
                                                        addEvent(decision.host, "Confirmation required")
                                                        return true
                                                    }
                                                }
                                            }

                                            @Suppress("DEPRECATION")
                                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                                val safeUrl = NavigationPolicy.normalize(url.orEmpty()) ?: return true
                                                val decision = NavigationPolicy.decide(safeUrl, currentUrlState, false, trustedState, enabledState)
                                                when (decision.action) {
                                                    NavigationAction.ALLOW -> return false
                                                    NavigationAction.BLOCK -> { addEvent(decision.host, "Blocked: ${decision.reason}"); return true }
                                                    NavigationAction.ASK -> { pendingNavigation = PendingNavigation(safeUrl, decision.host, decision.reason); addEvent(decision.host, "Confirmation required"); return true }
                                                }
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                if (!url.isNullOrBlank()) {
                                                    currentUrl = url
                                                    address = url
                                                }
                                            }

                                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                                if (request?.isForMainFrame == true) addEvent(NavigationPolicy.hostOf(request.url.toString()), "Page load failed")
                                            }

                                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                                handler?.cancel()
                                                addEvent(NavigationPolicy.hostOf(error?.url), "Blocked invalid or untrusted TLS certificate")
                                            }

                                            @RequiresApi(Build.VERSION_CODES.O_MR1)
                                            override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
                                                callback?.backToSafety(true)
                                                addEvent(NavigationPolicy.hostOf(request?.url?.toString()), "Blocked by Android Safe Browsing")
                                            }
                                        }
                                    }
                                },
                                update = { webView = it }
                            )
                        } else {
                            Column(
                                Modifier.fillMaxWidth().weight(1f).rykoGlass(24.dp, Color(0xE31B1B1F), 0.34f).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Browse with a second look.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("Known ad destinations are filtered. Unexpected cross-site redirects ask before opening.", color = Color(0xFFBDBDC2), fontSize = 12.sp)
                            }
                        }
                    }
                }
                SafeguardTab.ACTIVITY -> {
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent activity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                events.clear()
                                prefs.edit().remove("events").apply()
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                        if (events.isEmpty()) EmptyState("No activity yet", "Redirect checks and blocked requests will appear here.")
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(events) { event ->
                                Row(Modifier.fillMaxWidth().rykoGlass(18.dp, Color(0xD91A1A1D), 0.28f).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(event.host, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(event.reason, color = Color(0xFFBDBDC2), fontSize = 11.sp)
                                    }
                                    Text(event.time, color = Color(0xFF9B9BA3), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                SafeguardTab.SETTINGS -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Protection settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        SettingRow("Balanced protection", "Filter known ad domains and ask before uncertain automatic redirects.", protectionEnabled) {
                            protectionEnabled = it
                            prefs.edit().putBoolean("enabled", it).apply()
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xD91A1A1D)), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(15.dp)) {
                                Text("What Safeguard checks", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(7.dp))
                                Text("• Known advertising and redirect domains\n• Unexpected top-level cross-site navigation\n• Android Safe Browsing warnings\n• Invalid TLS certificate errors", color = Color(0xFFBDBDC2), fontSize = 12.sp, lineHeight = 19.sp)
                            }
                        }
                        Text("Protection is best-effort. WebView does not expose every navigation type or every service-worker request to the same callback.", color = Color(0xFF9B9BA3), fontSize = 11.sp)
                    }
                }
                SafeguardTab.TRUSTED -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Trusted websites", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showTrustDialog = true }) { Text("＋ Add") }
                        }
                        Text("Trusted entries allow their domain and subdomains to navigate without a redirect prompt. Known blocked domains and Android Safe Browsing checks still apply.", color = Color(0xFFBDBDC2), fontSize = 11.sp)
                        if (trustedDomains.isEmpty()) EmptyState("No trusted websites", "Add a domain only when you recognise and trust it.")
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(trustedDomains.toList()) { domain ->
                                Row(Modifier.fillMaxWidth().rykoGlass(18.dp, Color(0xD91A1A1D), 0.28f).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF68D7A3))
                                    Spacer(Modifier.width(10.dp))
                                    Text(domain, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                    IconButton(onClick = { trustedDomains.remove(domain); saveTrusted() }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove trusted website", tint = Color(0xFFBDBDC2))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingNavigation != null) {
        val pending = pendingNavigation!!
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text("This website wants to redirect you") },
            text = {
                Column {
                    Text(pending.host, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(pending.reason)
                    Spacer(Modifier.height(8.dp))
                    Text("Only continue if you recognise this destination.", color = Color(0xFFBDBDC2), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingNavigation = null
                    webView?.loadUrl(pending.url)
                }) { Text("Allow once") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        trustedDomains.add(pending.host)
                        saveTrusted()
                        pendingNavigation = null
                        webView?.loadUrl(pending.url)
                    }) { Text("Trust site") }
                    TextButton(onClick = { pendingNavigation = null }) { Text("Block") }
                }
            }
        )
    }

    if (showTrustDialog) {
        AlertDialog(
            onDismissRequest = { showTrustDialog = false; trustedInput = "" },
            title = { Text("Trust a website") },
            text = {
                OutlinedTextField(value = trustedInput, onValueChange = { trustedInput = it }, singleLine = true, label = { Text("Domain") }, placeholder = { Text("example.com") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val domain = NavigationPolicy.hostOf(NavigationPolicy.normalize(trustedInput).orEmpty())
                    if (domain.isNotBlank() && !NavigationPolicy.isBlockedHost(domain) && domain !in trustedDomains) {
                        trustedDomains.add(domain)
                        saveTrusted()
                    }
                    trustedInput = ""
                    showTrustDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showTrustDialog = false; trustedInput = "" }) { Text("Cancel") } }
        )
    }

    if (showCatalogue) {
        AlertDialog(
            onDismissRequest = { showCatalogue = false },
            title = { Text("Anime & Manga catalogue") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Anime", "Manga").forEach { category ->
                            OutlinedButton(onClick = { loadCatalogue(category) }) { Text(category) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (catalogueLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    catalogueError?.let { Text(it, color = Color(0xFFBDBDC2), fontSize = 11.sp) }
                    LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(catalogueSites) { site ->
                            Text(
                                site.name,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showCatalogue = false
                                    selectedTab = SafeguardTab.OVERVIEW
                                    loadAddress(site.url)
                                }.padding(vertical = 9.dp)
                            )
                            Divider(color = Color(0x26FFFFFF))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { loadCatalogue(catalogueCategory) }) { Text("Refresh") } },
            dismissButton = { TextButton(onClick = { showCatalogue = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.rykoGlass(18.dp, Color(0xD91A1A1D), 0.30f).padding(14.dp)) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFBDBDC2), fontSize = 11.sp)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().rykoGlass(20.dp, Color(0xD91A1A1D), 0.28f).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFA99BFF), modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Color(0xFFBDBDC2), fontSize = 11.sp)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().rykoGlass(18.dp, Color(0xD91A1A1D), 0.30f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = Color(0xFFBDBDC2), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun parseCatalogue(csv: String): List<CatalogueSite> {
    fun parseRow(row: String): List<String> {
        val cells = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < row.length) {
            val char = row[index]
            when {
                char == '"' && quoted && index + 1 < row.length && row[index + 1] == '"' -> { value.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { cells.add(value.toString().trim()); value.clear() }
                else -> value.append(char)
            }
            index++
        }
        cells.add(value.toString().trim())
        return cells
    }
    return csv.lineSequence().drop(1).mapNotNull { line ->
        val cells = parseRow(line)
        if (cells.size < 2) return@mapNotNull null
        val name = cells[0].trim()
        val url = NavigationPolicy.normalize(cells[1].trim()) ?: return@mapNotNull null
        if (name.isBlank()) null else CatalogueSite(name, url)
    }.distinctBy { it.url }.toList()
}
