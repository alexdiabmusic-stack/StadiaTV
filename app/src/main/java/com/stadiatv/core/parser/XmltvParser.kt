package com.stadiatv.core.parser

import com.stadiatv.core.model.Programme
import com.stadiatv.core.util.StableIds
import java.io.Reader
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

data class ParsedXmltv(
    val programmes: List<Programme>,
    val warnings: List<ParserWarning>,
)

class XmltvParser(
    private val maxProgrammes: Int = 100_000,
    private val maxDepth: Int = 32,
) {
    fun parse(sourceId: String, reader: Reader): ParsedXmltv {
        val programmes = mutableListOf<Programme>()
        val warnings = mutableListOf<ParserWarning>()

        val handler = object : DefaultHandler() {
            private var current: MutableProgramme? = null
            private var textTarget: String? = null
            private var textBuffer = StringBuilder()
            private var depth = 0
            private var locator: Locator? = null

            override fun setDocumentLocator(locator: Locator?) {
                this.locator = locator
            }

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                depth += 1
                if (depth > maxDepth) {
                    warnings += ParserWarning(lineNumber(), "XML_DEPTH_LIMIT", "XML depth limit exceeded")
                    throw StopParsingException()
                }

                when (elementName(localName, qName)) {
                    "programme" -> current = MutableProgramme(
                        channel = attributes.getValue("channel").orEmpty(),
                        start = parseXmltvTime(attributes.getValue("start")),
                        stop = parseXmltvTime(attributes.getValue("stop")),
                    )
                    "icon" -> current?.iconUrl = attributes.getValue("src")
                    "title", "desc", "category" -> {
                        textTarget = elementName(localName, qName)
                        textBuffer = StringBuilder()
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (current != null && textTarget != null) {
                    textBuffer.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (elementName(localName, qName)) {
                    "programme" -> {
                        val programme = current
                        if (programme?.start != null && programme.channel.isNotBlank() && !programme.title.isNullOrBlank()) {
                            val hash = StableIds.fingerprint("${programme.title}|${programme.description.orEmpty()}").take(16)
                            programmes += Programme(
                                id = "$sourceId:${programme.channel}:${programme.start}:$hash",
                                sourceId = sourceId,
                                channelEpgId = programme.channel,
                                startAt = programme.start,
                                endAt = programme.stop,
                                title = programme.title.orEmpty(),
                                description = programme.description,
                                category = programme.category,
                                iconUrl = programme.iconUrl,
                            )
                        } else {
                            warnings += ParserWarning(lineNumber(), "INVALID_PROGRAMME", "Programme missing channel, start, or title")
                        }
                        current = null
                        if (programmes.size >= maxProgrammes) {
                            warnings += ParserWarning(lineNumber(), "PROGRAMME_LIMIT", "Programme limit reached")
                            throw StopParsingException()
                        }
                    }
                    "title", "desc", "category" -> commitText()
                    "icon" -> textTarget = null
                }
                depth -= 1
            }

            private fun commitText() {
                val programme = current
                val target = textTarget
                val value = textBuffer.toString().trim()
                if (programme != null && target != null && value.isNotBlank()) {
                    when (target) {
                        "title" -> if (programme.title == null) programme.title = value
                        "desc" -> if (programme.description == null) programme.description = value
                        "category" -> if (programme.category == null) programme.category = value
                    }
                }
                textTarget = null
                textBuffer = StringBuilder()
            }

            private fun lineNumber(): Int = locator?.lineNumber ?: -1
        }

        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        try {
            factory.newSAXParser().parse(InputSource(reader), handler)
        } catch (_: StopParsingException) {
        }
        return ParsedXmltv(programmes, warnings)
    }

    fun parseXmltvTime(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val compact = value.trim()
        val main = compact.take(14)
        val offset = compact.drop(14).trim().ifBlank { "+0000" }
        return runCatching {
            val local = LocalDateTime.parse(main, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val normalizedOffset = offset.substring(0, 3) + ":" + offset.substring(3, 5)
            OffsetDateTime.of(local, ZoneOffset.of(normalizedOffset)).toInstant()
        }.getOrNull()
    }

    private data class MutableProgramme(
        val channel: String,
        val start: Instant?,
        val stop: Instant?,
        var title: String? = null,
        var description: String? = null,
        var category: String? = null,
        var iconUrl: String? = null,
    )

    private class StopParsingException : SAXException()

    private fun elementName(localName: String?, qName: String?): String =
        qName?.takeIf { it.isNotBlank() } ?: localName.orEmpty()
}
