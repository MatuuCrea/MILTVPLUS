package com.matuucrea.miltvplus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val PremiumNavy = Color(0xFF050B1B)
private val PremiumPanel = Color(0xFF101D38)
private val PremiumBlue = Color(0xFF087BFF)
private val PremiumCyan = Color(0xFF16C8F2)
private val PremiumPink = Color(0xFFE91E63)
private val PremiumGold = Color(0xFFFFC247)

private data class NavDestination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun PremiumSidebar(activeTab: String, onNavigate: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxHeight()) {
        val compact = maxWidth < 600.dp
        var expanded by remember(compact) { mutableStateOf(!compact) }
        var moreExpanded by remember { mutableStateOf(false) }
        val entries = listOf(
            NavDestination("Inicio", Icons.Default.Home),
            NavDestination("TV en Vivo", Icons.Default.LiveTv),
            NavDestination("Guía EPG", Icons.Default.CalendarMonth),
            NavDestination("Películas", Icons.Default.Movie),
            NavDestination("Series", Icons.Default.VideoLibrary),
            NavDestination("Deportes", Icons.Default.EmojiEvents),
            NavDestination("Infantil", Icons.Default.ChildCare),
            NavDestination("Documentales", Icons.Default.MenuBook),
            NavDestination("Música", Icons.Default.MusicNote),
            NavDestination("Países", Icons.Default.Public),
            NavDestination("Adultos 18+", Icons.Default.Lock),
            NavDestination("Favoritos", Icons.Default.Star),
            NavDestination("Configuración", Icons.Default.Settings)
        )
        val extras = listOf(
            NavDestination("Apps", Icons.Default.Apps),
            NavDestination("Fuentes", Icons.Default.PlaylistAdd),
            NavDestination("Cuenta", Icons.Default.AccountCircle)
        )
        Column(
            Modifier.width(if (expanded) 218.dp else 64.dp).fillMaxHeight()
                .background(Color(0xFF08060D))
                .padding(horizontal = 7.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
            ) {
                Icon(if (expanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                    contentDescription = "Desplegar menú", tint = PremiumPink)
                if (expanded) {
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("MIL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text("TV", color = PremiumCyan, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text("PLUS", color = PremiumPink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                        Text("Tu mundo en una sola app 🇦🇷", color = Color(0xFFB8AFC6), fontSize = 8.sp)
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF34283B))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                entries.forEach { item ->
                    val selected = activeTab == item.label ||
                        (item.label == "TV en Vivo" && activeTab in setOf("Catálogo", "TV")) ||
                        (item.label == "Guía EPG" && activeTab == "Catálogo") ||
                        (item.label == "Configuración" && activeTab == "Ajustes") ||
                        (item.label == "Adultos 18+" && activeTab == "Adultos")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(
                                    listOf(Color(0xFF94104F), Color(0xFF42102F))
                                ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { onNavigate(item.label) }
                            .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
                    ) {
                        Icon(item.icon, item.label,
                            tint = if (selected) Color(0xFFFF72B9) else Color(0xFFD6D0DE),
                            modifier = Modifier.size(20.dp))
                        if (expanded) {
                            Spacer(Modifier.width(10.dp))
                            Text(item.label, color = Color.White, fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { moreExpanded = !moreExpanded }
                        .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
                ) {
                    Icon(Icons.Default.MoreHoriz, "Más Opciones", tint = PremiumCyan, modifier = Modifier.size(20.dp))
                    if (expanded) {
                        Spacer(Modifier.width(10.dp))
                        Text("Más Opciones", color = Color.White, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(if (moreExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = Color.LightGray)
                    }
                }
                AnimatedVisibility(moreExpanded && expanded) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 8.dp)
                            .background(Color(0xFF11101A), RoundedCornerShape(10.dp)).padding(4.dp)
                    ) {
                        extras.forEach { item ->
                            TextButton(onClick = { onNavigate(item.label) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(item.icon, null, tint = PremiumCyan, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(item.label, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF49300D), Color(0xFF1C1020))))
                        .padding(9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = PremiumGold, modifier = Modifier.size(24.dp))
                    Text("MILTVPLUS Premium", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    Text("Más entretenimiento para vos...", color = PremiumGold, fontSize = 8.sp)
                    Text("🇦🇷", fontSize = 15.sp)
                }
            } else {
                Icon(Icons.Default.WorkspacePremium, "MILTVPLUS Premium", tint = PremiumGold)
            }
        }
    }
}

@Composable
fun PremiumAppTopBar(activeTab: String, onSearch: (String) -> Unit, onSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(30_000) }
    }
    val clock = remember(now) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "AR")).format(java.util.Date(now))
    }
    val date = remember(now) {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy", java.util.Locale("es", "AR"))
            .format(java.util.Date(now)).replaceFirstChar { it.uppercase() }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color(0xFF090711))) {
        val narrow = maxWidth < 520.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Column(Modifier.width(if (narrow) 58.dp else 112.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(clock, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (!narrow) Text(date, color = Color(0xFFB8AFC6), fontSize = 9.sp, maxLines = 1)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it; onSearch(it) },
                modifier = Modifier.weight(1f).heightIn(max = 48.dp),
                placeholder = { Text("Buscar en MILTVPLUS...", fontSize = 10.sp, maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
                singleLine = true, shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = PremiumPink, unfocusedBorderColor = Color(0xFF493D50),
                    focusedLeadingIconColor = PremiumPink, unfocusedLeadingIconColor = Color.LightGray
                )
            )
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.NotificationsNone, "Notificaciones", tint = Color.White)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Settings, "Configuración", tint = Color.White)
            }
            if (!narrow) Icon(Icons.Default.AccountCircle, "Perfil", tint = Color.White, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
fun PremiumHomeDashboard(
    countryName: String,
    countryFlag: String,
    channelCount: Int,
    featuredTitle: String?,
    loading: Boolean,
    errorMessage: String?,
    onNavigate: (String) -> Unit
) {
    val shortcuts = listOf(
        Triple("TV en Vivo", "Canales públicos y señales autorizadas", Icons.Default.LiveTv),
        Triple("Películas", "Estrenos y clásicos", Icons.Default.Movie),
        Triple("Series", "Temporadas y episodios", Icons.Default.VideoLibrary),
        Triple("Deportes", "Toda la pasión deportiva", Icons.Default.SportsSoccer),
        Triple("Infantil", "Contenido para chicos", Icons.Default.ChildCare),
        Triple("Países", "Explorá señales del mundo", Icons.Default.Public)
    )
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumNavy, Color(0xFF07152D), Color(0xFF030712))))
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 900.dp
            if (wide) {
                Row(Modifier.fillMaxWidth().heightIn(min = 300.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroPanel(Modifier.weight(3f), onNavigate)
                    CountryPanel(Modifier.weight(1f), countryName, countryFlag, channelCount, loading, onNavigate)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroPanel(Modifier.fillMaxWidth(), onNavigate)
                    CountryPanel(Modifier.fillMaxWidth(), countryName, countryFlag, channelCount, loading, onNavigate)
                }
            }
        }

        errorMessage?.let { message ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1720))
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFFB4AB))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("No se pudo actualizar el catálogo", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(message, color = Color(0xFFFFDAD6), fontSize = 11.sp)
                    }
                    TextButton(onClick = { onNavigate("TV en Vivo") }) {
                        Text("Reintentar", color = PremiumCyan)
                    }
                }
            }
        }

        Text("Explorá MILTVPLUS", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 720.dp) 6 else if (maxWidth >= 420.dp) 3 else 2
            val rows = shortcuts.chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, description, icon) ->
                            ShortcutCard(Modifier.weight(1f), label, description, icon) { onNavigate(label) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 900.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LivePreview(Modifier.weight(1.2f), countryName, channelCount, featuredTitle, loading, onNavigate)
                    EpgPreview(Modifier.weight(1f), onNavigate)
                    SettingsPreview(Modifier.weight(1f), onNavigate)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    LivePreview(Modifier.fillMaxWidth(), countryName, channelCount, featuredTitle, loading, onNavigate)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        EpgPreview(Modifier.weight(1f), onNavigate)
                        SettingsPreview(Modifier.weight(1f), onNavigate)
                    }
                }
            }
        }
        Text("MILTVPLUS · Hecho con identidad argentina 🇦🇷", Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF8295B4), fontSize = 10.sp)
    }
}

@Composable
private fun HeroPanel(modifier: Modifier, onNavigate: (String) -> Unit) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2852))) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 270.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF041A43), Color(0xFF075DB4), Color(0xFF06152F))))
                .padding(20.dp)
        ) {
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.78f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("🇦🇷  MILTVPLUS · ARGENTINA Y EL MUNDO", color = PremiumCyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Text("Bienvenido a", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("MILTVPLUS", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Box(Modifier.width(64.dp).height(4.dp).background(PremiumCyan, RoundedCornerShape(5.dp)))
                Text("La mejor experiencia de entretenimiento en Argentina y el mundo.", color = Color.White, fontSize = 14.sp)
                listOf("TV en vivo y señales públicas", "Películas, series y deportes", "Guía de programación y favoritos").forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = PremiumCyan, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(it, color = Color.White, fontSize = 12.sp)
                    }
                }
                Button(onClick = { onNavigate("TV en Vivo") }, colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Ver televisión")
                }
                Text("Viva Argentina. Siempre en tu corazón.", color = Color(0xFFFFE5A3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☀", color = PremiumGold, fontSize = 48.sp)
                Text("🇦🇷", fontSize = 42.sp)
            }
        }
    }
}

@Composable
private fun CountryPanel(modifier: Modifier, countryName: String, countryFlag: String, channelCount: Int, loading: Boolean, onNavigate: (String) -> Unit) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF08152D))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, null, tint = PremiumCyan)
                Spacer(Modifier.width(8.dp))
                Text("Países", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onNavigate("Países") }) { Icon(Icons.Default.Search, "Buscar país", tint = Color.White) }
            }
            Row(
                Modifier.fillMaxWidth().background(PremiumBlue.copy(alpha = .2f), RoundedCornerShape(11.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(countryFlag, fontSize = 26.sp)
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(countryName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (loading) "Actualizando catálogo…" else "$channelCount URLs HTTPS configuradas", color = PremiumCyan, fontSize = 10.sp)
                }
                Icon(Icons.Default.CheckCircle, null, tint = PremiumBlue)
            }
            Text("Explorá canales y contenidos por región", color = Color.LightGray, fontSize = 11.sp)
            OutlinedButton(onClick = { onNavigate("Países") }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumCyan)) {
                Icon(Icons.Default.Public, null); Spacer(Modifier.width(7.dp)); Text("Seleccionar país")
            }
        }
    }
}

@Composable
private fun ShortcutCard(modifier: Modifier, label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val color = when (label) {
        "Películas" -> Color(0xFF32166A)
        "Series" -> Color(0xFF07513C)
        "Deportes" -> Color(0xFF66320A)
        "Infantil" -> Color(0xFF5C174B)
        "Países" -> Color(0xFF064B65)
        else -> Color(0xFF073D83)
    }
    Card(modifier.clickable(onClick = onClick).heightIn(min = 104.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text(description, color = Color(0xFFD8E4F6), fontSize = 9.sp, maxLines = 2)
        }
    }
}

@Composable
private fun LivePreview(modifier: Modifier, country: String, count: Int, featured: String?, loading: Boolean, onNavigate: (String) -> Unit) {
    Card(modifier.clickable { onNavigate("TV en Vivo") }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PremiumPanel)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LiveTv, null, tint = PremiumCyan)
                Spacer(Modifier.width(7.dp))
                Text("TV en Vivo", color = Color.White, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("$count URLs HTTPS", color = PremiumCyan, fontSize = 10.sp)
            }
            Text(country.uppercase(), color = PremiumCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(featured ?: if (loading) "Conectando al catálogo…" else "Señales públicas y autorizadas", color = Color.LightGray, fontSize = 12.sp)
            Text("Abrir canales  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EpgPreview(modifier: Modifier, onNavigate: (String) -> Unit) {
    Card(modifier.clickable { onNavigate("EPG") }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PremiumPanel)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Default.CalendarMonth, null, tint = PremiumCyan)
            Text("Guía de programación", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("EPG · horarios publicados", color = Color.LightGray, fontSize = 10.sp)
            Text("Abrir guía  →", color = PremiumCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsPreview(modifier: Modifier, onNavigate: (String) -> Unit) {
    Card(modifier.clickable { onNavigate("Configuración") }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PremiumPanel)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Default.Settings, null, tint = PremiumCyan)
            Text("Configuración", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Idioma · calidad · reproducción", color = Color.LightGray, fontSize = 10.sp)
            Text("Personalizar  →", color = PremiumCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}


/**
 * Adult area navigation modeled on the supplied MILTVPLUS reference:
 * a compact expandable rail on phones and a persistent sidebar on wide screens.
 */
@Composable
fun PremiumAdultWorkspace(
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF05030A))
    ) {
        val wide = maxWidth >= 760.dp
        var expanded by remember(wide) { mutableStateOf(wide) }
        val destinations = listOf(
            "Inicio" to Icons.Default.Home,
            "TV en Vivo" to Icons.Default.LiveTv,
            "Películas" to Icons.Default.Movie,
            "Series" to Icons.Default.VideoLibrary,
            "Deportes" to Icons.Default.EmojiEvents,
            "Infantil" to Icons.Default.ChildCare,
            "Documentales" to Icons.Default.MenuBook,
            "Música" to Icons.Default.MusicNote,
            "Adultos 18+" to Icons.Default.Favorite,
            "Favoritos" to Icons.Default.Star,
            "Configuración" to Icons.Default.Settings
        )
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(if (expanded) 196.dp else 58.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF09050E))
                    .padding(horizontal = 7.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
                ) {
                    Icon(
                        if (expanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                        contentDescription = "Expandir o contraer navegación",
                        tint = PremiumPink
                    )
                    if (expanded) {
                        Spacer(Modifier.width(10.dp))
                        Text("MILTVPLUS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
                destinations.forEach { (label, icon) ->
                    val selected = label == "Adultos 18+"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(
                                    listOf(Color(0xFF8B0750), Color(0xFF3D092E))
                                ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { onNavigate(label) }
                            .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = if (selected) Color(0xFFFF69B4) else Color(0xFFD9D5E2))
                        if (expanded) {
                            Spacer(Modifier.width(11.dp))
                            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (expanded) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF39200A), Color(0xFF170D1B))))
                            .padding(11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = PremiumGold, modifier = Modifier.size(25.dp))
                        Text("MILTVPLUS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text("Experiencia Premium", color = PremiumGold, fontSize = 10.sp)
                    }
                } else {
                    Icon(Icons.Default.WorkspacePremium, null, tint = PremiumGold)
                }
            }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Color(0xFF100511), Color(0xFF05030A))))
            ) {
                content()
            }
        }
    }
}
