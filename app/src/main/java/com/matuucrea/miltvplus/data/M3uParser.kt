package com.matuucrea.miltvplus.data

import java.net.URI
import java.security.MessageDigest

/**
 * Parses standard extended M3U playlists.
 * Only HTTPS stream and artwork URLs are retained because playback is HTTPS-only.
 */
object M3uParser {
    private val attr = Regex("([A-Za-z0-9_-]+)=\\\"([^\\\"]*)\\\"")

    fun parse(text: String): List<Channel> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_PLAYLIST_BYTES) {
            "La lista M3U supera el límite de 5 MB."
        }
        val normalized = text.removePrefix("\uFEFF")
        require(normalized.lineSequence().firstOrNull()?.trim()?.startsWith("#EXTM3U", true) == true) {
            "El archivo no comienza con #EXTM3U"
        }
        val channels = mutableListOf<Channel>()
        var name = ""
        var group = ""
        var logo: String? = null

        normalized.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    val attributes = attr.findAll(line.substringAfter("#EXTINF"))
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = extractDisplayName(line, attributes)
                        .ifBlank { "Canal sin nombre" }
                    group = attributes["group-title"].orEmpty()
                    logo = attributes["tvg-logo"]?.takeIf { isHttpsUrl(it) }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    if (isHttpsUrl(line) && name.isNotBlank()) {
                        val digest = MessageDigest.getInstance("SHA-256")
                            .digest("$name|$line".toByteArray(Charsets.UTF_8))
                        val id = digest.take(12).joinToString("") { "%02x".format(it) }
                        channels += Channel(id, name, line, group, logo)
                    }
                    name = ""
                    group = ""
                    logo = null
                }
            }
        }
        return channels.distinctBy { it.id }
    }

    /**
     * The EXTINF title begins after the first comma outside a quoted attribute.
     * Titles themselves may contain commas, so substringAfterLast(',') is incorrect.
     */
    private fun extractDisplayName(
        line: String,
        attributes: Map<String, String>
    ): String {
        val payload = line.substringAfter("#EXTINF", "").trim()
        var inQuotes = false
        var separator = -1
        for (index in payload.indices) {
            val char = payload[index]
            if (char == '"' && (index == 0 || payload[index - 1] != '\\')) {
                inQuotes = !inQuotes
            } else if (char == ',' && !inQuotes) {
                separator = index
                break
            }
        }
        val displayName = if (separator >= 0) payload.substring(separator + 1).trim() else ""
        return displayName.ifBlank { attributes["tvg-name"].orEmpty().trim() }
    }

    private const val MAX_PLAYLIST_BYTES = 5 * 1024 * 1024

    private fun isHttpsUrl(value: String): Boolean =
        runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)
}
