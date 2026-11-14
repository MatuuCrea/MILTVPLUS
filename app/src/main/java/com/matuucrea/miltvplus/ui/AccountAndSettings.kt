package com.matuucrea.miltvplus.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.matuucrea.miltvplus.auth.AuthSession
import com.matuucrea.miltvplus.auth.SupabaseAuthRepository
import com.matuucrea.miltvplus.data.AppDirectory
import com.matuucrea.miltvplus.data.CountryOption
import com.matuucrea.miltvplus.data.Channel
import com.matuucrea.miltvplus.data.RemotePlaylistDownloader
import com.matuucrea.miltvplus.data.RemoteCatalogEntry
import com.matuucrea.miltvplus.data.SupabaseCatalogRepository
import com.matuucrea.miltvplus.data.SupabaseUserRepository
import com.matuucrea.miltvplus.data.UserPlaylistSource
import com.matuucrea.miltvplus.security.ParentalAccessStore
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    auth: SupabaseAuthRepository,
    userRepository: SupabaseUserRepository,
    onSessionChanged: (AuthSession?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var session by remember { mutableStateOf<AuthSession?>(auth.currentSession()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        session = auth.restoreSession()
        onSessionChanged(session)
    }

    Column(
        Modifier.fillMaxWidth().padding(18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tu cuenta MILTVPLUS", style = MaterialTheme.typography.headlineSmall)
        if (session != null) {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text(session?.email ?: "Cuenta conectada", style = MaterialTheme.typography.titleMedium)
                    Text("Sesión protegida con Android Keystore. Favoritos, preferencias y fuentes se sincronizan bajo RLS.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        newPassword, { newPassword = it }, Modifier.fillMaxWidth(),
                        label = { Text("Nueva contraseña") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true
                    )
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            runCatching { auth.updatePassword(newPassword) }
                                .onSuccess { newPassword = ""; message = "Contraseña actualizada." }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }, enabled = !busy && newPassword.length >= 8) { Text("Actualizar contraseña") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            runCatching { auth.signOut() }.onFailure { message = it.message }
                            session = null
                            onSessionChanged(null)
                            busy = false
                        }
                    }, enabled = !busy) { Text("Cerrar sesión") }
                }
            }
            Button(onClick = {
                scope.launch {
                    busy = true
                    runCatching { userRepository.favoriteIds() }
                        .onSuccess { message = "Favoritos sincronizados: ${it.size}" }
                        .onFailure { message = it.message }
                    busy = false
                }
            }, enabled = !busy) {
                Icon(Icons.Default.CloudSync, null)
                Spacer(Modifier.width(8.dp))
                Text("Comprobar sincronización")
            }
        } else {
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo electrónico") }, singleLine = true)
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(),
                label = { Text("Contraseña (mínimo 8 caracteres)") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true
            )
            Button(onClick = {
                scope.launch {
                    busy = true
                    message = null
                    runCatching { if (createAccount) auth.signUp(email, password) else auth.signIn(email, password) }
                        .onSuccess { result ->
                            session = result
                            onSessionChanged(result)
                            message = if (result == null) {
                                "Cuenta creada. Revisá tu correo y confirmá la dirección para iniciar sesión."
                            } else "Sesión iniciada correctamente."
                        }
                        .onFailure { message = it.message ?: "No se pudo completar la operación." }
                    busy = false
                }
            }, enabled = !busy && email.contains("@") && password.length >= 8, modifier = Modifier.fillMaxWidth()) {
                Text(if (createAccount) "Crear cuenta" else "Iniciar sesión")
            }
            TextButton(onClick = { createAccount = !createAccount; message = null }) {
                Text(if (createAccount) "Ya tengo una cuenta · Iniciar sesión" else "Crear una cuenta nueva")
            }
            TextButton(onClick = {
                scope.launch {
                    busy = true
                    runCatching { auth.sendPasswordRecovery(email) }
                        .onSuccess { message = "Si el correo está registrado, Supabase enviará instrucciones de recuperación." }
                        .onFailure { message = it.message }
                    busy = false
                }
            }, enabled = !busy && email.contains("@")) { Text("Recuperar contraseña") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Para confirmar correos y recuperar contraseñas, agregá miltvplus://auth/callback a la lista Redirect URLs de Supabase Auth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsScreen(
    onThemeChanged: (String, String) -> Unit,
    userRepository: SupabaseUserRepository,
    isSignedIn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
    onMusicVolumeChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miltvplus_preferences", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }
    var subtitles by remember { mutableStateOf(prefs.getBoolean("subtitles", true)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var autoplay by remember { mutableStateOf(prefs.getBoolean("autoplay", false)) }
    var language by remember { mutableStateOf(prefs.getString("language", "es") ?: "es") }
    var audioLanguage by remember { mutableStateOf(prefs.getString("audio_language", "original") ?: "original") }
    var accent by remember { mutableStateOf(prefs.getString("accent_color", "electric-blue") ?: "electric-blue") }
    var quality by remember { mutableStateOf(prefs.getString("video_quality", "auto") ?: "auto") }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", false)) }
    var musicVolume by remember { mutableFloatStateOf(prefs.getFloat("music_volume", 1f).coerceIn(0f, 1f)) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun persist() {
        prefs.edit()
            .putString("theme", theme)
            .putBoolean("subtitles", subtitles)
            .putBoolean("notifications", notifications)
            .putBoolean("autoplay", autoplay)
            .putString("language", language)
            .putString("audio_language", audioLanguage)
            .putString("accent_color", accent)
            .putString("video_quality", quality)
            .putBoolean("keep_screen_on", keepScreenOn)
            .putFloat("music_volume", musicVolume)
            .apply()
        onThemeChanged(theme, accent)
        onKeepScreenOnChanged(keepScreenOn)
        onMusicVolumeChanged(musicVolume)
        if (isSignedIn) scope.launch {
            runCatching {
                userRepository.savePreferences(
                    theme,
                    prefs.getString("country_code", "AR"),
                    language,
                    subtitles,
                    notifications,
                    autoplay,
                    accent,
                    audioLanguage.takeIf { it != "original" }
                )
            }.onSuccess { message = "Preferencias sincronizadas." }
                .onFailure { message = "Guardado local; sincronización pendiente: ${it.message}" }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall)
        Text("General", style = MaterialTheme.typography.titleMedium)
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Sistema").forEach { (value, label) ->
                FilterChip(selected = theme == value, onClick = { theme = value; persist() }, label = { Text(label) })
            }
        }
        Text("Reproductor", style = MaterialTheme.typography.titleMedium)
        Text("Calidad de Video", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Automática", "high" to "Alta", "medium" to "Media", "low" to "Baja").forEach { (value, label) ->
                FilterChip(selected = quality == value, onClick = { quality = value; persist() }, label = { Text(label) })
            }
        }
        Text("Color de acento", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("electric-blue" to "Azul eléctrico", "violet" to "Violeta", "emerald" to "Verde").forEach { (value, label) ->
                FilterChip(selected = accent == value, onClick = { accent = value; persist() }, label = { Text(label) })
            }
        }
        HorizontalDivider()
        SettingSwitch("Subtítulos habilitados", subtitles) { subtitles = it; persist() }
        SettingSwitch("Preferencia de notificaciones", notifications) { notifications = it; persist() }
        SettingSwitch("Reproducción automática", autoplay) { autoplay = it; persist() }
        HorizontalDivider()
        Text("Reproducción y dispositivo", style = MaterialTheme.typography.titleMedium)
        SettingSwitch("Mantener pantalla encendida", keepScreenOn) { keepScreenOn = it; persist() }
        Text("Volumen de reproducción", style = MaterialTheme.typography.titleSmall)
        Slider(value = musicVolume, onValueChange = { musicVolume = it; onMusicVolumeChanged(it) }, onValueChangeFinished = { persist() }, valueRange = 0f..1f)
        Text((musicVolume * 100).toInt().toString() + "% · Se aplica al reproductor integrado de MILTVPLUS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Idioma de interfaz", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("es" to "Español", "en" to "English", "pt" to "Português").forEach { (value, label) ->
                FilterChip(selected = language == value, onClick = { language = value; persist() }, label = { Text(label) })
            }
        }
        Text("Idioma de audio preferido", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("original" to "Original", "es" to "Español", "en" to "English", "pt" to "Português").forEach { (value, label) ->
                FilterChip(selected = audioLanguage == value, onClick = { audioLanguage = value; persist() }, label = { Text(label) })
            }
        }
        Text("La preferencia de notificaciones se sincroniza; la entrega de avisos push/reminders requiere un servicio de eventos aún no desplegado. Las pistas de audio y subtítulos dependen de cada proveedor. Estos ajustes seleccionan la preferencia; no crean pistas que el stream no incluya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider()
        Text("EPG · Países y Canales · Favoritos", style = MaterialTheme.typography.titleMedium)
        Text("La guía, los países y los favoritos se muestran según los datos publicados y disponibles en tu cuenta. Los filtros regionales no amplían derechos de reproducción.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
        SettingSwitch("Preferencia de notificaciones", notifications) { notifications = it; persist() }
        Text("Acerca de", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        Text("Privacidad y seguridad", style = MaterialTheme.typography.titleMedium)
        Text("Las sesiones Android se cifran con Android Keystore; los datos de cuenta se aíslan con RLS. No se integra publicidad ni analítica de terceros en esta versión. Podés cerrar sesión desde Cuenta y administrar el acceso +18 desde su sección.", style = MaterialTheme.typography.bodySmall)
        Text("Créditos de catálogo: This product uses the TMDB API but is not endorsed or certified by TMDB. TMDB aporta metadatos e imágenes, no derechos de reproducción. Más información: themoviedb.org/about/logos-attribution.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        AppUpdateSection()
        OutlinedButton(onClick = {
            theme = "dark"; subtitles = true; notifications = true; autoplay = false
            language = "es"; audioLanguage = "original"; accent = "electric-blue"
            keepScreenOn = false; musicVolume = 1f; quality = "auto"
            persist()
            message = "Configuración restablecida."
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.RestartAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("Restablecer Configuración")
        }
        Text("Accesibilidad", style = MaterialTheme.typography.titleMedium)
        Text("Se respeta la escala de fuente y las opciones de accesibilidad del sistema Android. La navegación por control remoto usa grupos de foco Compose; requiere pruebas físicas en Android TV.", style = MaterialTheme.typography.bodySmall)
        Text("Android móvil, Android TV y TV Box Android comparten este cliente. El portal web oficial distribuye información y descargas; no es una aplicación de streaming ni una PWA.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@Composable
fun CountryPickerScreen(channelCounts: Map<String, Int> = emptyMap(), onCountrySelected: (CountryOption) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miltvplus_preferences", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(prefs.getString("country_code", "AR") ?: "AR") }
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Países y regiones", style = MaterialTheme.typography.headlineSmall)
        Text("El país filtra el catálogo; no modifica licencias ni habilita contenidos no disponibles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar país") }, singleLine = true)
        LazyColumn {
            items(AppDirectory.countries.filter { it.name.contains(search, true) || it.code.contains(search, true) }, key = { it.code }) { country ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selected = country.code
                        prefs.edit().putString("country_code", country.code).apply()
                        onCountrySelected(country)
                    }.padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(country.flag, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(country.name)
                        Text(channelCounts[country.code]?.let { "Canales publicados: $it" } ?: "Canales publicados: —", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected == country.code) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
        }
        val phrase = when (selected) {
            "AR" -> "Argentina · Siempre en tu corazón 🇦🇷"
            "US" -> "Estados Unidos · Un mundo de historias 🇺🇸"
            "ES" -> "España · Cultura que nos une 🇪🇸"
            "MX" -> "México · Tradición y emoción 🇲🇽"
            "CO" -> "Colombia · Pasión que se comparte 🇨🇴"
            "CL" -> "Chile · Un país, mil paisajes 🇨🇱"
            "BR" -> "Brasil · Ritmo y alegría 🇧🇷"
            else -> AppDirectory.countries.firstOrNull { it.code == selected }?.let { "${it.name} · Tu cultura en pantalla ${it.flag}" } ?: "Tu mundo en MILTVPLUS"
        }
        Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2852))) {
            Text(phrase, Modifier.padding(18.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AdultAccessScreen(
    userRepository: SupabaseUserRepository,
    isSignedIn: Boolean,
    countryCode: String,
    onPlay: (RemoteCatalogEntry) -> Unit
) {
    val context = LocalContext.current
    val screenScrollState = rememberScrollState()
    val screenScope = rememberCoroutineScope()
    val store = remember { ParentalAccessStore(context) }
    val catalog = remember { SupabaseCatalogRepository() }
    var ageConfirmed by remember { mutableStateOf(store.ageConfirmed) }
    var pinExists by remember { mutableStateOf(store.hasPin) }
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var adultItems by remember { mutableStateOf<List<RemoteCatalogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedAdultCategory by remember { mutableStateOf("Todos") }
    var unavailableCategory by remember { mutableStateOf<String?>(null) }
    val adultCategories = listOf("Todos", "Romance", "Eróticos", "Parejas", "Hot", "Premium", "Solo Adultos")

    LaunchedEffect(ageConfirmed, isSignedIn) {
        if (ageConfirmed && isSignedIn) {
            runCatching { userRepository.confirmAdultAccess() }
                .onFailure { message = "La confirmación local está guardada; no se pudo sincronizar: ${it.message}" }
        }
    }
    var catalogRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(unlocked, countryCode, catalogRefresh) {
        if (unlocked) {
            var firstLoad = true
            while (true) {
                // Keep existing cards visible during background refreshes; show the spinner
                // only on the first load or when the catalog is currently empty.
                if (firstLoad || adultItems.isEmpty()) loading = true
                catalog.fetchPublished("catalog_items", countryCode)
                    .onSuccess { rows ->
                        adultItems = rows.filter {
                            val rating = it.ageRating?.trim()?.uppercase().orEmpty()
                            rating.contains("18") || rating in setOf("NC-17","R18","TV-MA","MA","AO","ADULT","ADULTS ONLY")
                        }
                        message = null
                    }
                    .onFailure { message = "No se pudo actualizar el catálogo +18. Se conserva el contenido anterior si estaba disponible: ${it.message}" }
                loading = false
                firstLoad = false
                // Refresh every five minutes while the protected section remains unlocked.
                kotlinx.coroutines.delay(5 * 60 * 1000L)
            }
        } else {
            adultItems = emptyList()
            loading = false
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF210619), Color(0xFF09030D), Color(0xFF05030A))))
            .padding(16.dp).verticalScroll(screenScrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF260719))
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF570B36), Color(0xFF21051E), Color(0xFF090611))))
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                Text("♡       ♡       ♡", Modifier.align(Alignment.TopEnd), color = Color(0xFFFF4D9D).copy(alpha = .32f), fontSize = 24.sp)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 430.dp
                    if (compact) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(82.dp).background(Brush.radialGradient(listOf(Color(0xFFFF4D9D), Color(0xFF4B092F), Color(0xFF180512))), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Text("18+", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                            }
                            Text("SOLO PARA ADULTOS", color = Color(0xFFFFB7D7), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFC247), modifier = Modifier.size(25.dp))
                            Text("Contenido Adulto", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                            Text("Este espacio está pensado para disfrutarse con tranquilidad, respeto y privacidad. Gracias por cuidar tu acceso y acompañarnos.", color = Color(0xFFFFD8E9), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                message = when {
                                    !ageConfirmed -> "Confirmá que tenés 18 años o más en el control de acceso."
                                    !pinExists -> "Creá tu PIN parental de seis dígitos para continuar."
                                    !unlocked -> "Ingresá tu PIN parental de seis dígitos para desbloquear."
                                    else -> "La sección +18 ya está desbloqueada."
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E78)), shape = RoundedCornerShape(24.dp)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Ingresar Ahora", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(88.dp).background(Brush.radialGradient(listOf(Color(0xFFFF4D9D), Color(0xFF4B092F), Color(0xFF180512))), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                    Text("18+", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
                                }
                                Text("Solo para adultos", color = Color(0xFFFFB7D7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFC247), modifier = Modifier.size(26.dp))
                                Text("Contenido Adulto", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                                Text("Disfrutá de una experiencia cuidada, privada y clasificada +18.", color = Color(0xFFFFD8E9), style = MaterialTheme.typography.bodySmall)
                                Text("ACCESO PRIVADO · MAYORES DE 18", color = Color(0xFFFF79B7), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                Button(onClick = {
                                    screenScope.launch { screenScrollState.animateScrollTo(screenScrollState.maxValue) }
                                    message = when {
                                        !ageConfirmed -> "Confirmá que tenés 18 años o más en el control de acceso."
                                        !pinExists -> "Creá tu PIN parental de seis dígitos para continuar."
                                        !unlocked -> "Ingresá tu PIN parental de seis dígitos para desbloquear."
                                        else -> "La sección +18 ya está desbloqueada."
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E78)), shape = RoundedCornerShape(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Ingresar Ahora", fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("♡", color = Color(0xFFFF4D9D), fontSize = 38.sp)
                        }
                    }
                }
            }
        }
        Text("Explorá por categoría", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        // Seis accesos visuales de la referencia. "Todos" queda como filtro auxiliar.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 760.dp) 6 else 3
            val tiles = adultCategories.filterNot { it == "Todos" }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { category ->
                            val (symbol, tileColor) = when (category) {
                                "Romance" -> "💋" to Color(0xFF6A1454)
                                "Eróticos" -> "🔥" to Color(0xFF65152F)
                                "Parejas" -> "♡" to Color(0xFF511044)
                                "Hot" -> "⭐" to Color(0xFF5C183C)
                                "Premium" -> "◇" to Color(0xFF102E70)
                                else -> "18+" to Color(0xFF68152A)
                            }
                            Card(
                                onClick = {
                                    selectedAdultCategory = category
                                    if (unlocked && category != "Solo Adultos") {
                                        val available = adultItems.any {
                                            it.category.contains(category, ignoreCase = true) ||
                                                it.title.contains(category, ignoreCase = true)
                                        }
                                        if (!available) unavailableCategory = category
                                    } else if (unlocked && adultItems.isEmpty()) {
                                        unavailableCategory = category
                                    }
                                },
                                modifier = Modifier.weight(1f).height(104.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = CardDefaults.cardColors(containerColor = tileColor)
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(symbol, color = Color(0xFFFF78B9), fontSize = 27.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(5.dp))
                                    Text(category, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF1E7A), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recomendados para vos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (!unlocked) {
                Text("Desbloqueá la sección con tu PIN para ver recomendaciones del catálogo publicado.", color = Color(0xFFFFD8E9), style = MaterialTheme.typography.bodySmall)
            } else if (adultItems.isEmpty()) {
                Text("Todavía no hay títulos +18 publicados y verificados para esta región.", color = Color(0xFFFFD8E9), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(adultItems.take(4), key = { it.id }) { item ->
                        Card(modifier = Modifier.width(174.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B0B19))) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(112.dp).background(Brush.linearGradient(listOf(Color(0xFF5A1647), Color(0xFF170D2C))),), contentAlignment = Alignment.Center) {
                                    item.artworkUrl?.takeIf(::isSafeAdultArtworkUrl)?.let { artwork ->
                                        AsyncImage(model = artwork, contentDescription = "Portada de ${item.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } ?: Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6DAF), modifier = Modifier.size(34.dp))
                                    Text("18+", Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xFFB30B45), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                                Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
                                        Text(item.category.ifBlank { item.ageRating ?: "18+" }, color = Color(0xFFFF9CCB), fontSize = 9.sp, maxLines = 1)
                                    }
                                    if (item.actionUrl?.let(::isSafeAdultArtworkUrl) == true) {
                                        IconButton(onClick = { onPlay(item) }) { Icon(Icons.Default.PlayCircleFilled, "Reproducir ${item.title}", tint = Color(0xFFFF4D9D)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FilterChip(
            selected = selectedAdultCategory == "Todos",
            onClick = { selectedAdultCategory = "Todos" },
            label = { Text("Ver todas las categorías") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFB30B64),
                selectedLabelColor = Color.White,
                labelColor = Color(0xFFFFD8E9)
            )
        )
        Text("Solo contenido publicado y clasificado por edad. No se muestran canales de televisión ni enlaces inventados.", color = Color(0xFFFFD8E9), style = MaterialTheme.typography.bodySmall)
        Text("El acceso requiere declaración de mayoría de edad y un PIN parental de seis dígitos. La confirmación no reemplaza los controles legales de edad exigibles en cada país.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !ageConfirmed -> {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                Text("Confirmá que tenés 18 años o más para continuar.")
                Button(onClick = { store.confirmAge(); ageConfirmed = true }) { Text("Confirmo que tengo 18 años o más") }
            }
            !pinExists -> {
                Text("Creá un PIN parental para proteger esta sección.")
                OutlinedTextField(pin, { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pin = it }, label = { Text("Nuevo PIN de 6 dígitos") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    runCatching { store.setPin(pin) }
                        .onSuccess { pinExists = true; pin = ""; message = "PIN guardado. Ingresalo para desbloquear." }
                        .onFailure { message = it.message }
                }, enabled = pin.length == 6) { Text("Guardar PIN") }
            }
            !unlocked -> {
                OutlinedTextField(pin, { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pin = it }, label = { Text("PIN parental") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    unlocked = store.verifyPin(pin)
                    pin = ""
                    if (!unlocked) message = "PIN incorrecto o acceso temporalmente bloqueado."
                }, enabled = pin.length == 6) { Text("Desbloquear sección") }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Sección desbloqueada · Catálogo clasificado +18", modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { catalogRefresh++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo +18", tint = Color(0xFFFF9CCB))
                    }
                }
                if (loading) CircularProgressIndicator()
                else {
                    val categorizedItems = when (selectedAdultCategory) {
                        "Todos", "Solo Adultos" -> adultItems
                        else -> adultItems.filter { it.category.contains(selectedAdultCategory, ignoreCase = true) || it.title.contains(selectedAdultCategory, ignoreCase = true) }
                    }
                    if (categorizedItems.isEmpty()) {
                        Text(
                            if (adultItems.isEmpty()) "Todavía no hay títulos +18 publicados y verificados para esta región."
                            else "No hay títulos publicados dentro de «$selectedAdultCategory». Elegí otra categoría para ver el catálogo disponible.",
                            color = Color(0xFFFFD8E9)
                        )
                    } else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(categorizedItems, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                item.artworkUrl?.takeIf { isSafeAdultArtworkUrl(it) }?.let { artwork ->
                                    AsyncImage(
                                        model = artwork,
                                        contentDescription = "Imagen de ${item.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 76.dp, height = 104.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                                    Text(item.ageRating ?: "18+", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (item.actionUrl?.let(::isSafeAdultArtworkUrl) == true) {
                                    TextButton(onClick = { onPlay(item) }) { Text("Reproducir") }
                                } else {
                                    Text("No disponible", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9CCB))
                                }
                            }
                        }
                    }
                    }
                    OutlinedButton(
                        onClick = { unlocked = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9CCB))
                    ) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(6.dp)); Text("Bloquear ahora") }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 720.dp) 4 else 2
            val trustItems = listOf(
                Triple(Icons.Default.Shield, "Contenido seguro", "y controlado"),
                Triple(Icons.Default.Lock, "Acceso exclusivo", "para mayores +18"),
                Triple(Icons.Default.WorkspacePremium, "Calidad premium", "según el catálogo"),
                Triple(Icons.Default.Favorite, "Experiencia privada", "con control parental")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                trustItems.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (icon, title, subtitle) ->
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF170D19)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = Color(0xFFFFC247), modifier = Modifier.size(22.dp))
                                Column(Modifier.padding(start = 7.dp)) {
                                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                    Text(subtitle, color = Color(0xFFFFD8E9), fontSize = 8.sp)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        if (!isSignedIn) Text("Iniciá sesión para sincronizar la constancia de edad con tu cuenta.", style = MaterialTheme.typography.bodySmall)
    }
    unavailableCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { unavailableCategory = null },
            icon = { Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4D9D)) },
            title = { Text("$category · Próximamente") },
            text = {
                Text(
                    "Gracias por tu interés en esta categoría. Estamos preparando su espacio y todavía no hay publicaciones verificadas disponibles. Volvé pronto; queremos recibirte con una experiencia cuidada, privada y a la altura de tus expectativas."
                )
            },
            confirmButton = {
                TextButton(onClick = { unavailableCategory = null }) { Text("Entendido") }
            },
            containerColor = Color(0xFF210619),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFFFD8E9)
        )
    }
}

@Composable
fun RemoteSourcesScreen(repository: SupabaseUserRepository, isSignedIn: Boolean, countryCode: String?, onImport: (List<Channel>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<UserPlaylistSource>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val downloader = remember { RemotePlaylistDownloader() }
    val scope = rememberCoroutineScope()

    suspend fun reloadSources() {
        if (isSignedIn) runCatching { repository.playlistSources() }
            .onSuccess { sources = it }
            .onFailure { message = it.message }
    }
    LaunchedEffect(isSignedIn) { reloadSources() }

    Column(
        Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Fuentes sincronizadas", style = MaterialTheme.typography.titleLarge)
        if (!isSignedIn) {
            Text("Iniciá sesión para guardar listas en tu cuenta y sincronizarlas entre dispositivos.", style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre de la lista") }, singleLine = true)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL M3U/M3U8 HTTPS") }, singleLine = true)
            Button(onClick = {
                scope.launch {
                    busy = true
                    runCatching { repository.addPlaylistSource(name, url, countryCode) }
                        .onSuccess {
                            name = ""
                            url = ""
                            message = "Fuente registrada. Queda deshabilitada hasta verificar titularidad y autorización."
                            reloadSources()
                        }
                        .onFailure { message = it.message }
                    busy = false
                }
            }, enabled = !busy && name.isNotBlank() && url.startsWith("https://", true)) {
                Icon(Icons.Default.PlaylistAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Guardar fuente")
            }
            Text("Una fuente nueva queda deshabilitada y no se reproduce hasta que sea verificada por un administrador.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            sources.forEach { source ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.titleSmall)
                            Text(source.sourceUrl ?: "Fuente local", style = MaterialTheme.typography.bodySmall)
                            Text(if (source.enabled && source.verified) "Verificada y habilitada" else "Deshabilitada · pendiente de verificación", style = MaterialTheme.typography.labelSmall)
                        }
                        if (source.enabled && source.verified && source.sourceUrl != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { downloader.download(source.sourceUrl) }
                                        .onSuccess { channels -> onImport(channels); message = "Se importaron ${channels.size} canales autorizados." }
                                        .onFailure { message = it.message }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Importar") }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { repository.removePlaylistSource(source.id) }
                                    .onSuccess { sources = sources.filterNot { it.id == source.id }; message = "Fuente eliminada." }
                                    .onFailure { message = it.message }
                            }
                        }) { Icon(Icons.Default.Delete, "Eliminar fuente") }
                    }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}


private fun isSafeAdultArtworkUrl(value:String):Boolean = runCatching {
    val uri=java.net.URI(value)
    uri.scheme.equals("https",true) && !uri.host.isNullOrBlank() && uri.userInfo==null
}.getOrDefault(false)
