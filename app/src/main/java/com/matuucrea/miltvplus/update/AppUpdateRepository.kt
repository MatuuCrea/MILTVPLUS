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
                    val match = Regex("^([a-fA-F0-9]{64})\\\\s+\\\\*?app-release\\\\.apk$").find(line.trim())
                    match?.groupValues?.get(1)?.lowercase()
                }.firstOrNull() ?: error("SHA256SUMS no contiene el hash de app-release.apk.")
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val temporary = File(directory, "app-release.apk.part")
                val finalFile = File(directory, "miltvplus-${update.versionName}.apk")
                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MILTVPLUS-Android")
                    setRequestProperty("Accept", "application/vnd.android.package-archive")
                }
                try {
                    check(connection.url.protocol.equals("https", true)) { "La descarga intentó salir de HTTPS." }
                    val status = connection.responseCode
                    check(status in 200..299) { "No se pudo descargar la actualización (HTTP $status)." }
                    val declaredLength = connection.contentLengthLong
                    require(declaredLength <= MAX_APK_BYTES || declaredLength < 0) { "El APK supera el tamaño máximo permitido." }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_APK_BYTES) { "El APK supera el tamaño máximo permitido." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    check(actual == expected) { "La verificación SHA-256 falló. No se instalará este archivo." }
                    if (finalFile.exists()) finalFile.delete()
                    check(temporary.renameTo(finalFile)) { "No se pudo guardar el APK verificado." }
                    finalFile
                } finally {
                    connection.disconnect()
                    if (temporary.exists()) temporary.delete()
                }
            }
        }

