package com.matuucrea.miltvplus.data

import com.matuucrea.miltvplus.BuildConfig
import com.matuucrea.miltvplus.auth.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UserPlaylistSource(val id: String, val name: String, val sourceUrl: String?, val enabled: Boolean, val verified: Boolean)

class SupabaseUserRepository(private val sessionProvider: suspend () -> AuthSession?) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun favoriteIds(): Set<String> = withContext(Dispatchers.IO) {
        val session = requireSession()
        val rows = request("GET", "/rest/v1/user_favorites?select=catalog_item_id&user_id=eq.${session.userId}", session)
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optString("catalog_item_id") }.toSet()
    }

    suspend fun setFavorite(catalogItemId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        requireUuid(catalogItemId)
        val session = requireSession()
        if (enabled) {
            request(
                "POST",
                "/rest/v1/user_favorites",
                session,
                JSONObject().put("user_id", session.userId).put("catalog_item_id", catalogItemId),
                "resolution=ignore-duplicates,return=minimal"
            )
        } else {
            request(
                "DELETE",
                "/rest/v1/user_favorites?user_id=eq.${session.userId}&catalog_item_id=eq.$catalogItemId",
                session
            )
        }
    }

    suspend fun fetchPreferences(): JSONObject? = withContext(Dispatchers.IO) {
        val session = requireSession()
        val rows = request("GET", "/rest/v1/user_preferences?select=theme,country_code,language_code,subtitles_enabled,notifications_enabled,autoplay_enabled,accent_color,preferred_audio_language&user_id=eq.${session.userId}&limit=1", session)
        if (rows.length() == 0) null else rows.getJSONObject(0)
    }

    suspend fun savePreferences(
        theme: String,
        countryCode: String?,
        languageCode: String,
        subtitlesEnabled: Boolean,
        notificationsEnabled: Boolean = true,
        autoplayEnabled: Boolean = false,
        accentColor: String = "electric-blue",
        preferredAudioLanguage: String? = null
    ) = withContext(Dispatchers.IO) {
        require(theme in setOf("dark", "light", "system"))
        val session = requireSession()
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("theme", theme)
            .put("country_code", countryCode ?: JSONObject.NULL)
            .put("language_code", languageCode)
            .put("subtitles_enabled", subtitlesEnabled)
            .put("notifications_enabled", notificationsEnabled)
            .put("autoplay_enabled", autoplayEnabled)
            .put("accent_color", accentColor)
            .put("preferred_audio_language", preferredAudioLanguage ?: JSONObject.NULL)
        request("POST", "/rest/v1/user_preferences?on_conflict=user_id", session, body, "resolution=merge-duplicates,return=minimal")
    }

    suspend fun addPlaylistSource(name: String, sourceUrl: String, countryCode: String?) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && name.length <= 100) { "Ingresá un nombre para la fuente." }
        val uri = URI(sourceUrl.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "La lista remota debe utilizar HTTPS y no incluir credenciales en la URL."
        }
        val session = requireSession()
        val body = JSONObject()
            .put("owner_id", session.userId)
            .put("name", name.trim())
            .put("source_type", if (uri.path.endsWith(".m3u8", ignoreCase = true)) "m3u8" else "m3u")
            .put("source_url", uri.toString())
            .put("country_code", countryCode ?: JSONObject.NULL)
            .put("is_enabled", false)
            .put("is_verified", false)
        request("POST", "/rest/v1/media_sources", session, body, "return=representation")
    }

    suspend fun playlistSources(): List<UserPlaylistSource> = withContext(Dispatchers.IO) {
        val session = requireSession()
        val rows = request("GET", "/rest/v1/media_sources?select=id,name,source_url,is_enabled,is_verified&owner_id=eq.${session.userId}&order=created_at.desc", session)
        (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                UserPlaylistSource(row.optString("id"), row.optString("name"),
                    row.optString("source_url").takeIf { it.isNotBlank() && it != "null" },
                    row.optBoolean("is_enabled", false), row.optBoolean("is_verified", false))
            }
        }
    }

    suspend fun removePlaylistSource(sourceId: String) = withContext(Dispatchers.IO) {
        requireUuid(sourceId)
        val session = requireSession()
        request("DELETE", "/rest/v1/media_sources?id=eq.$sourceId&owner_id=eq.${session.userId}", session)
    }

    suspend fun confirmAdultAccess() = withContext(Dispatchers.IO) {
        val session = requireSession()
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("confirmed_18_plus", true)
            .put("confirmed_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date()))
        request("POST", "/rest/v1/age_access_consents?on_conflict=user_id", session, body, "resolution=merge-duplicates,return=minimal")
    }

    private suspend fun requireSession(): AuthSession =
        sessionProvider() ?: throw IllegalStateException("Iniciá sesión para sincronizar tus datos.")

    private fun request(
        method: String,
        path: String,
        session: AuthSession,
        body: JSONObject? = null,
        prefer: String? = null
    ): JSONArray {
        val uri = URI(baseUrl + path)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) doOutput = true
        }
        try {
            body?.let { connection.outputStream.use { output -> output.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) {
                val message = runCatching { JSONObject(response).optString("message").ifBlank { JSONObject(response).optString("hint") } }.getOrNull()
                message?.takeIf(String::isNotBlank) ?: "Supabase rechazó la sincronización (HTTP $status)."
            }
            return if (response.isBlank()) JSONArray() else JSONArray(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun requireUuid(value: String) {
        require(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(value)) {
            "El identificador del contenido no es un UUID válido."
        }
    }
}
