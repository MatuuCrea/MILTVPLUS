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

data class TmdbTitle(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val rating: Double
)

class TmdbCatalogRepository {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun fetch(
        action: String,
        userAccessToken: String,
        query: String = "",
        id: Int? = null,
        season: Int? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        require(action in ALLOWED_ACTIONS) { "Acción TMDB no permitida." }
        require(userAccessToken.isNotBlank()) { "Iniciá sesión para consultar TMDB." }
        val endpoint = URI("$baseUrl/functions/v1/tmdb-catalog")
        require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null) {
            "La conexión al catálogo debe usar HTTPS."
        }
        val body = JSONObject().put("action", action).put("page", 1)
        if (query.isNotBlank()) body.put("query", query)
        id?.let { body.put("id", it) }
        season?.let { body.put("season", it) }
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $userAccessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { readLimited(it, MAX_RESPONSE_BYTES) }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "TMDB respondió HTTP $status.")
            }
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun readTitles(response: JSONObject): List<TmdbTitle> {
        val rows = response.optJSONArray("results") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optInt("id")
            if (id <= 0) return@mapNotNull null
            val title = row.optString("title").ifBlank { row.optString("name") }.ifBlank { "Sin título" }
            val poster = row.optString("poster_path").takeIf { it.startsWith("/") }
                ?.let { "https://image.tmdb.org/t/p/w342$it" }
            val backdrop = row.optString("backdrop_path").takeIf { it.startsWith("/") }
                ?.let { "https://image.tmdb.org/t/p/w780$it" }
            val date = row.optString("release_date").ifBlank { row.optString("first_air_date") }
            TmdbTitle(
                id = id,
                title = title,
                overview = row.optString("overview"),
                posterUrl = poster,
                backdropUrl = backdrop,
                year = date.take(4).takeIf { it.matches(Regex("^\\d{4}$")) },
                rating = row.optDouble("vote_average", 0.0)
            )
        }
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= maxBytes) { "La respuesta TMDB supera el límite permitido." }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_500_000
        val ALLOWED_ACTIONS = setOf(
            "movie_popular", "movie_top_rated", "movie_upcoming", "tv_popular", "tv_on_the_air",
            "search_movie", "search_tv", "movie_details", "tv_details", "movie_credits", "tv_credits", "tv_season"
        )
    }
}
