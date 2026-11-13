package com.matuucrea.miltvplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matuucrea.miltvplus.data.Channel
import com.matuucrea.miltvplus.auth.AuthSession
import com.matuucrea.miltvplus.auth.SupabaseAuthRepository
import com.matuucrea.miltvplus.data.SupabaseUserRepository
import com.matuucrea.miltvplus.ui.AccountScreen
import com.matuucrea.miltvplus.ui.SettingsScreen
import com.matuucrea.miltvplus.ui.CountryPickerScreen
import com.matuucrea.miltvplus.ui.AdultAccessScreen
import com.matuucrea.miltvplus.ui.RemoteSourcesScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Intent
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.matuucrea.miltvplus.data.M3uParser
import com.matuucrea.miltvplus.data.RemoteCatalogEntry
import com.matuucrea.miltvplus.data.SupabaseCatalogRepository
import com.matuucrea.miltvplus.player.PlayerViewModel
import com.matuucrea.miltvplus.update.AppUpdateRepository

private val bg: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val panel: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val cyan=Color(0xFF00D9F5); private val blue=Color(0xFF087BFF); private val orange=Color(0xFFFF7900)
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(5,9,27);window.navigationBarColor=android.graphics.Color.rgb(5,9,27);setContent {
        var themeMode by remember { mutableStateOf(getSharedPreferences("miltvplus_preferences",0).getString("theme","dark") ?: "dark") }
        var accentColor by remember { mutableStateOf(getSharedPreferences("miltvplus_preferences",0).getString("accent_color","electric-blue") ?: "electric-blue") }
        val accent = when(accentColor) { "violet" -> Color(0xFF9B6BFF); "emerald" -> Color(0xFF20C997); else -> cyan }
        val scheme = when(themeMode) {
            "light" -> lightColorScheme(primary=blue,secondary=orange,background=Color(0xFFF3F6FC),surface=Color.White,onBackground=Color(0xFF101A38),onSurface=Color(0xFF101A38),onSurfaceVariant=Color(0xFF526078))
            "system" -> if (isSystemInDarkTheme()) darkColorScheme(primary=cyan,secondary=orange,background=Color(0xFF05091B),surface=Color(0xFF101A38)) else lightColorScheme(primary=blue,secondary=orange,background=Color(0xFFF3F6FC),surface=Color.White,onBackground=Color(0xFF101A38),onSurface=Color(0xFF101A38),onSurfaceVariant=Color(0xFF526078))
            else -> darkColorScheme(primary=cyan,secondary=orange,background=Color(0xFF05091B),surface=Color(0xFF101A38))
        }
        MaterialTheme(colorScheme=scheme.copy(primary=accent)){ App(onThemeChanged={mode,color->themeMode=mode;accentColor=color},authCallback=intent?.data) }
    } }
}
@Composable private fun App(onThemeChanged:(String,String)->Unit, authCallback:Uri?=null, vm:PlayerViewModel=viewModel()){
    val context=LocalContext.current
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    var tab by remember { mutableStateOf("Inicio") }; var query by remember { mutableStateOf("") }; var updateNotice by remember { mutableStateOf<String?>(null) }
    val remoteRepository = remember { SupabaseCatalogRepository() }
    val authRepository = remember(context) { SupabaseAuthRepository(context) }
    val userRepository = remember { SupabaseUserRepository { authRepository.restoreSession() } }
    var authSession by remember { mutableStateOf<AuthSession?>(authRepository.currentSession()) }
    var cloudFavorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    val uiScope = rememberCoroutineScope()
    var remoteKindFilter by remember { mutableStateOf<String?>(null) }
    var channelCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedChannelCountry by remember { mutableStateOf<String?>(null) }
    var countryCode by remember { mutableStateOf(context.getSharedPreferences("miltvplus_preferences",0).getString("country_code","AR") ?: "AR") }
    LaunchedEffect(Unit) {
        val callback = authCallback ?: (context as? MainActivity)?.intent?.data
        authSession = if (callback != null) runCatching { authRepository.acceptCallback(callback) }.getOrNull() ?: authRepository.restoreSession()
            else authRepository.restoreSession()
        runCatching { AppUpdateRepository.checkForUpdate().getOrThrow() }.onSuccess { release ->
            updateNotice = release?.versionName
            context.getSharedPreferences("miltvplus_preferences", 0).edit()
                .putString("pending_update_version", release?.versionName)
                .putString("pending_update_url", release?.releaseUrl)
                .apply()
        }
    }
    LaunchedEffect(authSession?.userId) {
        if (authSession == null) {
            cloudFavorites = emptySet()
        } else {
            cloudFavorites = runCatching { userRepository.favoriteIds() }.getOrDefault(emptySet())
            runCatching { userRepository.fetchPreferences() }.getOrNull()?.let { remotePrefs ->
                val prefs = context.getSharedPreferences("miltvplus_preferences",0)
                val theme = remotePrefs.optString("theme","dark")
                val accent = remotePrefs.optString("accent_color","electric-blue")
                val country = remotePrefs.optString("country_code").takeIf { it.matches(Regex("^[A-Z]{2}$")) } ?: "AR"
                prefs.edit()
                    .putString("theme",theme).putString("accent_color",accent).putString("country_code",country)
                    .putString("language",remotePrefs.optString("language_code","es"))
                    .putString("audio_language",remotePrefs.optString("preferred_audio_language").takeIf { it.isNotBlank() && it != "null" } ?: "original")
                    .putBoolean("subtitles",remotePrefs.optBoolean("subtitles_enabled",true))
                    .putBoolean("notifications",remotePrefs.optBoolean("notifications_enabled",true))
                    .putBoolean("autoplay",remotePrefs.optBoolean("autoplay_enabled",false)).apply()
                countryCode = country
                onThemeChanged(theme,accent)
            }
        }
    }
    var remoteCategory by remember { mutableStateOf("tv_channels") }
    var remoteItems by remember { mutableStateOf<List<RemoteCatalogEntry>>(emptyList()) }
    var remoteLoading by remember { mutableStateOf(false) }
    var catalogRefresh by remember { mutableIntStateOf(0) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, remoteCategory, catalogRefresh, countryCode) {
        if (tab in setOf("Inicio", "Catálogo") && remoteCategory == "tv_channels") {
            remoteError = null
            while (true) {
                if (remoteItems.isEmpty()) remoteLoading = true
                val result = remoteRepository.fetchPublished(remoteCategory, if (remoteCategory == "tv_channels") null else countryCode)
                result.onSuccess { remoteItems = it }
                    .onFailure { if (remoteItems.isEmpty()) remoteError = it.message ?: "No se pudo consultar el catálogo." }
                remoteLoading = false
                if (remoteCategory != "tv_channels") break
                delay(60_000)
            }
        }
    }
    val channels by vm.channels.collectAsState(); val selected by vm.selected.collectAsState(); val errorMessage by vm.error.collectAsState(); val favorites by vm.favorites.collectAsState()
    LaunchedEffect(selected?.url) {
        val url = selected?.url
        if (url == null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } else {
            val prefs = context.getSharedPreferences("miltvplus_preferences",0)
            val audioLanguage = prefs.getString("audio_language","original")
            val subtitleLanguage = prefs.getString("language","es")
            val subtitlesEnabled = prefs.getBoolean("subtitles",true)
            val trackParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                .apply {
                    if (!audioLanguage.isNullOrBlank() && audioLanguage != "original") setPreferredAudioLanguage(audioLanguage)
                    if (subtitlesEnabled && !subtitleLanguage.isNullOrBlank()) setPreferredTextLanguage(subtitleLanguage)
                }.build()
            exoPlayer.trackSelectionParameters = trackParameters
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri ->
        uri?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.use { M3uParser.parse(readM3uLimited(it)) } ?: throw IllegalStateException("No se pudo abrir el archivo") }
            .onSuccess { vm.setChannels(it); tab="TV" }.onFailure { vm.setError("No se pudo importar: "+(it.message ?: "archivo inválido")) } }
    }
    Scaffold(containerColor=bg,bottomBar={NavigationBar(containerColor=Color(0xFF080F28)){
        listOf("Inicio","TV","Fuentes","Favoritos","Ajustes").forEach { t ->
            val ico=when(t){"Inicio"->Icons.Default.Home;"TV"->Icons.Default.LiveTv;"Fuentes"->Icons.Default.PlaylistAdd;"Favoritos"->Icons.Default.Favorite;else->Icons.Default.Settings}
            NavigationBarItem(selected=tab==t || (t=="TV" && tab=="Catálogo" && remoteCategory=="tv_channels"),onClick={if(t=="TV"){remoteCategory="tv_channels";remoteKindFilter=null;tab="Catálogo"}else tab=t},icon={Icon(ico,null)},label={Text(t)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color(0xFFFF4059),selectedTextColor=Color(0xFFFF4059),indicatorColor=Color(0xFFFF4059).copy(alpha=.16f)))
        }
    }}){pad->Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071638),bg,Color(0xFF100D20)))).padding(pad)){
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("MILTVPLUS",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black,letterSpacing=1.5.sp);Text("ENTRETENIMIENTO • V22.34.9",color=cyan,fontSize=10.sp)};Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(authSession==null) "● LOCAL" else "● CUENTA",color=cyan,fontSize=10.sp)
            updateNotice?.let { version -> TextButton(onClick={tab="Ajustes"}) { Text("ACTUALIZAR $version",color=orange,fontSize=10.sp) } }
            IconButton(onClick={tab="Cuenta"}) { Icon(Icons.Default.AccountCircle,"Cuenta",tint=cyan) }
        }}
        when(tab){
            "Inicio"->{
                val countryItems=remoteItems.filter { it.countryCode==countryCode }
                val featured=countryItems.firstOrNull() ?: remoteItems.firstOrNull()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Card(Modifier.fillMaxWidth().padding(horizontal=14.dp),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF17171C))) {
                        Box(Modifier.fillMaxWidth().height(238.dp)) {
                            if(featured?.artworkUrl!=null) AsyncImage(model=featured.artworkUrl,contentDescription=featured.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xF5000000)))))
                            Column(Modifier.align(Alignment.BottomStart).padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                Text("MILTVPLUS · TELEVISIÓN EN VIVO",color=Color(0xFFFF5268),fontSize=10.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.3.sp)
                                Text(featured?.title ?: "Tu mundo. Tus canales.",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Black)
                                Text(countryDisplayName(featured?.countryCode ?: countryCode),color=Color.LightGray,fontSize=13.sp)
                                Button(onClick={
                                    val source=featured
                                    if(source!=null) {
                                        val playable=countryItems.filter { it.actionUrl!=null }.map { Channel(it.id,it.title,it.actionUrl!!,it.countryCode ?: "",it.artworkUrl) }
                                        val idx=playable.indexOfFirst { it.id==source.id }
                                        if(idx>=0) launchChannelPlayer(context,playable,idx,remoteCatalog=true,countryCode=countryCode)
                                        else {
                                            val official=countryItems.filter { it.infoUrl!=null }
                                            val oi=official.indexOfFirst { it.id==source.id }
                                            if(oi>=0) launchOfficialWatch(context,official,oi,countryCode,null)
                                        }
                                    } else { remoteCategory="tv_channels";tab="Catálogo" }
                                },colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black)) { Icon(Icons.Default.PlayArrow,null);Text("  Ver canal") }
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Text("EXPLORAR POR PAÍS",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                        Text("País seleccionado: "+countryDisplayName(countryCode),color=Color.LightGray,fontSize=12.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            val codes=(listOf(countryCode)+remoteItems.mapNotNull { it.countryCode }.distinct().sorted()).distinct()
                            codes.forEach { code ->
                                FilterChip(selected=countryCode==code,onClick={
                                    countryCode=code
                                    context.getSharedPreferences("miltvplus_preferences",0).edit().putString("country_code",code).apply()
                                },label={Text(countryFlag(code)+"  "+countryDisplayName(code))},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF9F172C),selectedLabelColor=Color.White))
                            }
                            AssistChip(onClick={tab="Países"},label={Text("Todos los países  ›")})
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        listOf("Directo","Favoritos","Guía TV").forEach { label ->
                            Card(Modifier.weight(1f),onClick={if(label=="Favoritos")tab="Favoritos" else {remoteCategory=if(label=="Guía TV")"epg_programs" else "tv_channels";tab="Catálogo"}},colors=CardDefaults.cardColors(containerColor=Color(0xFF1B1B21)),shape=RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                    Icon(when(label){"Directo"->Icons.Default.LiveTv;"Favoritos"->Icons.Default.Favorite;else->Icons.Default.CalendarMonth},null,tint=Color(0xFFFF4059),modifier=Modifier.size(24.dp))
                                    Text(label,color=Color.White,fontWeight=FontWeight.Bold)
                                    Text(if(label=="Directo") countryItems.size.toString()+" canales" else if(label=="Favoritos") favorites.size.toString()+" guardados" else "Programación",color=Color.LightGray,fontSize=11.sp)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("EN DIRECTO · "+countryDisplayName(countryCode),Modifier.weight(1f),color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                        TextButton(onClick={remoteCategory="tv_channels";tab="Catálogo"}) { Text("Ver todos",color=Color(0xFFFF5268)) }
                    }
                    if(remoteLoading && countryItems.isEmpty()) Box(Modifier.fillMaxWidth().height(100.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Color(0xFFFF4059))}
                    else if(countryItems.isEmpty()) Text("No hay canales publicados para este país todavía.",Modifier.padding(horizontal=16.dp),color=Color.LightGray,fontSize=13.sp)
                    else LazyRow(contentPadding=PaddingValues(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                        items(countryItems.take(30),key={it.id}) { item ->
                            Card(Modifier.width(205.dp).height(154.dp),onClick={
                                val playable=countryItems.filter { it.actionUrl!=null }.map { Channel(it.id,it.title,it.actionUrl!!,it.countryCode ?: "",it.artworkUrl) }
                                val idx=playable.indexOfFirst { it.id==item.id }
                                if(idx>=0) launchChannelPlayer(context,playable,idx,remoteCatalog=true,countryCode=countryCode)
                                else {
                                    val official=countryItems.filter { it.infoUrl!=null }
                                    val oi=official.indexOfFirst { it.id==item.id }
                                    if(oi>=0) launchOfficialWatch(context,official,oi,countryCode,null)
                                }
                            },colors=CardDefaults.cardColors(containerColor=Color(0xFF1C1C22)),shape=RoundedCornerShape(17.dp)) {
                                Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        if(item.artworkUrl!=null) AsyncImage(model=item.artworkUrl,contentDescription=null,modifier=Modifier.size(38.dp),contentScale=ContentScale.Fit)
                                        Spacer(Modifier.width(8.dp))
                                        Text(item.title,color=Color.White,fontWeight=FontWeight.Bold,maxLines=2)
                                    }
                                    Text(item.subtitle.ifBlank { "Señal oficial · "+item.category },color=Color.LightGray,fontSize=11.sp,maxLines=2)
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(if(item.actionUrl!=null) Color(0xFF26C281) else Color(0xFFFFB74D),RoundedCornerShape(50)))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if(item.actionUrl!=null) "Stream directo disponible" else "Reproductor oficial",color=if(item.actionUrl!=null) Color(0xFF54D69B) else Color(0xFFFFC66D),fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    if(remoteError!=null) Text(remoteError!!,Modifier.padding(horizontal=16.dp),color=Color(0xFFFF9A9A))
                    Spacer(Modifier.height(18.dp))
                }
            }
            "Catálogo"->{Row(Modifier.fillMaxWidth().padding(end=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Title("Catálogo global","Contenido publicado desde tu backend seguro")};IconButton(onClick={catalogRefresh++},enabled=!remoteLoading){Icon(Icons.Default.Refresh,"Actualizar catálogo",tint=cyan)}};TextButton(onClick={tab="Inicio"},modifier=Modifier.padding(start=12.dp)){Text("← Volver a Inicio")}
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("tv_channels" to "Canales por país","catalog_items" to "TV / VOD","radio_stations" to "Radios","sports_events" to "Deportes","music_items" to "Música","theater_events" to "Teatro","epg_programs" to "Guía EPG").forEach{(table,label)->
                        FilterChip(selected=remoteCategory==table,onClick={remoteCategory=table;remoteKindFilter=null;channelCategoryFilter=null},label={Text(label)})
                    }
                }
                if(remoteCategory=="tv_channels" && remoteItems.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected=channelCategoryFilter==null,onClick={channelCategoryFilter=null},label={Text("Todas las categorías")})
                        remoteItems.map { it.category }.filter { it.isNotBlank() }.distinct().sorted().forEach { category ->
                            FilterChip(selected=channelCategoryFilter==category,onClick={channelCategoryFilter=category},label={Text(category.replaceFirstChar { it.uppercase() })})
                        }
                    }
                }
                if(remoteCategory=="tv_channels" && remoteItems.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected=selectedChannelCountry==null,onClick={selectedChannelCountry=null},label={Text("Todos los países")})
                        remoteItems.mapNotNull { it.countryCode }.distinct().sorted().forEach { code ->
                            FilterChip(selected=selectedChannelCountry==code,onClick={selectedChannelCountry=code},label={Text(countryFlag(code)+"  "+countryDisplayName(code))},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF9F172C),selectedLabelColor=Color.White))
                        }
                    }
                }
                if(remoteCategory=="catalog_items") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Todos", "live" to "En vivo", "movie" to "Películas", "series" to "Series").forEach { (kind,label) ->
                            FilterChip(selected=remoteKindFilter==kind,onClick={remoteKindFilter=kind},label={Text(label)})
                        }
                    }
                }
                when { remoteLoading -> Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=cyan)}
                    remoteError!=null -> Empty("No se pudo conectar",remoteError ?: "Error de conexión")
                    remoteItems.isEmpty() -> Empty("Catálogo sin publicaciones","La conexión responde, pero esta sección todavía no tiene registros publicados.")
                    else -> {
                        val publicItems = remoteItems.filterNot { isAdultRating(it.ageRating) }
                        if (publicItems.isEmpty()) {
                            Empty("No hay publicaciones disponibles","No se muestran títulos +18 fuera de la sección protegida.")
                        } else if (remoteCategory == "epg_programs") {
                            val schedule = publicItems
                                .filter { !it.channelExternalId.isNullOrBlank() && !it.startsAt.isNullOrBlank() }
                                .groupBy { it.channelExternalId!! }
                                .toSortedMap()
                            if (schedule.isEmpty()) {
                                Empty("Guía sin horarios válidos","Las publicaciones EPG necesitan channel_external_id, starts_at y ends_at.")
                            } else {
                                LazyColumn(contentPadding=PaddingValues(14.dp)) {
                                    schedule.forEach { (channelId, programs) ->
                                        item(key="channel_$channelId") {
                                            Text("CANAL · $channelId",Modifier.padding(top=12.dp,bottom=5.dp),color=cyan,fontWeight=FontWeight.Bold)
                                        }
                                        items(programs.sortedBy { it.startsAt },key={it.id}) { program ->
                                            val startDate = parseIsoDate(program.startsAt)
                                            val endDate = parseIsoDate(program.endsAt)
                                            val start = formatLocalTime(startDate, program.startsAt)
                                            val end = formatLocalTime(endDate, program.endsAt)
                                            val liveNow = startDate?.before(Date()) == true && endDate?.after(Date()) == true
                                            Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=panel)) {
                                                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Schedule,null,tint=cyan)
                                                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                                                        Text(program.title,color=Color.White,fontWeight=FontWeight.Bold)
                                                        Text((if(liveNow) "EN VIVO · " else "")+"$start – $end",color=cyan,fontSize=12.sp)
                                                        if(program.subtitle.isNotBlank()) Text(program.subtitle,color=Color.LightGray,fontSize=12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(contentPadding=PaddingValues(14.dp)){items(publicItems.filter { (remoteKindFilter==null || it.kind==remoteKindFilter) && (remoteCategory!="tv_channels" || ((channelCategoryFilter==null || it.category==channelCategoryFilter) && (selectedChannelCountry==null || it.countryCode==selectedChannelCountry))) },key={it.id}){item->
                                Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=panel)){
                                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                                        if(item.artworkUrl!=null) AsyncImage(
                                            model=item.artworkUrl,
                                            contentDescription=item.title,
                                            contentScale=ContentScale.Crop,
                                            modifier=Modifier.size(width=64.dp,height=88.dp)
                                        )
                                        Icon(when(remoteCategory){"radio_stations"->Icons.Default.Radio; "sports_events"->Icons.Default.SportsSoccer; "music_items"->Icons.Default.MusicNote; "theater_events"->Icons.Default.TheaterComedy; else->Icons.Default.LiveTv},null,tint=cyan)
                                        Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(item.title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);if(item.subtitle.isNotBlank())Text(item.subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}
                                        if(authSession!=null && remoteCategory=="catalog_items" && item.id.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))) {
                                            IconButton(onClick={
                                                val enable=item.id !in cloudFavorites
                                                uiScope.launch {
                                                    runCatching { userRepository.setFavorite(item.id,enable) }
                                                        .onSuccess { cloudFavorites=if(enable) cloudFavorites+item.id else cloudFavorites-item.id }
                                                        .onFailure { remoteError=it.message ?: "No se pudo sincronizar el favorito." }
                                                }
                                            }) { Icon(if(item.id in cloudFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorito en la nube",tint=cyan) }
                                        }
                                        if(item.actionUrl!=null && remoteCategory=="tv_channels") TextButton(onClick={
                                            val playable = remoteItems.filter { it.actionUrl != null && (selectedChannelCountry==null || it.countryCode==selectedChannelCountry) && (channelCategoryFilter==null || it.category==channelCategoryFilter) }.map { Channel(it.id,it.title,it.actionUrl!!,it.countryCode ?: "",it.artworkUrl) }
                                            val start = playable.indexOfFirst { it.id == item.id }
                                            if(start >= 0) launchChannelPlayer(context, playable, start, remoteCatalog=true, countryCode=selectedChannelCountry, category=channelCategoryFilter)
                                        }){Text("Ver en pantalla completa")}
                                        if(item.actionUrl!=null && remoteCategory in setOf("radio_stations","catalog_items","sports_events","theater_events")) TextButton(onClick={vm.play(Channel(item.id,item.title,item.actionUrl,item.subtitle,item.artworkUrl))}){Text("Reproducir")}
                                        if(item.infoUrl!=null && remoteCategory in setOf("sports_events","theater_events","music_items","radio_stations","tv_channels")) TextButton(onClick={
                                            if(remoteCategory=="tv_channels") {
                                                val watchable = remoteItems.filter { it.infoUrl != null && (selectedChannelCountry==null || it.countryCode==selectedChannelCountry) && (channelCategoryFilter==null || it.category==channelCategoryFilter) }
                                                val start = watchable.indexOfFirst { it.id==item.id }
                                                if(start>=0) launchOfficialWatch(context,watchable,start,selectedChannelCountry,channelCategoryFilter)
                                            } else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.infoUrl))) }.onFailure { remoteError="No se pudo abrir el enlace oficial." }
                                        }){Text(if(remoteCategory=="tv_channels") "Ver señal oficial" else "Información")}
                                    }
                                }
                            }}
                        }
                    }
                }
            }
            "Fuentes"->{Title("Fuentes","Importación local y listas sincronizadas");Card(Modifier.fillMaxWidth().padding(16.dp),colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.PlaylistAdd,null,tint=cyan);Text("Agregar lista M3U / M3U8",color=MaterialTheme.colorScheme.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Seleccioná un archivo del dispositivo. Usá solo listas propias, públicas o licenciadas.",color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick={picker.launch(arrayOf("audio/x-mpegurl","application/vnd.apple.mpegurl","text/plain","*/*"))}){Icon(Icons.Default.FolderOpen,null);Text("Seleccionar archivo")}}};errorMessage?.let{Text(it,Modifier.padding(18.dp),color=orange)};RemoteSourcesScreen(userRepository,authSession!=null,countryCode){ imported -> vm.setChannels(imported); tab="TV" }}
            "Cuenta"->{Title("Cuenta","Acceso y sincronización entre dispositivos");AccountScreen(authRepository,userRepository){authSession=it}}
            "Adultos"->{Title("Acceso protegido","Control parental local y confirmación de edad");AdultAccessScreen(userRepository,authSession!=null,countryCode){item->item.actionUrl?.let{vm.play(Channel(item.id,item.title,it,item.subtitle,item.artworkUrl))}}}
            "Países"->{CountryPickerScreen { country ->
                countryCode=country.code
                val prefs=context.getSharedPreferences("miltvplus_preferences",0)
                uiScope.launch { if(authSession!=null) runCatching { userRepository.savePreferences(prefs.getString("theme","dark")?:"dark",country.code,prefs.getString("language","es")?:"es",prefs.getBoolean("subtitles",true),prefs.getBoolean("notifications",true),prefs.getBoolean("autoplay",false),prefs.getString("accent_color","electric-blue")?:"electric-blue",prefs.getString("audio_language","original")?.takeIf{it!="original"}) } }
            }}
            "Ajustes"->{Title("Ajustes","Personalizá tu experiencia");SettingsScreen(onThemeChanged,userRepository,authSession!=null)
                TextButton(onClick={tab="Países"},modifier=Modifier.padding(horizontal=16.dp)){Text("🌎 País y región")}
                TextButton(onClick={tab="Cuenta"},modifier=Modifier.padding(horizontal=16.dp)){Text("👤 Cuenta y sincronización")}
                TextButton(onClick={tab="Adultos"},modifier=Modifier.padding(horizontal=16.dp)){Text("🔞 Control parental +18")}
                TextButton(onClick={tab="Música"},modifier=Modifier.padding(horizontal=16.dp)){Text("🎧 Conectar con Spotify")}
            }
            "Música"->{Title("Música","Artistas, álbumes y listas publicadas")
                Button(onClick={ { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://open.spotify.com/"))) }.onFailure { remoteError="No se pudo abrir Spotify." } } },modifier=Modifier.padding(horizontal=16.dp)){Icon(Icons.Default.OpenInNew,null);Text("Abrir Spotify")}
                Text("La conexión con Spotify abre su servicio oficial. El inicio de sesión OAuth y la reproducción integrada requieren una aplicación Spotify registrada y aprobada.",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={remoteCategory="music_items";tab="Catálogo"}){Text("Ver música publicada en MILTVPLUS")}
            }
            else->{Title(if(tab=="TV")"Televisión en vivo" else "Tus favoritos","Elegí un canal para reproducir");OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp),placeholder={Text("Buscar canal o categoría")},singleLine=true);val list=channels.filter{(tab!="Favoritos"||it.id in favorites)&&(it.name.contains(query,true)||it.group.contains(query,true))};if(list.isEmpty())Empty("No hay canales para mostrar","Agregá una lista autorizada desde Fuentes.");LazyColumn(contentPadding=PaddingValues(14.dp)){items(list,key={it.id}){c->ChannelItem(c,c.id in favorites,{launchChannelPlayer(context,channels,channels.indexOfFirst { it.id==c.id })},{vm.toggleFavorite(c.id)})}};selected?.let{channel->Card(Modifier.fillMaxWidth().padding(14.dp),colors=CardDefaults.cardColors(containerColor=Color.Black)){Column(Modifier.padding(12.dp)){Text("REPRODUCIENDO · "+channel.name,color=cyan,fontWeight=FontWeight.Bold);AndroidView(factory={ctx->PlayerView(ctx).apply{player=exoPlayer;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}},update={it.player=exoPlayer},modifier=Modifier.fillMaxWidth().height(220.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={launchChannelPlayer(context,channels,channels.indexOfFirst { it.id==channel.id }.coerceAtLeast(0))}){Text("Pantalla completa")};TextButton(onClick={vm.stop()}){Text("Detener reproducción")}}}}};errorMessage?.let{Text(it,color=orange,modifier=Modifier.padding(12.dp))}}
        }
    }}
}
private fun readM3uLimited(input:InputStream):String {
    val output=ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    var total=0
    while(true) {
        val count=input.read(buffer)
        if(count<0) break
        total+=count
        require(total<=5*1024*1024) { "La lista M3U supera el límite de 5 MB." }
        output.write(buffer,0,count)
    }
    return output.toString(Charsets.UTF_8.name())
}
private fun parseIsoDate(value:String?):Date? {
    if(value.isNullOrBlank()) return null
    val normalized=value.replace(Regex("([+-]\\d{2}):(\\d{2})$")) { match -> match.groupValues[1]+match.groupValues[2] }
        .replace("Z","+0000")
    val patterns=listOf("yyyy-MM-dd'T'HH:mm:ss.SSSZ","yyyy-MM-dd'T'HH:mm:ssZ")
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern,Locale.US).apply { isLenient=false }.parse(normalized) }.getOrNull()
    }
}
private fun formatLocalTime(date:Date?,fallback:String?):String =
    date?.let { SimpleDateFormat("HH:mm",Locale.getDefault()).format(it) }
        ?: fallback?.substringAfter('T')?.take(5).orEmpty()

private fun isAdultRating(rating:String?):Boolean {
    val value=rating?.trim()?.uppercase().orEmpty()
    return value.contains("18") || value in setOf("NC-17","R18","TV-MA","MA","AO","ADULT","ADULTS ONLY")
}
@Composable private fun Title(a:String,b:String){Column(Modifier.padding(start=20.dp,end=20.dp,top=22.dp,bottom=10.dp)){Text(a,color=MaterialTheme.colorScheme.onBackground,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(b,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}}
@Composable private fun Empty(a:String,b:String){Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.TvOff,null,tint=cyan,modifier=Modifier.size(36.dp));Text(a,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text(b,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}}
@Composable private fun ChannelItem(c:Channel,f:Boolean,play:()->Unit,fav:()->Unit){Card(onClick=play,Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LiveTv,null,tint=cyan);Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(c.name,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold);Text(c.group.ifBlank{"Sin categoría"},color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)};IconButton(onClick=fav){Icon(if(f)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(f)orange else Color.LightGray)}}}}

private fun launchChannelPlayer(context: android.content.Context, channels: List<Channel>, startIndex: Int, remoteCatalog: Boolean = false, countryCode: String? = null, category: String? = null) {
    val playable = channels.filter { it.url.startsWith("https://", true) }
    if (playable.isEmpty()) return
    val selected = channels.getOrNull(startIndex)
    val index = playable.indexOfFirst { selected != null && it.id == selected.id }.coerceAtLeast(0)
    val payload = org.json.JSONArray()
    playable.forEach { channel ->
        payload.put(org.json.JSONObject().put("id",channel.id).put("name",channel.name).put("url",channel.url))
    }
    context.startActivity(
        Intent(context, com.matuucrea.miltvplus.player.ChannelPlayerActivity::class.java)
            .putExtra(com.matuucrea.miltvplus.player.ChannelPlayerActivity.EXTRA_CHANNELS,payload.toString())
             .putExtra(com.matuucrea.miltvplus.player.ChannelPlayerActivity.EXTRA_START_INDEX,index)
            .putExtra(com.matuucrea.miltvplus.player.ChannelPlayerActivity.EXTRA_REMOTE_CATALOG,remoteCatalog)
            .putExtra(com.matuucrea.miltvplus.player.ChannelPlayerActivity.EXTRA_COUNTRY_CODE,countryCode ?: "")
            .putExtra(com.matuucrea.miltvplus.player.ChannelPlayerActivity.EXTRA_CATEGORY,category ?: "")
    )
}

private fun launchOfficialWatch(context: android.content.Context, entries: List<RemoteCatalogEntry>, startIndex: Int, countryCode: String? = null, category: String? = null) {
    val watchable = entries.filter { it.infoUrl?.startsWith("https://", true) == true }
    if (watchable.isEmpty()) return
    val selected = entries.getOrNull(startIndex)
    val index = watchable.indexOfFirst { selected != null && it.id == selected.id }.coerceAtLeast(0)
    val payload = org.json.JSONArray()
    watchable.forEach { entry ->
        payload.put(org.json.JSONObject().put("id",entry.id).put("name",entry.title).put("url",entry.infoUrl))
    }
    context.startActivity(
        Intent(context, com.matuucrea.miltvplus.player.OfficialWatchActivity::class.java)
            .putExtra(com.matuucrea.miltvplus.player.OfficialWatchActivity.EXTRA_CHANNELS,payload.toString())
             .putExtra(com.matuucrea.miltvplus.player.OfficialWatchActivity.EXTRA_START_INDEX,index)
            .putExtra(com.matuucrea.miltvplus.player.OfficialWatchActivity.EXTRA_REMOTE_CATALOG,true)
            .putExtra(com.matuucrea.miltvplus.player.OfficialWatchActivity.EXTRA_COUNTRY_CODE,countryCode ?: "")
            .putExtra(com.matuucrea.miltvplus.player.OfficialWatchActivity.EXTRA_CATEGORY,category ?: "")
    )
}

private fun countryDisplayName(code:String):String = when(code.uppercase()) {
 "AR"->"Argentina";"BR"->"Brasil";"CL"->"Chile";"UY"->"Uruguay";"PY"->"Paraguay";"BO"->"Bolivia";"PE"->"Perú";"EC"->"Ecuador";"CO"->"Colombia";"VE"->"Venezuela";"MX"->"México";"US"->"Estados Unidos";"CA"->"Canadá";"ES"->"España";"PT"->"Portugal";"FR"->"Francia";"IT"->"Italia";"DE"->"Alemania";"GB"->"Reino Unido";"JP"->"Japón";"KR"->"Corea del Sur";"IN"->"India";"ZA"->"Sudáfrica";"AU"->"Australia";"NZ"->"Nueva Zelanda";"CN"->"China";"RU"->"Rusia";"TR"->"Turquía";"IL"->"Israel";"EG"->"Egipto";"NG"->"Nigeria";"KE"->"Kenia";"MA"->"Marruecos";else->code
}
private fun countryFlag(code:String):String {
 val upper=code.uppercase()
 if(upper.length!=2 || upper.any { it !in 'A'..'Z' }) return "🌐"
 return String(Character.toChars(0x1F1E6+upper[0].code-65))+String(Character.toChars(0x1F1E6+upper[1].code-65))
}
