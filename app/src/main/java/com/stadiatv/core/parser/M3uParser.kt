package com.stadiatv.core.parser

import kotlinx.coroutines.ensureActive
import java.io.BufferedReader
import java.io.Reader
import java.net.URI
import kotlin.coroutines.coroutineContext

data class ParsedM3uEntry(
    val name: String,
    val streamUrl: String,
    val attributes: Map<String, String>,
    val options: Map<String, String>,
    val sourceLineNumber: Int,
)

data class ParserWarning(
    val lineNumber: Int,
    val code: String,
    val message: String,
)

data class M3uParseResult(
    val entries: List<ParsedM3uEntry>,
    val warnings: List<ParserWarning>,
)

class M3uParser(
    private val maxLineLength: Int = 64 * 1024,
) {
    suspend fun parse(reader: Reader, playlistUrl: String?): M3uParseResult {
        val entries = mutableListOf<ParsedM3uEntry>()
        val warnings = mutableListOf<ParserWarning>()
        val base = playlistUrl?.let(::runCatchingUri)
        var currentName = ""
        var currentAttributes = emptyMap<String, String>()
        val currentOptions = linkedMapOf<String, String>()
        var currentLine = 0

        BufferedReader(reader).useLines { lines ->
            val iterator = lines.iterator()
            var lineNumber = 0
            while (iterator.hasNext()) {
                coroutineContext.ensureActive()
                lineNumber += 1
                var line = iterator.next()
                if (lineNumber == 1) line = line.removePrefix("\uFEFF")
                if (line.length > maxLineLength) {
                    warnings += ParserWarning(lineNumber, "LINE_TOO_LONG", "Line exceeded maximum supported length")
                    line = line.take(maxLineLength)
                }
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue
                when {
                    trimmed.startsWith("#EXTINF", ignoreCase = true) -> {
                        val ext = parseExtinf(trimmed, lineNumber, warnings)
                        currentName = ext.first
                        currentAttributes = ext.second
                        currentOptions.clear()
                        currentLine = lineNumber
                    }
                    trimmed.startsWith("#EXTVLCOPT", ignoreCase = true) || trimmed.startsWith("#KODIPROP", ignoreCase = true) -> {
                        val split = trimmed.indexOf(':')
                        if (split > 0) currentOptions[trimmed.substring(1, split)] = trimmed.substring(split + 1)
                    }
                    trimmed.startsWith("#") -> Unit
                    currentLine == 0 -> warnings += ParserWarning(lineNumber, "ORPHAN_URL", "URL appeared before EXTINF")
                    else -> {
                        val resolved = resolveUrl(trimmed, base)
                        if (resolved == null) {
                            warnings += ParserWarning(lineNumber, "UNSUPPORTED_URL", "Unsupported or invalid stream URL")
                        } else {
                            val fallback = resolved.substringAfterLast('/').substringBefore('?').ifBlank { "Untitled channel" }
                            entries += ParsedM3uEntry(
                                name = currentAttributes["tvg-name"]?.ifBlank { null } ?: currentName.ifBlank { fallback },
                                streamUrl = resolved,
                                attributes = currentAttributes,
                                options = currentOptions.toMap(),
                                sourceLineNumber = currentLine,
                            )
                        }
                        currentLine = 0
                        currentName = ""
                        currentAttributes = emptyMap()
                        currentOptions.clear()
                    }
                }
            }
        }
        if (currentLine != 0) warnings += ParserWarning(currentLine, "MISSING_STREAM_URL", "EXTINF entry had no stream URL")
        return M3uParseResult(entries, warnings)
    }

    private fun parseExtinf(line: String, lineNumber: Int, warnings: MutableList<ParserWarning>): Pair<String, Map<String, String>> {
        val comma = line.lastIndexOf(',')
        val metadata = if (comma >= 0) line.substring(0, comma) else line.also {
            warnings += ParserWarning(lineNumber, "MISSING_COMMA", "EXTINF line has no display-name comma")
        }
        val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
        return name to parseAttributes(metadata)
    }

    fun parseAttributes(metadata: String): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        val quoted = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")
        quoted.findAll(metadata).forEach { attrs[it.groupValues[1]] = it.groupValues[2] }
        val withoutQuoted = quoted.replace(metadata, " ")
        Regex("""([\w-]+)\s*=\s*([^\s",]+)""").findAll(withoutQuoted).forEach {
            attrs.putIfAbsent(it.groupValues[1], it.groupValues[2])
        }
        return attrs
    }

    private fun resolveUrl(value: String, base: URI?): String? {
        val uri = runCatchingUri(value) ?: return null
        val resolved = if (uri.isAbsolute) uri else base?.resolve(uri) ?: return null
        return when (resolved.scheme?.lowercase()) {
            "http", "https" -> resolved.toString()
            else -> null
        }
    }

    private fun runCatchingUri(value: String): URI? = try {
        URI(value.trim())
    } catch (_: IllegalArgumentException) {
        null
    }
}
