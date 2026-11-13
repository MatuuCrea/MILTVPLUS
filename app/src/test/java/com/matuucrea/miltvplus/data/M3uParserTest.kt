package com.matuucrea.miltvplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test fun parsesNameAndGroup() {
        val playlist = "#EXTM3U\n#EXTINF:-1 group-title=\"Noticias\",Canal Uno\nhttps://example.org/live.m3u8"
        val channels = M3uParser.parse(playlist)
        assertEquals(1, channels.size)
        assertEquals("Canal Uno", channels[0].name)
        assertEquals("Noticias", channels[0].group)
    }

    @Test fun rejectsInvalidHeader() {
        assertThrows(IllegalArgumentException::class.java) { M3uParser.parse("no es m3u") }
    }

    @Test fun rejectsInsecureAndLocalStreamSchemes() {
        val playlist = "#EXTM3U\n#EXTINF:-1,HTTP\nhttp://example.org/live.m3u8\n#EXTINF:-1,Local\nfile:///etc/passwd"
        assertTrue(M3uParser.parse(playlist).isEmpty())
    }

    @Test fun ignoresCredentialsEmbeddedInStreamUrl() {
        val playlist = "#EXTM3U\n#EXTINF:-1,Privado\nhttps://user:password@example.org/live.m3u8"
        assertTrue(M3uParser.parse(playlist).isEmpty())
    }

    @Test fun keepsOnlyHttpsArtwork() {
        val playlist = "#EXTM3U\n#EXTINF:-1 tvg-logo=\"http://example.org/logo.png\",Canal\nhttps://example.org/live.m3u8"
        val channels = M3uParser.parse(playlist)
        assertEquals(1, channels.size)
        assertEquals(null, channels[0].logo)
    }

    @Test fun removesDuplicateEntries() {
        val playlist = "#EXTM3U\n#EXTINF:-1,Canal\nhttps://example.org/live.m3u8\n#EXTINF:-1,Canal\nhttps://example.org/live.m3u8"
        assertEquals(1, M3uParser.parse(playlist).size)
    }
}
