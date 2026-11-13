package com.matuucrea.miltvplus.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    isSignedIn: Boolean
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
            .apply()
        onThemeChanged(theme, accent)
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
        Text("Personalización", style = MaterialTheme.typography.headlineSmall)
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Sistema").forEach { (value, label) ->
                FilterChip(selected = theme == value, onClick = { theme = value; persist() }, label = { Text(label) })
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
        Text("Privacidad y seguridad", style = MaterialTheme.typography.titleMedium)
        Text("Las sesiones Android se cifran con Android Keystore; los datos de cuenta se aíslan con RLS. No se integra publicidad ni analítica de terceros en esta versión. Podés cerrar sesión desde Cuenta y administrar el acceso +18 desde su sección.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        AppUpdateSection()
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
fun CountryPickerScreen(onCountrySelected: (CountryOption) -> Unit) {
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
                    Text(country.name, Modifier.weight(1f).padding(start = 12.dp))
                    if (selected == country.code) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
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
    val store = remember { ParentalAccessStore(context) }
    val catalog = remember { SupabaseCatalogRepository() }
    var ageConfirmed by remember { mutableStateOf(store.ageConfirmed) }
    var pinExists by remember { mutableStateOf(store.hasPin) }
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var adultItems by remember { mutableStateOf<List<RemoteCatalogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("Recomendados") }

    LaunchedEffect(ageConfirmed, isSignedIn) {
        if (ageConfirmed && isSignedIn) {
            runCatching { userRepository.confirmAdultAccess() }
                .onFailure { message = "La confirmación local está guardada; no se pudo sincronizar: ${it.message}" }
        }
    }
    LaunchedEffect(unlocked, countryCode) {
        if (unlocked) {
            loading = true
            catalog.fetchPublished("catalog_items", countryCode)
                .onSuccess { rows ->
                    adultItems = rows.filter {
                        val rating = it.ageRating?.trim()?.uppercase().orEmpty()
                        rating.contains("18") || rating in setOf("NC-17","R18","TV-MA","MA","AO","ADULT","ADULTS ONLY")
                    }
                }
                .onFailure { message = "No se pudo cargar el catálogo +18: ${it.message}" }
            loading = false
        } else adultItems = emptyList()
    }

    val categories = listOf("Recomendados", "Romance", "Parejas", "Pasión", "Hot", "Premium", "Solo adultos")
    val visibleItems = adultItems.filter { item ->
        val metadata = (item.title + " " + item.subtitle + " " + item.category + " " + (item.kind ?: "")).lowercase()
        when (selectedCategory) {
            "Recomendados" -> true
            "Romance" -> listOf("romance", "románt", "romant", "amor").any { it in metadata }
            "Parejas" -> listOf("pareja", "couple", "relación").any { it in metadata }
            "Pasión", "Hot" -> listOf("pasión", "passion", "sensual", "adult", "erót").any { it in metadata }
            "Premium" -> listOf("premium", "exclusive", "exclusivo").any { it in metadata }
            "Solo adultos" -> true
            else -> false
        }
    }

    Column(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color(0xFF210414), androidx.compose.ui.graphics.Color(0xFF09030B), androidx.compose.ui.graphics.Color(0xFF030207))
            )
        ).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF250719))
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 190.dp).padding(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(82.dp)
                                .then(Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("18+", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineLarge)
                        }
                        Text("SOLO ADULTOS", color = androidx.compose.ui.graphics.Color(0xFFFFB5D4), style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Contenido Adulto", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text("Romance, historias y entretenimiento exclusivo para mayores de edad.", color = androidx.compose.ui.graphics.Color(0xFFFFDDEB), style = MaterialTheme.typography.bodyMedium)
                        Text("MILTVPLUS · EXPERIENCIA 18+", color = androidx.compose.ui.graphics.Color(0xFFFF6FAF), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!ageConfirmed) {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF160B18))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Lock, null, tint = androidx.compose.ui.graphics.Color(0xFFFF5C9C), modifier = Modifier.size(34.dp))
                    Text("Acceso exclusivo para mayores de 18 años", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Text("Confirmá tu mayoría de edad para continuar. Esta declaración no reemplaza los controles legales de edad exigibles en tu país.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { store.confirmAge(); ageConfirmed = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE91E63))) {
                        Text("Confirmo que tengo 18 años o más")
                    }
                }
            }
        } else if (!pinExists) {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF160B18))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Creá tu PIN privado", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nuevo PIN de 6 dígitos") }, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = {
                        runCatching { store.setPin(pin) }
                            .onSuccess { pinExists = true; pin = ""; message = "PIN guardado. Ingresalo para desbloquear." }
                            .onFailure { message = it.message }
                    }, enabled = pin.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Guardar PIN") }
                }
            }
        } else if (!unlocked) {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF160B18))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Lock, null, tint = androidx.compose.ui.graphics.Color(0xFFFF5C9C), modifier = Modifier.size(32.dp))
                    Text("Sección protegida", style = MaterialTheme.typography.titleLarge)
                    Text("Ingresá tu PIN parental para ver el catálogo 18+.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, modifier = Modifier.fillMaxWidth(), label = { Text("PIN parental") }, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = {
                        unlocked = store.verifyPin(pin)
                        pin = ""
                        if (!unlocked) message = "PIN incorrecto o acceso temporalmente bloqueado."
                    }, enabled = pin.length == 6, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE91E63))) { Text("Ingresar ahora") }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = androidx.compose.ui.graphics.Color(0xFFFF78B4))
                Text(" Acceso desbloqueado · ${adultItems.size} títulos publicados", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { unlocked = false }) { Text("Bloquear") }
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF9C145D),
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }
            Text("Recomendados para vos", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
            when {
                loading -> CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFFFF5C9C))
                visibleItems.isEmpty() -> Text(
                    if (adultItems.isEmpty()) "Todavía no hay títulos 18+ publicados y verificados para esta región. No mostramos contenido inventado."
                    else "No hay títulos publicados en esta categoría. Probá otra categoría.",
                    color = androidx.compose.ui.graphics.Color(0xFFD8C7D2)
                )
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleItems, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF180D1A)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                                    Text(item.subtitle, color = androidx.compose.ui.graphics.Color(0xFFD4C4D0), style = MaterialTheme.typography.bodySmall)
                                    Text(item.ageRating ?: "18+", color = androidx.compose.ui.graphics.Color(0xFFFF79B6), style = MaterialTheme.typography.labelSmall)
                                }
                                if (item.actionUrl != null) {
                                    Button(onClick = { onPlay(item) }, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE91E63))) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Text(" Ver")
                                    }
                                } else {
                                    Text("No disponible", color = androidx.compose.ui.graphics.Color(0xFFB8A8B4), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectedCategory = "Recomendados" }) { Text("Ver todo") }
                OutlinedButton(onClick = { unlocked = false }) { Text("Bloquear ahora") }
            }
        }
        message?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF9CC7), style = MaterialTheme.typography.bodySmall) }
        if (!isSignedIn) Text("Iniciá sesión para sincronizar la constancia de edad con tu cuenta.", color = androidx.compose.ui.graphics.Color(0xFFD4C4D0), style = MaterialTheme.typography.bodySmall)
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
