package com.stadiatv.core.network

import com.stadiatv.BuildConfig
import okhttp3.OkHttpClient
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderHeaders(
    val userAgent: String? = null,
    val referer: String? = null,
    val authorization: String? = null,
    val cookie: String? = null,
) {
    fun asMap(): Map<String, String> = buildMap {
        userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        referer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
        authorization?.takeIf(String::isNotBlank)?.let { put("Authorization", it) }
        cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
    }
}

@Singleton
class ProviderHttpClient @Inject constructor() {
    val catalogueClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(90))
        .callTimeout(Duration.ofMinutes(2))
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    fun validateScheme(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) ||
            (BuildConfig.ALLOW_USER_HTTP_SOURCES && url.startsWith("http://", ignoreCase = true))
}
