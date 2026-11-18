package com.rykocollection.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NEWS_API = "https://ryko-84zbxiwqx-z-hub.vercel.app"
private val NewsAccent = Color(0xFFA99BFF)
private val NewsMuted = Color(0xFF96949F)
private val NewsSurface = Color(0xFF111115)

private data class NewsStory(val title:String,val link:String,val description:String,val date:String,val source:String,val category:String,val image:String?=null) {
    val time:Long get() = parseNewsTimestamp(date)
}
private fun parseNewsTimestamp(value:String):Long {
    if(value.isBlank()) return 0L
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { java.time.ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { java.time.LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }
        .getOrDefault(0L)
}
private object NewsCache { var stories:List<NewsStory> = emptyList(); var failed:List<String> = emptyList(); var loadedAt:Long = 0L }
private fun JsonObject.field(key:String)=this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun httpText(url:String):String {
    val c=URL(url).openConnection() as HttpURLConnection
    c.connectTimeout=12000; c.readTimeout=12000; c.setRequestProperty("Accept","application/json")
    return try { val code=c.responseCode; val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty(); check(code in 200..299){"News service HTTP "+code}; body } finally { c.disconnect() }
}
private suspend fun fetchStories():Pair<List<NewsStory>,List<String>> = withContext(Dispatchers.IO) {
    val root=Json.parseToJsonElement(httpText(NEWS_API+"/api/news-feed")).jsonObject
    val now=System.currentTimeMillis()
    val list=root["items"]?.jsonArray.orEmpty().mapNotNull { e -> runCatching {
        val o=e.jsonObject
        val title=o.field("title").replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ").trim()
        val link=o.field("link")
        val story=NewsStory(title,link,o.field("description").replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ").trim().take(360),o.field("pubDate"),o.field("source"),o.field("category"),o.field("thumbnail").takeIf{it.startsWith("http")})
        story.takeIf{it.title.isNotBlank() && it.link.startsWith("http") && it.time >= now-45L*86400000 && it.time <= now+86400000}
    }.getOrNull() }.distinctBy{it.link}.sortedByDescending{it.time}.take(100)
    list to root["failedSources"]?.jsonArray.orEmpty().mapNotNull{runCatching{it.jsonPrimitive.content}.getOrNull()}
}
private suspend fun fetchNewsImage(story:NewsStory):String? = withContext(Dispatchers.IO) {
    if(story.image!=null) return@withContext story.image
    runCatching {
        val url=NEWS_API+"/api/news-image?url="+URLEncoder.encode(story.link,Charsets.UTF_8.name())
        Json.parseToJsonElement(httpText(url)).jsonObject.field("image").takeIf{it.startsWith("http")}
    }.getOrNull()
}

@Composable
fun NativeNewsroomPage() {
    val context= LocalContext.current
    val scope=rememberCoroutineScope()
    var stories by remember { mutableStateOf(NewsCache.stories) }
    var failed by remember { mutableStateOf(NewsCache.failed) }
    var loading by remember { mutableStateOf(stories.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var visibleCount by remember { mutableIntStateOf(5) }
    var requestInFlight by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    suspend fun refresh(force:Boolean) {
        if(!force && NewsCache.stories.isNotEmpty() && System.currentTimeMillis()-NewsCache.loadedAt<300000L) {
            stories=NewsCache.stories; failed=NewsCache.failed; loading=false; return
        }
        if(requestInFlight) return
        requestInFlight=true
        if(force) refreshing=true else loading=true
        error=null
        try {
            val result=fetchStories()
            stories=result.first; failed=result.second
            NewsCache.stories=stories; NewsCache.failed=failed; NewsCache.loadedAt=System.currentTimeMillis()
        } catch(cancelled:kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch(t:Throwable) {
            error=t.message ?: "News feeds could not be reached."
        } finally {
            requestInFlight=false
            loading=false
            refreshing=false
        }
    }
    LaunchedEffect(Unit){refresh(false)}
    LaunchedEffect(category, search) { visibleCount = 5 }
    val categories=listOf("All","Anime","Manga","Releases","Industry","Events")
    val filtered=stories.filter { s ->
        val text=(s.title+" "+s.description+" "+s.source).lowercase()
        val match=when(category) {
            "All"->true
            "Manga"->Regex("manga|manhwa|manhua|webtoon|comic").containsMatchIn(text)
            "Releases"->Regex("release|premiere|airing|debut|season|episode|stream").containsMatchIn(text)
            "Events"->Regex("event|convention|expo|festival|comic.?con").containsMatchIn(text)
            "Industry"->s.category.equals("Industry",true)||Regex("studio|production|box office|award|director|business").containsMatchIn(text)
            else->s.category.equals("Anime",true)||Regex("anime").containsMatchIn(text)
        }
        match && (search.isBlank() || text.contains(search.trim(),true))
    }
    val headerCount = 4 + if (failed.isNotEmpty() && stories.isNotEmpty()) 1 else 0
    LaunchedEffect(listState, visibleCount, filtered.size, headerCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                val lastStoryIndex = lastVisible - headerCount
                if (visibleCount < filtered.size && lastStoryIndex >= (visibleCount - 2).coerceAtLeast(0)) {
                    visibleCount = (visibleCount + 5).coerceAtMost(filtered.size)
                }
            }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp, top=16.dp, end=16.dp, bottom=144.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Newspaper,null,tint=NewsAccent,modifier=Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(start=10.dp)) {
                        Text("THE ANIME WORLD, IN ONE PLACE",color=NewsAccent,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                        Text("Ryko Newsroom",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
                    }
                    IconButton(onClick={scope.launch{refresh(true)}}){Icon(Icons.Default.Refresh,"Refresh",tint=if(refreshing)NewsAccent else Color.White)}
                }
                Text("Anime and manga headlines, releases, studio announcements and events.",color=NewsMuted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
            }
            item {
                OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth().rykoGlass(16.dp, Color(0xD91B1B1F), 0.22f),singleLine=true,placeholder={Text("Search news, releases, events…",color=NewsMuted)},leadingIcon={Icon(Icons.Default.Search,null,tint=NewsAccent)},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=NewsAccent,unfocusedBorderColor=Color(0xFF29292F),cursorColor=NewsAccent),shape=RoundedCornerShape(15.dp))
            }
            item { Row(Modifier.fillMaxWidth().rykoGlass(14.dp, Color(0xB31B1B1F), 0.18f).padding(7.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){categories.forEach{c->FilterChip(selected=category==c,onClick={category=c},label={Text(c,fontSize=10.sp)})}} }
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("Latest updates",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold);Text(if(loading)"Collecting headlines…" else filtered.size.toString()+" stories",color=NewsMuted,fontSize=11.sp)}
                    Text(if(refreshing)"Refreshing…" else "Refresh",color=NewsAccent,modifier=Modifier.clickable{scope.launch{refresh(true)}}.padding(8.dp),fontSize=12.sp)
                }
            }
            if(failed.isNotEmpty() && stories.isNotEmpty()) item { Text("Some sources are unavailable: "+failed.joinToString()+". Showing available stories.",color=Color(0xFFFFD18A),fontSize=11.sp,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22C98C35)).padding(12.dp)) }
            if(loading && stories.isEmpty()) items(4) {
                Column(Modifier.fillMaxWidth().rykoGlass(17.dp).padding(13.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF25252B)))
                    Box(Modifier.fillMaxWidth(.45f).height(10.dp).background(Color(0xFF25252B)))
                    Box(Modifier.fillMaxWidth(.9f).height(15.dp).background(Color(0xFF25252B)))
                    Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF25252B)))
                }
            } else if(filtered.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=42.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Icon(Icons.Default.Newspaper,null,tint=NewsMuted,modifier=Modifier.size(36.dp))
                    Text(error ?: "No stories match this filter.",color=Color.White,fontWeight=FontWeight.SemiBold)
                    Text("Try another category or refresh.",color=NewsMuted,fontSize=12.sp)
                    Text("Try again",color=NewsAccent,modifier=Modifier.clickable{scope.launch{refresh(true)}}.padding(10.dp))
                }
            } else items(filtered.take(visibleCount),key={it.link}) { story ->
                NewsCard(story){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(story.link)))}}
            }
            if(visibleCount < filtered.size) item { Text("Loading more stories…",color=NewsMuted,fontSize=11.sp,modifier=Modifier.fillMaxWidth().padding(12.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
            item{Text("Stories open at their original publishers. Images load as cards appear.",color=NewsMuted,fontSize=10.sp)}
        }
    }
}
@Composable
private fun NewsCard(story:NewsStory,onOpen:()->Unit) {
    var resolvedImage by remember(story.link, story.image) { mutableStateOf(story.image) }
    LaunchedEffect(story.link, story.image) { if (resolvedImage == null) resolvedImage = fetchNewsImage(story) }
    Card(Modifier.fillMaxWidth().rykoGlass(18.dp).clickable(onClick=onOpen),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
        Column{
            Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF202027)),contentAlignment=Alignment.Center){
                if(resolvedImage!=null) AsyncImage(resolvedImage,story.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BrokenImage,null,tint=NewsMuted,modifier=Modifier.size(28.dp));Text("Image unavailable",color=NewsMuted,fontSize=11.sp)}
            }
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(story.source.ifBlank{"Ryko News"},color=NewsAccent,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    Icon(Icons.Default.AccessTime,null,tint=NewsMuted,modifier=Modifier.size(12.dp))
                    Text(formatNewsDate(story.time),color=NewsMuted,fontSize=10.sp,modifier=Modifier.padding(start=4.dp))
                }
                Text(story.title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold,lineHeight=21.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                if(story.description.isNotBlank())Text(story.description,color=NewsMuted,fontSize=12.sp,lineHeight=18.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically){Text("Read full story",color=NewsAccent,fontSize=11.sp,fontWeight=FontWeight.SemiBold);Icon(Icons.Default.OpenInNew,null,tint=NewsAccent,modifier=Modifier.padding(start=5.dp).size(13.dp))}
            }
        }
    }
}
private fun formatNewsDate(time:Long)=if(time<=0L)"Recently" else runCatching{DateTimeFormatter.ofPattern("d MMM yyyy",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))}.getOrDefault("Recently")
