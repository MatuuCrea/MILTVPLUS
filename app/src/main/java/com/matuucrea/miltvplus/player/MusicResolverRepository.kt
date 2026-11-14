package com.matuucrea.miltvplus.player

import com.matuucrea.miltvplus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

data class ResolvedMusic(
    val title: String,
    val creator: String,
    val album: String,
    val genre: String,
    val streamUrl: String,
    val artworkUrl: String?
)

/** Resolves each music item through the server's regional rights gate before playback. */
class MusicResolverRepository(
    private val baseUrl: String = BuildConfig.MEDIA_RESOLVER_URL
) {
    suspend fun resolve(id: String, country: String): Result<ResolvedMusic> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))) {
                "Identificador de pista inválido."
            }
            require(country.matches(Regex("^[A-Z]{2}$"))) { "País inválido." }
            val base = baseUrl.trim().trimEnd('/')
            require(base.startsWith("https://")) { "El servicio de música todavía no está configurado." }
            val connection = (URL("$base/api/v2/resolve/musica/$country/$id").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(128 * 1024) }.orEmpty()
                val json = JSONObject(body)
                check(status in 200..299 && json.optBoolean("success") &&
                    json.optString("mode") == "direct_stream" && json.optString("mediaType") == "audio") {
                    json.optString("error").ifBlank { "No hay audio directo disponible para esta pista." }
                }
                val streamUrl = json.optString("streamUrl")
                val uri = URI(streamUrl)
                require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                    "El servidor devolvió una dirección de audio no válida."
                }
                ResolvedMusic(
                    title = json.optString("title"),
                    creator = json.optString("creator"),
                    album = json.optString("album"),
                    genre = json.optString("genre"),
                    streamUrl = streamUrl,
                    artworkUrl = json.optString("artworkUrl").takeIf { it.startsWith("https://") }
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
