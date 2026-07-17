package com.stadiatv.core.sync

import com.stadiatv.core.database.SourceDao
import com.stadiatv.core.model.MediaKind
import com.stadiatv.core.network.ProviderHttpClient
import com.stadiatv.core.security.CredentialStore
import com.stadiatv.core.util.Redactor
import com.stadiatv.core.util.StableIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import javax.inject.Inject

class XtreamSourceAdapter @Inject constructor(
    private val sourceDao: SourceDao,
    private val credentialStore: CredentialStore,
    private val httpClient: ProviderHttpClient,
) : PlaylistSourceAdapter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun validateSource(sourceId: String): SourceValidationResult {
        val source = sourceDao.getSource(sourceId) ?: return SourceValidationResult(false, AdapterErrorCode.INVALID_URL, "Source not found")
        val base = source.baseUrl ?: return SourceValidationResult(false, AdapterErrorCode.INVALID_URL, "Server URL is blank")
        return if (httpClient.validateScheme(base)) SourceValidationResult(true) else SourceValidationResult(false, AdapterErrorCode.INVALID_URL, "Unsupported URL scheme")
    }

    override suspend fun fetchCategories(sourceId: String): AdapterResult<List<CategoryPayload>> = fetchArray(sourceId, "get_live_categories").map { array ->
        array.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val id = obj.string("category_id") ?: return@mapIndexedNotNull null
            CategoryPayload("$sourceId:cat:$id", sourceId, id, obj.string("category_name") ?: "Uncategorized", MediaKind.LIVE, index)
        }
    }

    override suspend fun fetchLiveChannels(sourceId: String): AdapterResult<List<MediaPayload>> = fetchArray(sourceId, "get_live_streams").map { array ->
        array.mapNotNull { element ->
            val obj = element.jsonObject
            val streamId = obj.string("stream_id") ?: return@mapNotNull null
            val ext = obj.string("container_extension") ?: "ts"
            MediaPayload(
                id = StableIds.xtreamMediaId(sourceId, "live", streamId),
                sourceId = sourceId,
                providerItemId = streamId,
                categoryId = obj.string("category_id")?.let { "$sourceId:cat:$it" },
                kind = MediaKind.LIVE,
                name = obj.string("name") ?: "Channel $streamId",
                logoUrl = obj.string("stream_icon"),
                posterUrl = null,
                epgId = obj.string("epg_channel_id"),
                containerExtension = ext,
                directStreamUrl = obj.string("direct_source")?.takeIf { it.startsWith("http") && !looksCredentialBearing(it) },
                contentFingerprint = StableIds.fingerprint("${obj.string("name")}|${obj.string("category_id")}|${obj.string("stream_icon")}|${obj.string("epg_channel_id")}"),
            )
        }
    }

    override suspend fun fetchMovies(sourceId: String): AdapterResult<List<MediaPayload>> = AdapterResult.Success(emptyList())
    override suspend fun fetchSeries(sourceId: String): AdapterResult<List<SeriesPayload>> = AdapterResult.Success(emptyList())
    override suspend fun fetchEpg(sourceId: String): AdapterResult<EpgPayload> = AdapterResult.Success(EpgPayload(0))

    private suspend fun fetchArray(sourceId: String, action: String): AdapterResult<JsonArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apiUrl(sourceId, action)).get().build()
            httpClient.catalogueClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) return@withContext AdapterResult.Failure(AdapterErrorCode.HTTP_AUTHENTICATION_FAILURE, "Authentication failed")
                if (!response.isSuccessful) return@withContext AdapterResult.Failure(AdapterErrorCode.SERVER_ERROR, "Server returned HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                AdapterResult.Success(element.jsonArray)
            }
        } catch (err: Exception) {
            AdapterResult.Failure(AdapterErrorCode.INVALID_JSON, Redactor.message(err.message))
        }
    }

    private suspend fun apiUrl(sourceId: String, action: String): String {
        val source = sourceDao.getSource(sourceId) ?: error("Source not found")
        val base = source.baseUrl ?: error("Server URL is blank")
        val username = credentialStore.getSecret(sourceId, "username").orEmpty()
        val password = credentialStore.getSecret(sourceId, "password").orEmpty()
        return "$base/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .addQueryParameter("action", action)
            .build()
            .toString()
    }

    private fun JsonObject.string(name: String): String? = get(name)?.stringOrNull()
    private fun JsonElement.stringOrNull(): String? = jsonPrimitive.contentOrNull
    private fun looksCredentialBearing(url: String): Boolean =
        Regex("(?i)(username=|password=|/live/[^/]+/[^/]+/|/movie/[^/]+/[^/]+/|/series/[^/]+/[^/]+/)").containsMatchIn(url)

    private inline fun <T, R> AdapterResult<T>.map(transform: (T) -> R): AdapterResult<R> = when (this) {
        is AdapterResult.Success -> AdapterResult.Success(transform(value), warnings)
        is AdapterResult.Failure -> this
    }
}
