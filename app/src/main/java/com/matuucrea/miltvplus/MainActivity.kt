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
import androidx.compose.foundation.verticalScroll
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
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.matuucrea.miltvplus.player.MusicPlaybackService
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
import com.matuucrea.miltvplus.ui.PremiumHomeDashboard
import com.matuucrea.miltvplus.ui.PremiumSidebar
import com.matuucrea.miltvplus.ui.PremiumAppTopBar
import com.matuucrea.miltvplus.ui.MusicExperience
import com.matuucrea.miltvplus.ui.TmdbCatalogScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Intent
import android.content.ComponentName
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
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(5,9,27);window.navigationBarColor=android.graphics.Color.rgb(5,9,27);if(getSharedPreferences("miltvplus_preferences",0).getBoolean("keep_screen_on",false))window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setContent {
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
    val exoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 30_000, 1_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()
    }
    var musicVolume by remember { mutableFloatStateOf(context.getSharedPreferences("miltvplus_preferences", 0).getFloat("music_volume", 1f).coerceIn(0f, 1f)) }
    val musicControllerFuture = remember(context) {
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync()
    }
    var musicController by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(musicControllerFuture) {
        val listener = Runnable {
            musicController = runCatching { musicControllerFuture.get() }.getOrNull()
        }
        musicControllerFuture.addListener(listener, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(musicControllerFuture) }
    }
    LaunchedEffect(musicVolume, musicController) {
        exoPlayer.volume = musicVolume
        musicController?.volume = musicVolume
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    var tab by remember { mutableStateOf("Inicio") }; var query by remember { mutableStateOf("") }; var updateNotice by remember { mutableStateOf<String?>(null) }
    val remoteRepository = remember { SupabaseCatalogRepository() }
    val musicResolver = remember { com.matuucrea.miltvplus.player.MusicResolverRepository() }
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
        if (tab == "Catálogo" || (tab == "Inicio" && remoteCategory == "tv_channels") || (tab == "Música" && remoteCategory == "music_items")) {
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
    // Único punto de enrutamiento: el menú lateral y los accesos del inicio comparten estas rutas.
    val navigateTo: (String) -> Unit = { destination ->
        when (destination) {
            "Inicio" -> { remoteCategory = "tv_channels"; remoteKindFilter = null; tab = "Inicio" }
            "TV en Vivo" -> { remoteCategory = "tv_channels"; remoteKindFilter = null; tab = "Catálogo" }
            "Películas" -> { remoteKindFilter = null; tab = "Películas" }
            "Series" -> { remoteKindFilter = null; tab = "Series" }
            "Deportes" -> { remoteCategory = "sports_events"; remoteKindFilter = null; tab = "Catálogo" }
            "Infantil" -> { remoteCategory = "catalog_items"; remoteKindFilter = "kids"; tab = "Catálogo" }
            "Documentales" -> { remoteCategory = "catalog_items"; remoteKindFilter = "documentary"; tab = "Catálogo" }
            "Apps" -> { remoteKindFilter = null; tab = "Apps" }
            "Fuentes" -> { remoteKindFilter = null; tab = "Fuentes" }
            "Música" -> { remoteCategory = "music_items"; remoteItems = emptyList(); remoteError = null; remoteKindFilter = null; tab = "Música" }
            "Cuenta" -> { remoteKindFilter = null; tab = "Cuenta" }
            "Radios" -> { remoteCategory = "radio_stations"; remoteKindFilter = null; tab = "Catálogo" }
            "Teatro" -> { remoteCategory = "theater_events"; remoteKindFilter = null; tab = "Catálogo" }
            "Países" -> { remoteKindFilter = null; tab = "Países" }
            "Guía EPG", "EPG" -> { remoteCategory = "epg_programs"; remoteKindFilter = null; tab = "Catálogo" }
            "Adultos 18+" -> tab = "Adultos"
            "Favoritos" -> { remoteKindFilter = null; tab = "Favoritos" }
            "Configuración" -> { remoteKindFilter = null; tab = "Ajustes" }
            "Más Opciones" -> { remoteKindFilter = null; tab = "Apps" }
        }
    }

    val activeNavigation = when {
        tab == "Catálogo" && remoteCategory == "epg_programs" -> "Guía EPG"
        tab == "Catálogo" && remoteCategory == "catalog_items" && remoteKindFilter == "movie" -> "Películas"
        tab == "Catálogo" && remoteCategory == "catalog_items" && remoteKindFilter == "series" -> "Series"
        tab == "Catálogo" && remoteCategory == "catalog_items" && remoteKindFilter == "kids" -> "Infantil"
        tab == "Catálogo" && remoteCategory == "catalog_items" && remoteKindFilter == "documentary" -> "Documentales"
        tab == "Catálogo" && remoteCategory == "sports_events" -> "Deportes"
        tab == "Catálogo" && remoteCategory == "music_items" -> "Música"
        tab == "Catálogo" && remoteCategory == "radio_stations" -> "Radios"
        tab == "Catálogo" && remoteCategory == "theater_events" -> "Teatro"
        tab == "Catálogo" -> "TV en Vivo"
        else -> tab
    }

    val deviceConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTelevision = (deviceConfiguration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    // TV interfaces always use the side navigation, even on low-resolution/720p panels.
    val compactWidth = deviceConfiguration.screenWidthDp < 840 && !isTelevision
    Scaffold(
        containerColor = bg,
        bottomBar = {
            if (compactWidth) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(
                        Triple("Inicio", Icons.Default.Home, "Inicio"),
                        Triple("TV en Vivo", Icons.Default.LiveTv, "TV"),
                        Triple("Fuentes", Icons.Default.PlaylistAdd, "Fuentes"),
                        Triple("Favoritos", Icons.Default.Favorite, "Favoritos"),
                        Triple("Configuración", Icons.Default.Settings, "Ajustes")
                    ).forEach { (destination, icon, label) ->
                        NavigationBarItem(
                            selected = activeNavigation == destination || (destination == "TV en Vivo" && tab == "TV"),
                            onClick = { navigateTo(destination) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, maxLines = 1) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            if (!compactWidth) PremiumSidebar(activeNavigation, navigateTo)
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Color(0xFF08080A),Color(0xFF0B0B0E),Color(0xFF08080A))))
            ) {
            PremiumAppTopBar(tab,onSearch={value -> query=value; if(value.isNotBlank() && tab=="Inicio") tab="Catálogo"},onSettings={tab="Ajustes"},onAccount={tab="Cuenta"})

        updateNotice?.let { version -> TextButton(onClick={tab="Ajustes"}) { Text("ACTUALIZACIÓN DISPONIBLE · $version",color=orange,fontSize=10.sp) } }
        when(tab){
            "Películas" -> TmdbCatalogScreen(isSeries=false, auth=authRepository, onBack={ tab="Inicio" })
            "Series" -> TmdbCatalogScreen(isSeries=true, auth=authRepository, onBack={ tab="Inicio" })
            "Inicio"->{
                val countryItems=remoteItems.filter { it.countryCode==countryCode }
                // Count only direct HTTPS streams, not metadata-only channel listings.
                val playableCountryItems=countryItems.filter { it.actionUrl?.let(::isSafeHttpsUrl)==true }
                val featured=playableCountryItems.firstOrNull()
                PremiumHomeDashboard(
                    countryName=countryDisplayName(countryCode),
                    countryFlag=countryFlag(countryCode),
                    channelCount=playableCountryItems.size,
                    featuredTitle=featured?.title,
                    loading = remoteLoading,
                    errorMessage = remoteError,
                    onNavigate = navigateTo
                )
            }
            "Catálogo"->{Row(Modifier.fillMaxWidth().padding(end=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Title("Catálogo global","Contenido publicado desde tu backend seguro")};IconButton(onClick={catalogRefresh++},enabled=!remoteLoading){Icon(Icons.Default.Refresh,"Actualizar catálogo",tint=cyan)}};TextButton(onClick={remoteCategory="tv_channels";tab="Inicio"},modifier=Modifier.padding(start=12.dp)){Text("← Volver a Inicio")}
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
                            LazyColumn(contentPadding=PaddingValues(14.dp)){items(publicItems.filter { (query.isBlank() || it.title.contains(query,true) || it.subtitle.contains(query,true) || it.category.contains(query,true)) && (when(remoteKindFilter) { "kids" -> it.category.contains("infantil",true) || it.category.contains("kids",true) || it.subtitle.contains("infantil",true); "documentary" -> it.category.contains("documental",true) || it.category.contains("documentary",true) || it.subtitle.contains("documental",true); else -> remoteKindFilter==null || it.kind==remoteKindFilter }) && (remoteCategory!="tv_channels" || ((channelCategoryFilter==null || it.category==channelCategoryFilter) && (selectedChannelCountry==null || it.countryCode==selectedChannelCountry))) },key={it.id}){item->
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
                                        if(item.actionUrl?.let(::isSafeHttpsUrl)==true && remoteCategory=="tv_channels") TextButton(onClick={
                                            val playable = remoteItems.filter { it.actionUrl?.let(::isSafeHttpsUrl)==true && (selectedChannelCountry==null || it.countryCode==selectedChannelCountry) && (channelCategoryFilter==null || it.category==channelCategoryFilter) }.map { Channel(it.id,it.title,it.actionUrl!!,it.countryCode ?: "",it.artworkUrl) }
                                            val start = playable.indexOfFirst { it.id == item.id }
                                            if(start >= 0) launchChannelPlayer(context, playable, start, remoteCatalog=true, countryCode=selectedChannelCountry, category=channelCategoryFilter)
                                        }){Text("Ver en pantalla completa")}
                                        if(item.actionUrl?.let(::isSafeHttpsUrl)==true && remoteCategory in setOf("radio_stations","catalog_items","sports_events","theater_events")) TextButton(onClick={vm.play(Channel(item.id,item.title,item.actionUrl!!,item.subtitle,item.artworkUrl))}){Text("Reproducir")}
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
            "Adultos"->{
                AdultAccessScreen(userRepository,authSession!=null,countryCode) { item ->
                    item.actionUrl?.let { vm.play(Channel(item.id,item.title,it,item.subtitle,item.artworkUrl)) }
                }
            }
            "Países"->{CountryPickerScreen(channelCounts = remoteItems.mapNotNull { item -> item.countryCode?.let { it to 1 } }.groupingBy { it.first }.eachCount()) { country ->
                countryCode=country.code
                val prefs=context.getSharedPreferences("miltvplus_preferences",0)
                uiScope.launch { if(authSession!=null) runCatching { userRepository.savePreferences(prefs.getString("theme","dark")?:"dark",country.code,prefs.getString("language","es")?:"es",prefs.getBoolean("subtitles",true),prefs.getBoolean("notifications",true),prefs.getBoolean("autoplay",false),prefs.getString("accent_color","electric-blue")?:"electric-blue",prefs.getString("audio_language","original")?.takeIf{it!="original"}) } }
            }}
            "Ajustes"->{Title("Ajustes","Personalizá tu experiencia");SettingsScreen(
                onThemeChanged, userRepository, authSession!=null,
                onKeepScreenOnChanged = { keep ->
                    (context as? MainActivity)?.window?.let { window ->
                        if (keep) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                },
                onMusicVolumeChanged = { value -> musicVolume = value }
            )
                TextButton(onClick={tab="Países"},modifier=Modifier.padding(horizontal=16.dp)){Text("🌎 País y región")}
                TextButton(onClick={tab="Cuenta"},modifier=Modifier.padding(horizontal=16.dp)){Text("👤 Cuenta y sincronización")}
                TextButton(onClick={tab="Adultos"},modifier=Modifier.padding(horizontal=16.dp)){Text("🔞 Control parental +18")}
                TextButton(onClick={remoteCategory="music_items";remoteItems=emptyList();remoteError=null;tab="Música"},modifier=Modifier.padding(horizontal=16.dp)){Text("🎧 Conectar con Spotify")}
            }
            "Apps"->{
                Title("Apps y servicios","Accesos disponibles y conexiones oficiales")
                Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),colors=CardDefaults.cardColors(containerColor=panel)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Apps,null,tint=cyan,modifier=Modifier.size(32.dp))
                        Text("Tus servicios, en un solo lugar",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
                        Text("Las integraciones que todavía no están conectadas se mostrarán como no disponibles. No instalamos aplicaciones ni reproducimos servicios externos sin autorización.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                        Button(onClick={tab="Fuentes"},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.PlaylistAdd,null); Spacer(Modifier.width(8.dp)); Text("Administrar fuentes M3U") }
                        OutlinedButton(onClick={remoteCategory="music_items";remoteItems=emptyList();remoteError=null;tab="Música"},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.MusicNote,null); Spacer(Modifier.width(8.dp)); Text("Abrir música") }
                        OutlinedButton(onClick={tab="Ajustes"},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings,null); Spacer(Modifier.width(8.dp)); Text("Configuración") }
                    }
                }
            }
            "Música"->{
                MusicExperience(
                    entries = remoteItems.filterNot { isAdultRating(it.ageRating) },
                    loading = remoteLoading,
                    error = remoteError,
                    query = query,
                    onQueryChange = { query = it },
                    player = musicController ?: exoPlayer,
                    onPlay = { item, queue ->
                        val controller = musicController
                        if (controller == null) {
                            remoteError = "El reproductor de música se está conectando. Probá nuevamente en un instante."
                        } else {
                            uiScope.launch {
                                remoteError = null
                                val playable = queue.asSequence().filter { it.actionUrl != null && it.id.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) }.take(40).toList()
                                val resolved = playable.mapNotNull { track ->
                                    musicResolver.resolve(track.id, countryCode).getOrNull()?.let { track to it }
                                }
                                val startIndex = resolved.indexOfFirst { it.first.id == item.id }
                                if (startIndex < 0) {
                                    remoteError = "Esta pista no tiene una fuente de audio directa disponible para tu país."
                                } else {
                                    val mediaItems = resolved.map { (track, audio) ->
                                        MediaItem.Builder().setMediaId(track.id).setUri(audio.streamUrl)
                                            .setMediaMetadata(
                                                androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(audio.title.ifBlank { track.title })
                                                    .setArtist(audio.creator.ifBlank { track.subtitle })
                                                    .setAlbumTitle(audio.album)
                                                    .setArtworkUri(audio.artworkUrl?.let(Uri::parse) ?: track.artworkUrl?.let(Uri::parse))
                                                    .build()
                                            ).build()
                                    }
                                    controller.setMediaItems(mediaItems, startIndex, 0L)
                                    controller.repeatMode = Player.REPEAT_MODE_ALL
                                    controller.prepare()
                                    controller.play()
                                }
                            }
                        }
                    },
                    onRefresh = { catalogRefresh++ }
                )
            }
            else->{Title(if(tab=="TV")"Televisión en vivo" else "Tus favoritos","Elegí un canal para reproducir");OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp),placeholder={Text("Buscar canal o categoría")},singleLine=true);val list=channels.filter{(tab!="Favoritos"||it.id in favorites)&&(it.name.contains(query,true)||it.group.contains(query,true))};if(list.isEmpty())Empty("No hay canales para mostrar","Agregá una lista autorizada desde Fuentes.");LazyColumn(contentPadding=PaddingValues(14.dp)){items(list,key={it.id}){c->ChannelItem(c,c.id in favorites,{launchChannelPlayer(context,channels,channels.indexOfFirst { it.id==c.id })},{vm.toggleFavorite(c.id)})}};selected?.let{channel->Card(Modifier.fillMaxWidth().padding(14.dp),colors=CardDefaults.cardColors(containerColor=Color.Black)){Column(Modifier.padding(12.dp)){Text("REPRODUCIENDO · "+channel.name,color=cyan,fontWeight=FontWeight.Bold);AndroidView(factory={ctx->PlayerView(ctx).apply{player=exoPlayer;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}},update={it.player=exoPlayer},modifier=Modifier.fillMaxWidth().height(220.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={launchChannelPlayer(context,channels,channels.indexOfFirst { it.id==channel.id }.coerceAtLeast(0))}){Text("Pantalla completa")};TextButton(onClick={vm.stop()}){Text("Detener reproducción")}}}}};errorMessage?.let{Text(it,color=orange,modifier=Modifier.padding(12.dp))}}
        }
    }}}

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
    val playable = channels.filter { isSafeHttpsUrl(it.url) }
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


/** Accept only absolute HTTPS stream URLs without embedded credentials. */
private fun isSafeHttpsUrl(value:String):Boolean = runCatching {
    val uri=java.net.URI(value)
    uri.scheme.equals("https",true) && !uri.host.isNullOrBlank() && uri.userInfo==null
}.getOrDefault(false)
