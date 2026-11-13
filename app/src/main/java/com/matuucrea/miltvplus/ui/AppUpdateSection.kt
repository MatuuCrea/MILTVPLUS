package com.matuucrea.miltvplus.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.matuucrea.miltvplus.BuildConfig
import com.matuucrea.miltvplus.update.AppReleaseUpdate
import com.matuucrea.miltvplus.update.AppUpdateRepository
import kotlinx.coroutines.launch

@Composable
fun AppUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppReleaseUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Buscando publicaciones oficiales…") }
    var checked by remember { mutableStateOf(false) }

    suspend fun check() {
        checking = true
        message = "Consultando la última versión oficial…"
        AppUpdateRepository.checkForUpdate()
            .onSuccess {
                update = it
                message = if (it == null) {
                    "No hay una versión más reciente publicada. Versión instalada: ${BuildConfig.VERSION_NAME}."
                } else {
                    "Nueva versión ${it.versionName} disponible. Se verificará su SHA-256 antes de abrir el instalador."
                }
            }
            .onFailure { message = "No se pudo comprobar la actualización: ${it.message}" }
        checked = true
        checking = false
    }

    LaunchedEffect(Unit) { check() }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Actualizaciones oficiales", style = MaterialTheme.typography.titleMedium)
            Text("Versión instalada: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (checking || installing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { check() } },
                    enabled = !checking && !installing
                ) { Text(if (checked) "Buscar de nuevo" else "Comprobar") }
                if (update != null) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                message = "Concedé el permiso de instalación para MILTVPLUS y después volvé a tocar Descargar e instalar."
                            } else {
                                scope.launch {
                                    installing = true
                                    message = "Descargando APK y verificando SHA-256…"
                                    AppUpdateRepository.downloadVerifiedApk(update!!, context)
                                        .onSuccess { apk ->
                                            runCatching { AppUpdateRepository.openInstaller(context, apk) }
                                                .onSuccess { message = "El instalador de Android se abrió. Confirmá la actualización para finalizar." }
                                                .onFailure { message = it.message ?: "No se pudo abrir el instalador." }
                                        }
                                        .onFailure { message = "Actualización cancelada: ${it.message}" }
                                    installing = false
                                }
                            }
                        },
                        enabled = !checking && !installing
                    ) { Text("Descargar e instalar") }
                }
            }
            Text(
                "Las actualizaciones se detectan al abrir esta sección. Android siempre solicita confirmación del sistema antes de instalar un APK externo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
