package com.matuucrea.miltvplus.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.matuucrea.miltvplus.auth.SupabaseAuthRepository
import com.matuucrea.miltvplus.data.TmdbCatalogRepository
import com.matuucrea.miltvplus.data.TmdbTitle
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun TmdbCatalogScreen(
    isSeries: Boolean,
    auth: SupabaseAuthRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TmdbCatalogRepository() }
    val scope = rememberCoroutineScope()
    val favorites = remember {
        context.getSharedPreferences("miltvplus_tmdb_favorites", 0)
    }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var titles by remember { mutableStateOf<List<TmdbTitle>>(emptyList()) }
    var selected by remember { mutableStateOf<TmdbTitle?>(null) }
    var details by remember { mutableStateOf<JSONObject?>(null) }
    var seasonDetails by remember { mutableStateOf<JSONObject?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (isSeries) "tv_popular" else "movie_popular") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var favoriteIds by remember(isSeries) {
        mutableStateOf(favorites.getStringSet(if (isSeries) "series" else "movies", emptySet())?.toSet().orEmpty())
    }

    fun toggleFavorite(item: TmdbTitle) {
        val id = item.id.toString()
        val next = favoriteIds.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        favoriteIds = next
        favorites.edit().putStringSet(if (isSeries) "series" else "movies", next).apply()
    }

    fun load(action: String, search: String = "") {
        val token = accessToken
        if (token.isNullOrBlank()) {
            error = "Iniciá sesión en Cuenta para consultar el catálogo."
            return
        }
        scope.launch {
            loading = true
            error = null
            notice = null
            runCatching { repository.fetch(action, token, search) }
                .onSuccess { response ->
                    titles = repository.readTitles(response)
                    selected = null
                    details = null
                    seasonDetails = null
                    if (titles.isEmpty()) notice = "Todavía no hay resultados para esta búsqueda."
                }
                .onFailure { error = it.message ?: "No se pudo cargar el catálogo." }
            loading = false
        }
    }

    LaunchedEffect(isSeries) {
        val session = auth.restoreSession()
        accessToken = session?.accessToken
        if (session == null) error = "Iniciá sesión en Cuenta para acceder al catálogo de películas y series."
        else load(category)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
            Icon(if (isSeries) Icons.Default.Tv else Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isSeries) "Series" else "Películas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Catálogo de metadatos de TMDB · Argentina", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (isSeries) "Buscar series" else "Buscar películas") },
            trailingIcon = {
                IconButton(onClick = { load(if (isSeries) "search_tv" else "search_movie", query) }, enabled = query.trim().length >= 2) {
                    Icon(Icons.Default.Search, "Buscar")
                }
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val options = if (isSeries) listOf("tv_popular" to "Populares", "tv_on_the_air" to "En emisión")
                else listOf("movie_popular" to "Populares", "movie_top_rated" to "Mejor valoradas", "movie_upcoming" to "Próximos estrenos")
            options.forEach { (value, label) ->
                FilterChip(selected = category == value, onClick = { category = value; load(value) }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        selected?.let { item ->
            TmdbDetailPanel(
                item = item,
                details = details,
                isSeries = isSeries,
                isFavorite = item.id.toString() in favoriteIds,
                onToggleFavorite = { toggleFavorite(item) },
                onShare = {
                    val kind = if (isSeries) "Serie" else "Película"
                    val shareText = "$kind: ${item.title}\nInformación e imágenes: TMDB. MILTVPLUS no dispone todavía de un enlace autorizado de reproducción.\nhttps://www.themoviedb.org/${if (isSeries) "tv" else "movie"}/${item.id}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, item.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir ${item.title}"))
                },
                onBack = { selected = null; details = null; seasonDetails = null },
                seasonDetails = seasonDetails,
                onLoadSeason = { seasonNumber ->
                    val token = accessToken
                    if (!token.isNullOrBlank()) scope.launch {
                        loading = true
                        runCatching { repository.fetch("tv_season", token, id = item.id, season = seasonNumber) }
                            .onSuccess { seasonDetails = it }
                            .onFailure { error = it.message ?: "No se pudo cargar la temporada." }
                        loading = false
                    }
                }
            )
        } ?: LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(titles, key = { "${if (isSeries) "tv" else "movie"}-${it.id}" }) { item ->
                Card(Modifier.fillMaxWidth().clickable {
                    val token = accessToken ?: return@clickable
                    selected = item
                    details = null
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { repository.fetch(if (isSeries) "tv_details" else "movie_details", token, id = item.id) }
                            .onSuccess { details = it }
                            .onFailure { error = it.message ?: "No se pudo cargar la ficha." }
                        loading = false
                    }
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = "Póster de ${item.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(76.dp, 108.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(item.year ?: "Año no informado", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (item.rating > 0) Text("TMDB ★ ${"%.1f".format(item.rating)}/10", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Text(item.overview.ifBlank { "Sin sinopsis disponible." }, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toggleFavorite(item) }) {
                            Icon(if (item.id.toString() in favoriteIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Me gusta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TmdbDetailPanel(
    item: TmdbTitle,
    details: JSONObject?,
    isSeries: Boolean,
    seasonDetails: JSONObject?,
    onLoadSeason: (Int) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    val trailerKey = remember(details) {
        val videos = details?.optJSONObject("videos")?.optJSONArray("results")
        (0 until (videos?.length() ?: 0)).asSequence()
            .mapNotNull { videos?.optJSONObject(it) }
            .firstOrNull {
                it.optString("site").equals("YouTube", true) &&
                    it.optString("type").equals("Trailer", true) &&
                    it.optBoolean("official", false) &&
                    it.optString("key").matches(Regex("^[A-Za-z0-9_-]{6,20}$"))
            }?.optString("key")
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("← Volver al catálogo") }
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = "Imagen de ${item.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(190.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
        )
        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(item.year, if (item.rating > 0) "★ ${"%.1f".format(item.rating)}/10" else null).joinToString(" · "), color = MaterialTheme.colorScheme.primary)
        Text(item.overview.ifBlank { "Sin sinopsis disponible." }, style = MaterialTheme.typography.bodyMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleFavorite) {
                Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                Spacer(Modifier.width(6.dp))
                Text(if (isFavorite) "Me gusta" else "Me gusta")
            }
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(6.dp))
                Text("Compartir")
            }
            Button(onClick = {}, enabled = false) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Reproducir")
            }
        }
        Text(
            "La reproducción se habilitará cuando exista una fuente de vídeo autorizada para esta obra. No se inventan enlaces ni se extraen streams de terceros.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (trailerKey != null) {
            Text("Tráiler oficial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.Black, RoundedCornerShape(12.dp)),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadUrl("https://www.youtube-nocookie.com/embed/$trailerKey?playsinline=1&rel=0")
                    }
                },
                update = { view -> if (view.url?.contains(trailerKey) != true) view.loadUrl("https://www.youtube-nocookie.com/embed/$trailerKey?playsinline=1&rel=0") }
            )
        } else {
            Text("Todavía no hay un tráiler oficial de YouTube disponible en los datos de esta ficha.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        details?.optJSONArray("genres")?.let { genres ->
            val names = (0 until genres.length()).mapNotNull { genres.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
            if (names.isNotEmpty()) Text(names.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        details?.optJSONObject("credits")?.optJSONArray("cast")?.let { cast ->
            if (cast.length() > 0) {
                Text("Reparto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items((0 until minOf(cast.length(), 12)).mapNotNull { cast.optJSONObject(it) }) { person ->
                        Column(Modifier.width(112.dp)) {
                            AsyncImage(
                                model = person.optString("profile_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w185$it" },
                                contentDescription = person.optString("name"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(92.dp, 112.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            )
                            Text(person.optString("name"), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                            Text(person.optString("character"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
            }
        }
        if (isSeries) {
            details?.optJSONArray("seasons")?.let { seasons ->
                Text("Temporadas y episodios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((0 until seasons.length()).mapNotNull { seasons.optJSONObject(it) }) { season ->
                        val number = season.optInt("season_number", -1)
                        if (number >= 0) FilterChip(
                            selected = seasonDetails?.optInt("id") == season.optInt("id"),
                            onClick = { onLoadSeason(number) },
                            label = { Text(season.optString("name", "Temporada $number")) }
                        )
                    }
                }
                seasonDetails?.optJSONArray("episodes")?.let { episodes ->
                    if (episodes.length() > 0) {
                        Text("Episodios", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        (0 until episodes.length()).forEach { index ->
                            episodes.optJSONObject(index)?.let { episode ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("E${episode.optInt("episode_number")} · ${episode.optString("name")}", fontWeight = FontWeight.Medium)
                                    Text(episode.optString("overview").ifBlank { "Sin descripción disponible." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider()
                            }
                        }
                    } else Text("Esta temporada todavía no tiene episodios publicados en TMDB.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Los datos e imágenes describen la obra; no incluyen derechos ni enlaces para reproducirla.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
