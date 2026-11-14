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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val endsAt: String? = null,
    val kind: String? = null,
    val countryCode: String? = null,
    val ageRating: String? = null,
    val infoUrl: String? = null
)

class SupabaseCatalogRepository {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun fetchPublished(table: String, countryCode: String? = null): Result<List<RemoteCatalogEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            require(table in PUBLISHED_TABLES) { "Tabla de catálogo no permitida." }
            require(isSafeHttpsUrl(baseUrl)) { "La URL de Supabase debe usar HTTPS y un host válido." }
            require(apiKey.startsWith("sb_publishable_")) { "Falta configurar la clave publishable de Supabase." }

            // EPG must arrive in schedule order; the other catalog sections show newest publications first.
            val order = when (table) { "tv_channels" -> "sort_order.asc,title.asc"; "epg_programs", "sports_events", "theater_events" -> "starts_at.asc"; else -> "created_at.desc" }
            require(countryCode == null || countryCode.matches(Regex("^[A-Z]{2}$"))) { "Código de país inválido." }
            val countryFilter = countryCode?.let { code ->
                val predicate = "(country_code.eq.$code,country_code.is.null,rights_regions.cs.{$code})"
                "&or=" + URLEncoder.encode(predicate, "UTF-8")
            }.orEmpty()
            val allRows = ArrayList<RemoteCatalogEntry>()
            var offset = 0
            while (offset < MAX_CATALOG_ROWS) {
                val endpoint = URL("$baseUrl/rest/v1/$table?select=*&is_published=eq.true&order=$order&limit=$PAGE_SIZE&offset=$offset$countryFilter")
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
                val pageSize: Int
                try {
                    val status = connection.responseCode
                    check(isSafeHttpsUrl(connection.url.toString())) { "La conexión intentó salir de HTTPS." }
                    val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.use { stream -> readLimited(stream, MAX_RESPONSE_BYTES) }.orEmpty()
                    check(status in 200..299) { "Supabase respondió HTTP $status: ${body.take(240)}" }
                    val rows = JSONArray(body)
                    pageSize = rows.length()
                    for (index in 0 until pageSize) allRows.add(mapEntry(rows.getJSONObject(index), table))
                } finally {
                    connection.disconnect()
                }
                if (pageSize < PAGE_SIZE) break
                offset += PAGE_SIZE
            }
            allRows
        }
    }

    private fun mapEntry(row: JSONObject, table: String): RemoteCatalogEntry {
        val title = row.optString("title").ifBlank { row.optString("name").ifBlank { "Sin título" } }
        val subtitleParts = listOf("kind", "sport", "competition", "creator", "genre", "venue", "description", "release_year", "age_rating", "album", "category", "language_code", "country_code", "duration_seconds", "rating", "provider_id", "season_number", "episode_number", "audio_languages", "subtitle_languages", "license_region", "available_from", "available_until")
            .mapNotNull { key -> row.optString(key).takeIf { it.isNotBlank() && it != "null" } }
        val startsAt = row.optString("starts_at").takeIf { it.isNotBlank() && it != "null" }
        val endsAt = row.optString("ends_at").takeIf { it.isNotBlank() && it != "null" }
        val schedule = listOfNotNull(startsAt, endsAt).joinToString(" – ")
        val accessModelLabel = when (row.optString("access_model")) {
            "free_web" -> "Acceso web gratuito"
            "free_to_air" -> "Televisión abierta"
            "free_with_ads" -> "Gratis con anuncios"
            "subscription" -> "Suscripción"
            "geo_restricted" -> "Con restricción geográfica"
            else -> null
        }
        val liveMarkerConfirmed = table == "tv_channels" &&
            row.optString("last_check_detail").startsWith("YouTube confirma", ignoreCase = true)
        val sourceStatusLabel = when {
            liveMarkerConfirmed -> "Marcador EN VIVO detectado en página oficial"
            row.optString("source_status") == "online" -> "Fuente accesible"
            row.optString("source_status") == "offline" -> "Fuente sin respuesta"
            row.optString("source_status") == "blocked" -> "Acceso bloqueado"
            row.optString("source_status") == "pending_review" -> "En revisión"
            row.optString("source_status") == "geo_restricted" -> "Restricción geográfica"
            else -> null
        }
        val healthDetail = row.optString("last_check_detail")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.take(150)
        val subtitle = (subtitleParts + listOfNotNull(schedule, accessModelLabel, sourceStatusLabel, healthDetail)).distinct().joinToString(" · ")
        val artwork = listOf("logo_url", "poster_url", "backdrop_url", "artwork_url", "image_url")
            .firstNotNullOfOrNull { key -> row.optString(key).takeIf(::isSafeHttpsUrl) }
        val youtubeLive = table == "tv_channels" &&
            row.optString("playback_type").equals("youtube_live", ignoreCase = true)
        // YouTube Live pages must use the broadcaster's official web player, not ExoPlayer/Media3.
        // Keep their URL in infoUrl so the app opens it in the immersive official-player view.
        val action = if (youtubeLive || !hasActivePlaybackRights(row, table)) null else listOf("stream_url", "authorized_stream_url")
            .firstNotNullOfOrNull { key -> row.optString(key).takeIf(::isSafeHttpsUrl) }
        val infoUrlKeys = if (youtubeLive) {
            listOf("stream_url", "official_watch_url", "official_info_url", "info_url", "website_url", "external_url")
        } else {
            listOf("official_watch_url", "official_info_url", "info_url", "website_url", "external_url")
        }
        return RemoteCatalogEntry(
            id = row.optString("id").ifBlank { "${table}_${row.hashCode()}" },
            title = title,
            subtitle = subtitle,
            artworkUrl = artwork,
            actionUrl = action,
            category = if (table == "tv_channels") row.optString("category").ifBlank { "other" } else row.optString("genre").ifBlank { table },
            channelExternalId = row.optString("channel_external_id").takeIf { it.isNotBlank() && it != "null" },
            startsAt = startsAt,
            endsAt = endsAt,
            kind = row.optString("kind").takeIf { it.isNotBlank() && it != "null" },
            countryCode = row.optString("country_code").takeIf { it.isNotBlank() && it != "null" },
            ageRating = row.optString("age_rating").takeIf { it.isNotBlank() && it != "null" },
            infoUrl = infoUrlKeys.firstNotNullOfOrNull { key -> row.optString(key).takeIf(::isSafeHttpsUrl) }
        )
    }

    private fun hasActivePlaybackRights(row: JSONObject, table: String): Boolean {
        if (!row.optBoolean("rights_verified", false)) return false
        if (row.optString("rights_holder").isBlank() || row.optString("rights_reference").isBlank()) return false
        if (table == "tv_channels" && !row.optString("source_status").equals("online", ignoreCase = true)) return false
        val region = Locale.getDefault().country.uppercase(Locale.ROOT)
        if (region.isBlank()) return false
        val regions = row.optJSONArray("rights_regions") ?: return false
        var regionAllowed = false
        for (index in 0 until regions.length()) {
            val allowed = regions.optString(index).uppercase(Locale.ROOT)
            if (allowed == region || allowed == "001" || allowed == "*") regionAllowed = true
        }
        if (!regionAllowed) return false
        val from = parseRightsDate(row.optString("rights_valid_from")) ?: return false
        val untilRaw = row.optString("rights_valid_until").takeIf { it.isNotBlank() && it != "null" }
        val until = if (untilRaw == null) null else parseRightsDate(untilRaw) ?: return false
        val now = System.currentTimeMillis()
        return from.time <= now && (until == null || until.time > now)
    }

    private fun parseRightsDate(value: String): Date? {
        if (value.isBlank() || value == "null") return null
        val normalizedOffset = Regex("([+-][0-9]{2}):([0-9]{2})").replace(value) { match -> match.groupValues[1] + match.groupValues[2] }
        val normalized = Regex("\\.([0-9]{3})[0-9]+").replace(normalizedOffset) { match -> "." + match.groupValues[1] }
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { parser.parse(normalized) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
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
        const val PAGE_SIZE = 500
        const val MAX_CATALOG_ROWS = 10_000
        val PUBLISHED_TABLES = setOf(
            "catalog_items", "tv_channels", "radio_stations", "sports_events",
            "music_items", "theater_events", "epg_programs"
        )
    }
}
