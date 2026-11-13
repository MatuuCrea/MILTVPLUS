package com.matuucrea.miltvplus.data

import com.matuucrea.miltvplus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Read-only Supabase REST client. RLS remains the authorization boundary:
 * only rows explicitly published by an administrator are returned to anonymous users.
 */
data class RemoteCatalogEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val actionUrl: String?,
    val category: String,
    val channelExternalId: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null
)

class SupabaseCatalogRepository {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun fetchPublished(table: String): Result<List<RemoteCatalogEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            require(table in PUBLISHED_TABLES) { "Tabla de catálogo no permitida." }
            require(isSafeHttpsUrl(baseUrl)) { "La URL de Supabase debe usar HTTPS y un host válido." }
            require(apiKey.startsWith("sb_publishable_")) { "Falta configurar la clave publishable de Supabase." }

            // EPG must arrive in schedule order; the other catalog sections show newest publications first.
            val order = if (table == "epg_programs") "starts_at.asc" else "created_at.desc"
            val endpoint = URL("$baseUrl/rest/v1/$table?select=*&is_published=eq.true&order=$order&limit=100")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val status = connection.responseCode
                check(isSafeHttpsUrl(connection.url.toString())) { "La conexión intentó salir de HTTPS." }
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { stream -> readLimited(stream, MAX_RESPONSE_BYTES) }.orEmpty()
                check(status in 200..299) { "Supabase respondió HTTP $status: ${body.take(240)}" }
                val rows = JSONArray(body)
                (0 until rows.length()).map { index -> mapEntry(rows.getJSONObject(index), table) }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun mapEntry(row: JSONObject, table: String): RemoteCatalogEntry {
        val title = row.optString("title").ifBlank { row.optString("name").ifBlank { "Sin título" } }
        val subtitleParts = listOf("kind", "sport", "competition", "creator", "genre", "venue", "description")
            .mapNotNull { key -> row.optString(key).takeIf { it.isNotBlank() && it != "null" } }
        val startsAt = row.optString("starts_at").takeIf { it.isNotBlank() && it != "null" }
        val endsAt = row.optString("ends_at").takeIf { it.isNotBlank() && it != "null" }
        val schedule = listOfNotNull(startsAt, endsAt).joinToString(" – ")
        val subtitle = (subtitleParts + listOfNotNull(schedule)).distinct().joinToString(" · ")
        val artwork = listOf("poster_url", "logo_url", "artwork_url", "image_url")
            .firstNotNullOfOrNull { key -> row.optString(key).takeIf(::isSafeHttpsUrl) }
        val action = listOf("stream_url", "authorized_stream_url")
            .firstNotNullOfOrNull { key -> row.optString(key).takeIf(::isSafeHttpsUrl) }
        return RemoteCatalogEntry(
            id = row.optString("id").ifBlank { "${table}_${row.hashCode()}" },
            title = title,
            subtitle = subtitle,
            artworkUrl = artwork,
            actionUrl = action,
            category = table,
            channelExternalId = row.optString("channel_external_id").takeIf { it.isNotBlank() && it != "null" },
            startsAt = startsAt,
            endsAt = endsAt
        )
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= maxBytes) { "La respuesta del catálogo supera el límite permitido." }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun isSafeHttpsUrl(value: String): Boolean =
        runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        val PUBLISHED_TABLES = setOf(
            "catalog_items", "radio_stations", "sports_events",
            "music_items", "theater_events", "epg_programs"
        )
    }
}
