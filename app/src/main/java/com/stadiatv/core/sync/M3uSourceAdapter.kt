package com.stadiatv.core.sync

import com.stadiatv.core.database.SourceDao
import com.stadiatv.core.model.MediaKind
import com.stadiatv.core.network.ProviderHttpClient
import com.stadiatv.core.parser.M3uParser
import com.stadiatv.core.util.Redactor
import com.stadiatv.core.util.StableIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.StringReader
import javax.inject.Inject

class M3uSourceAdapter @Inject constructor(
    private val sourceDao: SourceDao,
    private val httpClient: ProviderHttpClient,
    private val parser: M3uParser,
) : PlaylistSourceAdapter {
    override suspend fun validateSource(sourceId: String): SourceValidationResult {
        val source = sourceDao.getSource(sourceId) ?: return SourceValidationResult(false, AdapterErrorCode.INVALID_URL, "Source not found")
        val url = source.playlistUrl.orEmpty()
        return if (httpClient.validateScheme(url)) SourceValidationResult(true) else SourceValidationResult(false, AdapterErrorCode.INVALID_URL, "Unsupported URL scheme")
    }

    override suspend fun fetchCategories(sourceId: String): AdapterResult<List<CategoryPayload>> = parse(sourceId).map { result ->
        result.entries.map { it.attributes["group-title"].orEmpty().ifBlank { "Uncategorized" } }
            .distinct()
            .mapIndexed { index, name ->
                CategoryPayload(
                    id = "$sourceId:cat:${StableIds.fingerprint(name).take(16)}",
                    sourceId = sourceId,
                    providerCategoryId = null,
                    name = name,
                    kind = MediaKind.LIVE,
                    sortOrder = index,
                )
            }
    }

    override suspend fun fetchLiveChannels(sourceId: String): AdapterResult<List<MediaPayload>> = parse(sourceId).map { result ->
        result.entries.map { entry ->
            val attrs = entry.attributes
            val name = attrs["tvg-name"]?.ifBlank { null } ?: entry.name
            val group = attrs["group-title"].orEmpty().ifBlank { "Uncategorized" }
            MediaPayload(
                id = StableIds.m3uMediaId(sourceId, attrs["tvg-id"], name, group, entry.streamUrl),
                sourceId = sourceId,
                providerItemId = attrs["tvg-id"],
                categoryId = "$sourceId:cat:${StableIds.fingerprint(group).take(16)}",
                kind = if (attrs["radio"] == "true" || attrs["radio"] == "1") MediaKind.RADIO else MediaKind.LIVE,
                name = name,
                logoUrl = attrs["tvg-logo"],
                posterUrl = null,
                epgId = attrs["tvg-id"] ?: attrs["tvg-name"],
                containerExtension = entry.streamUrl.substringBefore('?').substringAfterLast('.', "").ifBlank { null },
                directStreamUrl = entry.streamUrl,
                contentFingerprint = StableIds.fingerprint("${name}|${group}|${entry.streamUrl}|${attrs["tvg-logo"].orEmpty()}"),
            )
        }
    }

    override suspend fun fetchMovies(sourceId: String): AdapterResult<List<MediaPayload>> = AdapterResult.Success(emptyList())
    override suspend fun fetchSeries(sourceId: String): AdapterResult<List<SeriesPayload>> = AdapterResult.Success(emptyList())
    override suspend fun fetchEpg(sourceId: String): AdapterResult<EpgPayload> = AdapterResult.Success(EpgPayload(0))

    private suspend fun parse(sourceId: String): AdapterResult<com.stadiatv.core.parser.M3uParseResult> = withContext(Dispatchers.IO) {
        val source = sourceDao.getSource(sourceId) ?: return@withContext AdapterResult.Failure(AdapterErrorCode.INVALID_URL, "Source not found")
        val url = source.playlistUrl ?: return@withContext AdapterResult.Failure(AdapterErrorCode.INVALID_URL, "Playlist URL is blank")
        if (!httpClient.validateScheme(url)) return@withContext AdapterResult.Failure(AdapterErrorCode.INVALID_URL, "Unsupported URL scheme")
        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.catalogueClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext AdapterResult.Failure(AdapterErrorCode.SERVER_ERROR, "Server returned HTTP ${response.code}")
                val body = response.body ?: return@withContext AdapterResult.Failure(AdapterErrorCode.EMPTY_CATALOGUE, "Empty response")
                val result = body.charStream().use { parser.parse(it, url) }
                if (result.entries.isEmpty()) AdapterResult.Failure(AdapterErrorCode.EMPTY_CATALOGUE, "Playlist contained no playable entries")
                else AdapterResult.Success(result, result.warnings.map { it.code })
            }
        } catch (err: Exception) {
            AdapterResult.Failure(AdapterErrorCode.UNKNOWN_REDACTED_ERROR, Redactor.message(err.message))
        }
    }

    private inline fun <T, R> AdapterResult<T>.map(transform: (T) -> R): AdapterResult<R> = when (this) {
        is AdapterResult.Success -> AdapterResult.Success(transform(value), warnings)
        is AdapterResult.Failure -> this
    }
}
