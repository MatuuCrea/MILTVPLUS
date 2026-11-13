package com.matuucrea.miltvplus.data
import org.junit.Assert.*
import org.junit.Test
class M3uParserTest {
    @Test fun parsesNameAndGroup(){val p="#EXTM3U\n#EXTINF:-1 group-title=\"Noticias\",Canal Uno\nhttps://example.org/live.m3u8";val c=M3uParser.parse(p);assertEquals(1,c.size);assertEquals("Canal Uno",c[0].name);assertEquals("Noticias",c[0].group)}
    @Test fun rejectsInvalidHeader(){assertThrows(IllegalArgumentException::class.java){M3uParser.parse("no es m3u")}}
    @Test fun ignoresFileScheme(){val p="#EXTM3U\n#EXTINF:-1,Canal\nfile:///etc/passwd";assertTrue(M3uParser.parse(p).isEmpty())}
}
