package com.stadiatv.core.util

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Redactor {
    private val sensitiveQueryKeys = setOf("username", "user", "password", "pass", "token", "auth", "key", "signature", "sig")
    private val sensitiveHeaders = setOf("authorization", "cookie", "set-cookie")

    fun url(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val parsed = value.toHttpUrlOrNull() ?: return maskInlineSecrets(value)
        val builder = parsed.newBuilder().username("").password("")
        parsed.queryParameterNames.forEach { name ->
            if (name.lowercase() in sensitiveQueryKeys) {
                builder.setQueryParameter(name, "REDACTED")
            }
        }
        return maskCredentialPath(builder.build().toString())
    }

    fun headers(headers: Headers): Map<String, String> = headers.names().associateWith { name ->
        if (name.lowercase() in sensitiveHeaders) "REDACTED" else headers[name].orEmpty()
    }

    fun message(value: String?): String = maskCredentialPath(maskInlineSecrets(value.orEmpty()))

    private fun maskInlineSecrets(value: String): String =
        value.replace(Regex("(?i)(username|user|password|pass|token|auth|key|signature)=([^&\\s]+)"), "$1=REDACTED")

    private fun maskCredentialPath(value: String): String =
        value.replace(Regex("(?i)/(live|movie|series)/([^/]+)/([^/]+)/"), "/$1/REDACTED/REDACTED/")
}
