package com.alexdiab.stadiatv.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class HttpStatusException(val code: Int, val retryAfterSeconds: Long? = null, message: String? = null) :
    IOException(message ?: "HTTP $code")

/**
 * The one network entry point for data providers.
 *
 * - Coroutine native (cancelling the caller cancels the call).
 * - Bounded retries with jittered backoff for transient failures; HTTP 429 honours `Retry-After`.
 * - Optional short TTL cache and coalescing of identical in-flight GETs, so a screen change or a
 *   channel zap never refetches a scoreboard that was loaded a few seconds ago.
 */
class HttpGateway(val client: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<String>>()
    private val cache = object : LinkedHashMap<String, Cached>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    private class Cached(val body: String, val expiresAt: Long)

    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
        ttlMillis: Long = 0,
        retries: Int = 2,
    ): String {
        val key = cacheKey(url, headers)
        if (ttlMillis > 0) {
            synchronized(cache) { cache[key] }?.let { if (it.expiresAt > now()) return it.body }
        }
        // Coalesce identical in-flight requests onto one network call.
        val created = scope.async(start = CoroutineStart.LAZY) {
            val body = execute(Request.Builder().url(url).applyHeaders(headers).get().build(), retries)
            if (ttlMillis > 0) synchronized(cache) { cache[key] = Cached(body, now() + ttlMillis) }
            body
        }
        val existing = inFlight.putIfAbsent(key, created)
        val shared = if (existing != null) {
            created.cancel()
            existing
        } else {
            created.invokeOnCompletion { inFlight.remove(key, created) }
            created.start()
            created
        }
        return shared.await()
    }

    suspend fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
        ttlMillis: Long = 0,
        retries: Int = 2,
    ): JsonElement = parseJson(getString(url, headers, ttlMillis, retries))

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).applyHeaders(headers).post(body).build(), retries = 1)
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String {
        val body = json.toRequestBody("application/json".toMediaType())
        return execute(Request.Builder().url(url).applyHeaders(headers).post(body).build(), retries = 1)
    }

    /** A raw GET that returns bytes (gzip XMLTV, binary guides). */
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap(), retries: Int = 1): ByteArray {
        val request = Request.Builder().url(url).applyHeaders(headers).get().build()
        return attempt(request, retries) { response -> response.body.bytes() }
    }

    fun invalidate(urlPrefix: String? = null) {
        synchronized(cache) {
            if (urlPrefix == null) cache.clear() else cache.keys.removeAll { it.startsWith(urlPrefix) }
        }
    }

    private suspend fun execute(request: Request, retries: Int): String =
        attempt(request, retries) { response -> response.body.string() }

    private suspend fun <T> attempt(request: Request, retries: Int, read: (Response) -> T): T {
        var lastError: IOException? = null
        for (attemptNumber in 0..retries) {
            try {
                client.newCall(request).await().use { response ->
                    if (response.isSuccessful) return read(response)
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    throw HttpStatusException(response.code, retryAfter)
                }
            } catch (error: HttpStatusException) {
                lastError = error
                val transient = error.code == 408 || error.code == 429 || error.code >= 500
                if (!transient || attemptNumber == retries) throw error
                delay(backoff(attemptNumber, error.retryAfterSeconds))
            } catch (error: IOException) {
                lastError = error
                if (attemptNumber == retries) throw error
                delay(backoff(attemptNumber, null))
            }
        }
        throw lastError ?: IOException("Request failed")
    }

    private fun backoff(attempt: Int, retryAfterSeconds: Long?): Long {
        if (retryAfterSeconds != null) return (retryAfterSeconds.coerceIn(1, 8)) * 1000
        return (350L shl attempt) + Random.nextLong(0, 200)
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        headers.forEach { (name, value) -> header(name, value) }
        return this
    }

    private fun cacheKey(url: String, headers: Map<String, String>): String =
        if (headers.isEmpty()) url else url + "\u0000" + headers.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_CACHE_ENTRIES = 96
        const val USER_AGENT = "StadiaTV-FireTV/2.0 (Android TV)"

        /** Shared client: HTTP/2, brotli, small disk cache, sensible timeouts for a TV on Wi-Fi. */
        fun createClient(context: Context): OkHttpClient {
            val dispatcher = Dispatcher().apply {
                maxRequests = 48
                maxRequestsPerHost = 10
            }
            return OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .cache(Cache(File(context.cacheDir, "http"), 24L * 1024 * 1024))
                .addInterceptor(BrotliInterceptor)
                .addInterceptor { chain ->
                    val request = chain.request()
                    chain.proceed(
                        if (request.header("User-Agent") == null) {
                            request.newBuilder().header("User-Agent", USER_AGENT).build()
                        } else request
                    )
                }
                .build()
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
