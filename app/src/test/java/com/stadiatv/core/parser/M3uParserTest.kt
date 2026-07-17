package com.stadiatv.core.parser

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun parsesExtendedM3uWithQuotedAttributes() = runTest {
        val text = """
            #EXTM3U url-tvg="https://example.com/epg.xml"
            #EXTINF:-1 tvg-id="bbc.one" tvg-name="BBC One" tvg-logo="https://img/bbc.png" group-title="UK",BBC One
            https://stream.example.com/live/bbc.m3u8
        """.trimIndent()

        val result = parser.parse(StringReader(text), "https://provider.example/list.m3u")

        assertEquals(1, result.entries.size)
        assertEquals("BBC One", result.entries.single().name)
        assertEquals("bbc.one", result.entries.single().attributes["tvg-id"])
        assertEquals("UK", result.entries.single().attributes["group-title"])
    }

    @Test
    fun handlesBomCrlfAndRelativeUrls() = runTest {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=Radio,Talk\r\nradio/talk.m3u8\r\n"

        val result = parser.parse(StringReader(text), "https://provider.example/path/list.m3u")

        assertEquals("https://provider.example/path/radio/talk.m3u8", result.entries.single().streamUrl)
        assertEquals("Radio", result.entries.single().attributes["group-title"])
    }

    @Test
    fun recoversFromMalformedEntriesWithWarnings() = runTest {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id=no-url,No URL
            #EXTINF:-1 group-title=Bad,Bad URL
            file:///tmp/video.ts
            #EXTINF:-1 group-title=Good,Good
            https://example.com/good.ts
        """.trimIndent()

        val result = parser.parse(StringReader(text), "https://provider.example/list.m3u")

        assertEquals(1, result.entries.size)
        assertEquals("Good", result.entries.single().name)
        assertTrue(result.warnings.any { it.code == "MISSING_STREAM_URL" || it.code == "UNSUPPORTED_URL" })
    }
}
