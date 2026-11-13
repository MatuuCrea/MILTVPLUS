package com.matuucrea.miltvplus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class RemotePlaylistDownloader {
    suspend fun download(sourceUrl: String): List<Channel> = withContext(Dispatchers.IO) {
        val uri = URI(sourceUrl)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "La fuente remota debe usar HTTPS y no incluir credenciales en la URL."
        }
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.apple.mpegurl, audio/x-mpegurl, text/plain, */*")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val status = connection.responseCode
            check(status in 200..299) { "La fuente M3U respondió HTTP $status." }
            val finalUri = connection.url.toURI()
            require(finalUri.scheme.equals("https", true) && finalUri.userInfo == null) {
                "La descarga intentó salir de HTTPS."
            }
            val text = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_PLAYLIST_BYTES) { "La lista remota supera el límite de 5 MB." }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            M3uParser.parse(text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_PLAYLIST_BYTES = 5 * 1024 * 1024
    }
}
