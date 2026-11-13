package com.matuucrea.miltvplus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PremiumNavy = Color(0xFF050B1B)
private val PremiumPanel = Color(0xFF101D38)
private val PremiumBlue = Color(0xFF087BFF)
private val PremiumCyan = Color(0xFF16C8F2)
private val PremiumPink = Color(0xFFE91E63)
private val PremiumGold = Color(0xFFFFC247)

private data class NavDestination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun PremiumNavigationHeader(activeTab: String, onNavigate: (String) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val destinations = listOf(
        NavDestination("Inicio", Icons.Default.Home),
        NavDestination("TV en Vivo", Icons.Default.LiveTv),
        NavDestination("Películas", Icons.Default.Movie),
        NavDestination("Series", Icons.Default.VideoLibrary),
        NavDestination("Deportes", Icons.Default.SportsSoccer),
        NavDestination("Infantil", Icons.Default.ChildCare),
        NavDestination("Apps", Icons.Default.Apps),
        NavDestination("Países", Icons.Default.Public),
        NavDestination("EPG", Icons.Default.CalendarMonth),
        NavDestination("Adultos 18+", Icons.Default.Favorite),
        NavDestination("Configuración", Icons.Default.Settings)
    )
    Column(
        Modifier.fillMaxWidth().background(PremiumNavy).animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { menuExpanded = !menuExpanded }) {
                Icon(if (menuExpanded) Icons.Default.Close else Icons.Default.Menu, "Expandir o contraer menú", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("MILTVPLUS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Tu TV, en todas partes  🇦🇷", color = PremiumCyan, fontSize = 10.sp)
            }
            Text("●", color = PremiumCyan, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text("EN LÍNEA", color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            destinations.take(7).forEach { destination ->
                val selected = activeTab == destination.label ||
                    (destination.label == "TV en Vivo" && activeTab == "Catálogo") ||
                    (destination.label == "Configuración" && activeTab == "Ajustes") ||
                    (destination.label == "Adultos 18+" && activeTab == "Adultos")
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination.label) },
                    icon = { Icon(destination.icon, null) },
                    label = { Text(destination.label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (destination.label == "Adultos 18+") PremiumPink else PremiumCyan,
                        selectedTextColor = if (destination.label == "Adultos 18+") PremiumPink else Color.White,
                        indicatorColor = Color(0xFF173A70)
                    ),
                    modifier = Modifier.widthIn(min = 72.dp)
                )
            }
        }
        AnimatedVisibility(menuExpanded) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(PremiumPanel, RoundedCornerShape(16.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                destinations.drop(7).forEach { destination ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            menuExpanded = false
                            onNavigate(destination.label)
                        }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(destination.icon, null, tint = if (destination.label == "Adultos 18+") PremiumPink else PremiumCyan)
                        Spacer(Modifier.width(12.dp))
                        Text(destination.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                    }
                }
            }
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
    onNavigate: (String) -> Unit
) {
    val shortcuts = listOf(
        Triple("TV en Vivo", "Canales públicos y señales autorizadas", Icons.Default.LiveTv),
        Triple("Películas", "Estrenos y clásicos", Icons.Default.Movie),
        Triple("Series", "Temporadas y episodios", Icons.Default.VideoLibrary),
        Triple("Deportes", "Toda la pasión deportiva", Icons.Default.SportsSoccer),
        Triple("Infantil", "Contenido para chicos", Icons.Default.ChildCare),
        Triple("Países", "Explorá el mundo", Icons.Default.Public)
    )
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(PremiumNavy, Color(0xFF07152D), Color(0xFF030712)))
        ).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2852))
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 230.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF06265C), Color(0xFF087BCE), Color(0xFF071A39))))
                    .padding(20.dp)
            ) {
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🇦🇷  MILTVPLUS · ARGENTINA Y EL MUNDO", color = PremiumCyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Bienvenido a", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("MILTVPLUS", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("Tu televisión, tu cultura y tus historias en un solo lugar.", color = Color.White, fontSize = 14.sp)
                    Text("Viva Argentina. Siempre en tu corazón.", color = Color(0xFFFFE5A3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { onNavigate("TV en Vivo") },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ver televisión")
                    }
                }
                Text("☀", Modifier.align(Alignment.TopEnd), color = PremiumGold, fontSize = 48.sp)
                Text("🇦🇷", Modifier.align(Alignment.BottomEnd), fontSize = 54.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Explorá MILTVPLUS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Accesos directos a cada experiencia", color = Color.LightGray, fontSize = 12.sp)
            }
            TextButton(onClick = { onNavigate("Países") }) { Text("$countryFlag  $countryName", color = PremiumCyan) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shortcuts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, description, icon) ->
                        Card(
                            modifier = Modifier.weight(1f).heightIn(min = 112.dp).clickable { onNavigate(label) },
                            shape = RoundedCornerShape(17.dp),
                            colors = CardDefaults.cardColors(containerColor = when (label) {
                                "Películas" -> Color(0xFF27165A)
                                "Series" -> Color(0xFF07513C)
                                "Deportes" -> Color(0xFF66320A)
                                "Infantil" -> Color(0xFF5C174B)
                                "Países" -> Color(0xFF064B65)
                                else -> Color(0xFF073D83)
                            })
                        ) {
                            Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp))
                                Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                Text(description, color = Color(0xFFD8E4F6), fontSize = 10.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth().clickable { onNavigate("TV en Vivo") },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PremiumPanel)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LiveTv, null, tint = PremiumCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("TV EN VIVO · $countryName", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Text("$channelCount señales", color = PremiumCyan, fontSize = 11.sp)
                }
                Text(
                    featuredTitle ?: if (loading) "Conectando con el catálogo publicado…" else "Elegí una señal pública o autorizada del catálogo",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Abrir televisión en vivo", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, tint = PremiumCyan)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f).clickable { onNavigate("EPG") }, colors = CardDefaults.cardColors(containerColor = PremiumPanel)) {
                Column(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = PremiumCyan)
                    Text("Guía EPG", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Programación disponible", color = Color.LightGray, fontSize = 10.sp)
                }
            }
            Card(Modifier.weight(1f).clickable { onNavigate("Configuración") }, colors = CardDefaults.cardColors(containerColor = PremiumPanel)) {
                Column(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Settings, null, tint = PremiumCyan)
                    Text("Configuración", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Personalizá tu experiencia", color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
        Text("MILTVPLUS · Hecho con identidad argentina 🇦🇷", Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF8295B4), fontSize = 10.sp)
    }
}
