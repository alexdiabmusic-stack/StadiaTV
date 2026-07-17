package com.stadiatv.core.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import java.time.Instant

class XmltvParserTest {
    @Test
    fun parsesProgrammeWithOffsetTime() {
        val xml = """
            <tv>
              <programme channel="bbc.one" start="20260717190000 +0100" stop="20260717200000 +0100">
                <title>News</title>
                <desc>Evening news</desc>
                <category>News</category>
              </programme>
            </tv>
        """.trimIndent()

        val result = XmltvParser().parse("source-1", StringReader(xml))

        assertEquals(1, result.programmes.size)
        assertEquals("bbc.one", result.programmes.single().channelEpgId)
        assertEquals(Instant.parse("2026-07-17T18:00:00Z"), result.programmes.single().startAt)
    }
}
