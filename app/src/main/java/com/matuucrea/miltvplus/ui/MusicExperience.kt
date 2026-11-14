package com.matuucrea.miltvplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.matuucrea.miltvplus.data.RemoteCatalogEntry

private val MusicInk = Color(0xFF080914)
private val MusicViolet = Color(0xFF9B6BFF)
private val MusicPink = Color(0xFFFF68B4)

/** Premium music discovery for published, licensed catalog entries. */
@Composable
fun MusicExperience(
    entries: List<RemoteCatalogEntry>,
    loading: Boolean,
    error: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    player: Player,
    onPlay: (RemoteCatalogEntry, List<RemoteCatalogEntry>) -> Unit,
    onRefresh: () -> Unit,
) {
    var genre by remember { mutableStateOf("Todo") }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var currentTitle by remember(player) { mutableStateOf(player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                currentTitle = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val genres = listOf("Todo") + entries.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val visible = entries.filter { item ->
        (genre == "Todo" || item.category.equals(genre, true)) &&
            (query.isBlank() || item.title.contains(query, true) || item.subtitle.contains(query, true) || item.category.contains(query, true))
    }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090916), Color(0xFF120D24), MusicInk)))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MILTVPLUS SOUND", color = MusicPink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("Tu música, tu momento", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Un espacio para descubrir, escuchar y disfrutar sin apuro.", color = Color(0xFFC9C1D9), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Actualizar música", tint = MusicPink) }
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF39205D), Color(0xFF19132F), Color(0xFF35152F))))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✦  UN AMBIENTE HECHO PARA ESCUCHAR", color = Color(0xFFFFC5E3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Bajá el ruido.\nSubí el volumen.", color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                    Text("Elegí una canción del catálogo autorizado y dejá que la música acompañe tu día.", color = Color(0xFFE0D9EE), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { visible.firstOrNull { it.actionUrl != null }?.let { onPlay(it, visible.filter { track -> track.actionUrl != null }) } },
                        enabled = visible.any { it.actionUrl != null },
                        colors = ButtonDefaults.buttonColors(containerColor = MusicPink, contentColor = Color(0xFF210D20))
                    ) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Escuchar ahora")
                    }
                }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar canción, artista, álbum o estilo") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            genres.forEach { value ->
                FilterChip(selected = genre == value, onClick = { genre = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
            }
        }
        if (currentTitle.isNotBlank()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF211832)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = MusicPink, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("SONANDO AHORA", color = MusicPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(currentTitle, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    IconButton(onClick = { player.seekToPreviousMediaItem() }, enabled = player.hasPreviousMediaItem()) {
                        Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White)
                    }
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Pausar o reanudar", tint = Color.White)
                    }
                    IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem()) {
                        Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White)
                    }
                    IconButton(onClick = { player.stop(); player.clearMediaItems(); currentTitle = ""; isPlaying = false }) {
                        Icon(Icons.Default.Stop, "Detener", tint = Color.White)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Para descubrir", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${visible.size} publicaciones", color = Color(0xFFB7AFC8), style = MaterialTheme.typography.labelSmall)
        }
        when {
            loading && entries.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MusicPink) }
            error != null && entries.isEmpty() -> MusicEmptyState("No pudimos conectar con la música", "Revisá tu conexión e intentá actualizar. Tus listas no se modificaron.", error)
            visible.isEmpty() -> MusicEmptyState("Este espacio está esperando canciones", "Pronto vas a poder encontrar acá música publicada por MILTVPLUS. Volvé más tarde para descubrir nuevas publicaciones.", null)
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(visible, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF171523)), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().clickable(enabled = item.actionUrl != null) { onPlay(item, visible.filter { it.actionUrl != null }) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.artworkUrl != null) {
                                AsyncImage(model = item.artworkUrl, contentDescription = item.title, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                            } else {
                                Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MusicViolet, MusicPink))), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(item.subtitle.ifBlank { item.category }, color = Color(0xFFBEB6CD), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            IconButton(onClick = { if (item.actionUrl != null) onPlay(item, visible.filter { it.actionUrl != null }) }, enabled = item.actionUrl != null) {
                                Icon(Icons.Default.PlayCircleFilled, "Reproducir música", tint = MusicPink, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicEmptyState(title: String, description: String, detail: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Default.Headphones, null, tint = MusicViolet, modifier = Modifier.size(48.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Text(description, color = Color(0xFFC9C1D9), style = MaterialTheme.typography.bodySmall)
        detail?.let { Text(it, color = Color(0xFFFFA5C8), style = MaterialTheme.typography.labelSmall) }
    }
}
