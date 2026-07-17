package com.stadiatv.core.sync

import com.stadiatv.core.model.MediaKind

sealed class AdapterResult<out T> {
    data class Success<T>(val value: T, val warnings: List<String> = emptyList()) : AdapterResult<T>()
    data class Failure(val code: AdapterErrorCode, val redactedMessage: String) : AdapterResult<Nothing>()
}

enum class AdapterErrorCode {
    INVALID_URL,
    DNS_FAILURE,
    CONNECTION_TIMEOUT,
    TLS_ERROR,
    HTTP_AUTHENTICATION_FAILURE,
    ACCOUNT_EXPIRED,
    ACCOUNT_DISABLED,
    RATE_LIMITED,
    SERVER_ERROR,
    INVALID_PLAYLIST,
    INVALID_JSON,
    INVALID_XMLTV,
    EMPTY_CATALOGUE,
    UNSUPPORTED_FORMAT,
    STORAGE_FAILURE,
    CANCELLED,
    UNKNOWN_REDACTED_ERROR,
}

data class SourceValidationResult(val valid: Boolean, val errorCode: AdapterErrorCode? = null, val redactedMessage: String? = null)
data class CategoryPayload(val id: String, val sourceId: String, val providerCategoryId: String?, val name: String, val kind: MediaKind, val sortOrder: Int)
data class MediaPayload(
    val id: String,
    val sourceId: String,
    val providerItemId: String?,
    val categoryId: String?,
    val kind: MediaKind,
    val name: String,
    val logoUrl: String?,
    val posterUrl: String?,
    val epgId: String?,
    val containerExtension: String?,
    val directStreamUrl: String?,
    val contentFingerprint: String,
)
data class SeriesPayload(val id: String, val sourceId: String, val providerSeriesId: String?, val name: String, val posterUrl: String?)
data class EpgPayload(val programmeCount: Int)

interface PlaylistSourceAdapter {
    suspend fun validateSource(sourceId: String): SourceValidationResult
    suspend fun fetchCategories(sourceId: String): AdapterResult<List<CategoryPayload>>
    suspend fun fetchLiveChannels(sourceId: String): AdapterResult<List<MediaPayload>>
    suspend fun fetchMovies(sourceId: String): AdapterResult<List<MediaPayload>>
    suspend fun fetchSeries(sourceId: String): AdapterResult<List<SeriesPayload>>
    suspend fun fetchEpg(sourceId: String): AdapterResult<EpgPayload>
}
