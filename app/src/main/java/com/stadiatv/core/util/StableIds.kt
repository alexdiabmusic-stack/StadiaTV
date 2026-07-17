package com.stadiatv.core.util

import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer

object StableIds {
    fun normalizeName(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKC).replace(Regex("\\s+"), " ")

    fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun m3uMediaId(sourceId: String, epgId: String?, name: String, group: String?, streamUrl: String): String {
        val host = runCatching { URI(streamUrl).host.orEmpty().lowercase() }.getOrDefault("")
        val key = if (!epgId.isNullOrBlank()) "$epgId|$host" else "${normalizeName(name)}|${normalizeName(group.orEmpty())}|$streamUrl"
        return "$sourceId:live:${fingerprint(key).take(24)}"
    }

    fun xtreamMediaId(sourceId: String, kind: String, providerItemId: String): String =
        "$sourceId:${kind.lowercase()}:$providerItemId"
}
