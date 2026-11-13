package com.matuucrea.miltvplus.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.matuucrea.miltvplus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

data class AppReleaseUpdate(
    val tagName: String,
    val versionName: String,
    val apkUrl: String,
    val checksumsUrl: String,
    val releaseUrl: String
)

object AppUpdateRepository {
    private const val RELEASE_API = "https://api.github.com/repos/MatuuCrea/MILTVPLUS/releases/latest"
    private const val MAX_APK_BYTES = 200 * 1024 * 1024
    private const val MAX_TEXT_BYTES = 128 * 1024

    suspend fun checkForUpdate(): Result<AppReleaseUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "MILTVPLUS-Android")
            }
            val jsonText = try {
                val status = connection.responseCode
                if (status == 404) return@withContext Result.success(null)
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { readLimited(it, MAX_TEXT_BYTES) }.orEmpty()
                check(status in 200..299) { "GitHub respondió HTTP $status: ${body.take(180)}" }
                body
            } finally {
                connection.disconnect()
            }
            val release = JSONObject(jsonText)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return@runCatching null
            val tag = release.optString("tag_name")
            val version = tag.removePrefix("v").removePrefix("V")
            require(version.matches(Regex("^\\d+(\\.\\d+){1,3}$"))) { "La versión publicada no tiene formato válido." }
            if (compareReleaseVersions(version, BuildConfig.VERSION_NAME) <= 0) return@runCatching null

            val assets = release.optJSONArray("assets") ?: error("La publicación no contiene archivos.")
            var apk: String? = null
            var checksums: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                when (asset.optString("name")) {
                    "app-release.apk" -> apk = asset.optString("browser_download_url")
                    "SHA256SUMS" -> checksums = asset.optString("browser_download_url")
                }
            }
            val apkUrl = apk ?: error("La publicación no contiene app-release.apk.")
            val checksumUrl = checksums ?: error("La publicación no contiene SHA256SUMS.")
            require(isHttps(apkUrl) && isHttps(checksumUrl)) { "Los enlaces de actualización deben usar HTTPS." }
            val releaseUrl = release.optString("html_url")
            require(isHttps(releaseUrl)) { "El enlace de publicación no es seguro." }
            AppReleaseUpdate(tag, version, apkUrl, checksumUrl, releaseUrl)
        }
    }

    suspend fun downloadVerifiedApk(update: AppReleaseUpdate, context: Context): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val checksums = downloadText(update.checksumsUrl)
                val expected = checksums.lineSequence().mapNotNull { line ->
                    val match = Regex("^([a-fA-F0-9]{64})\\s+\\*?app-release\\.apk$").find(line.trim())
                    match?.groupValues?.get(1)?.lowercase()
                }.firstOrNull() ?: error("SHA256SUMS no contiene el hash de app-release.apk.")
                val bytes = downloadBytes(update.apkUrl, MAX_APK_BYTES)
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                check(actual == expected) { "La verificación SHA-256 falló. No se instalará este archivo." }
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(directory, "miltvplus-${update.versionName}.apk")
                apk.writeBytes(bytes)
                apk
            }
        }

    fun openInstaller(context: Context, apk: File) {
        require(apk.isFile && apk.length() > 0) { "El APK descargado no existe o está vacío." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            throw IllegalStateException("Habilitá «Permitir instalar apps» para MILTVPLUS y volvé a tocar Instalar actualización.")
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    private fun downloadText(address: String): String =
        downloadBytes(address, MAX_TEXT_BYTES).toString(Charsets.UTF_8)

    private fun downloadBytes(address: String, maxBytes: Int): ByteArray {
        require(isHttps(address)) { "Sólo se permiten descargas HTTPS." }
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MILTVPLUS-Android")
            setRequestProperty("Accept", "*/*")
        }
        try {
            check(connection.url.protocol.equals("https", true)) { "La descarga intentó salir de HTTPS." }
            val status = connection.responseCode
            check(status in 200..299) { "No se pudo descargar la actualización (HTTP $status)." }
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= maxBytes || declaredLength < 0) { "El archivo supera el tamaño máximo permitido." }
            return connection.inputStream.use { readLimitedBytes(it, maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): String =
        readLimitedBytes(input, maxBytes).toString(Charsets.UTF_8)

    private fun readLimitedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "La respuesta supera el tamaño máximo permitido." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun isHttps(value: String): Boolean =
        runCatching { URL(value).protocol.equals("https", true) && URL(value).host.isNotBlank() }
            .getOrDefault(false)
}

internal fun compareReleaseVersions(left: String, right: String): Int {
    fun parts(value: String) = value.removePrefix("v").removePrefix("V")
        .split(".").map { it.toLongOrNull() ?: 0L }
    val a = parts(left)
    val b = parts(right)
    for (index in 0 until maxOf(a.size, b.size)) {
        val comparison = (a.getOrElse(index) { 0L }).compareTo(b.getOrElse(index) { 0L })
        if (comparison != 0) return comparison
    }
    return 0
}
