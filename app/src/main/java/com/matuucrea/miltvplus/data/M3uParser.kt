package com.matuucrea.miltvplus.data

import java.net.URI
import java.security.MessageDigest

object M3uParser {
    private val attr = Regex("([A-Za-z0-9_-]+)=\\\"([^\\\"]*)\\\"")

    fun parse(text: String): List<Channel> {
        require(text.lineSequence().firstOrNull()?.trim()?.startsWith("#EXTM3U", true) == true) {
            "El archivo no comienza con #EXTM3U"
        }
        val channels = mutableListOf<Channel>()
        var name = ""
        var group = ""
        var logo: String? = null

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    val attributes = attr.findAll(line.substringAfter("#EXTINF"))
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = line.substringAfterLast(',').trim()
                        .ifBlank { attributes["tvg-name"].orEmpty() }
                        .ifBlank { "Canal sin nombre" }
                    group = attributes["group-title"].orEmpty()
                    logo = attributes["tvg-logo"]?.takeIf { it.startsWith("https://") }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    val scheme = runCatching { URI(line).scheme?.lowercase() }.getOrNull()
                    if (scheme in setOf("http", "https") && name.isNotBlank()) {
                        val digest = MessageDigest.getInstance("SHA-256")
                            .digest("$name|$line".toByteArray())
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
}
