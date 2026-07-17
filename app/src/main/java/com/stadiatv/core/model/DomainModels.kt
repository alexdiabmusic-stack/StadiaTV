package com.stadiatv.core.model

import android.net.Uri
import java.time.Instant

enum class SourceType { M3U, XTREAM }

enum class MediaKind { LIVE, RADIO, MOVIE, SERIES, EPISODE }

enum class SourceStatus { IDLE, SYNCING, READY, ERROR, AUTHENTICATION_FAILED, DISABLED }

data class PlaylistSource(
    val id: String,
    val displayName: String,
    val type: SourceType,
    val baseUrl: String?,
    val playlistUrl: String?,
    val epgUrl: String?,
    val enabled: Boolean,
    val status: SourceStatus,
    val lastAttemptAt: Instant?,
    val lastSuccessfulSyncAt: Instant?,
    val lastErrorCode: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Category(
    val id: String,
    val sourceId: String,
    val providerCategoryId: String?,
    val name: String,
    val kind: MediaKind,
    val sortOrder: Int,
)

data class MediaItem(
    val id: String,
    val sourceId: String,
    val providerItemId: String?,
    val categoryId: String?,
    val kind: MediaKind,
    val name: String,
    val normalizedName: String,
    val logoUrl: String?,
    val posterUrl: String?,
    val epgId: String?,
    val containerExtension: String?,
    val directStreamUrl: String?,
    val contentFingerprint: String,
    val lastSeenSyncId: String?,
    val unavailableSince: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Programme(
    val id: String,
    val sourceId: String,
    val channelEpgId: String,
    val startAt: Instant,
    val endAt: Instant?,
    val title: String,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
)

data class Favorite(
    val mediaItemId: String,
    val createdAt: Instant,
)

data class PlaybackHistory(
    val mediaItemId: String,
    val lastPlayedAt: Instant,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
)

data class DrmConfiguration(
    val scheme: String,
    val licenseUri: Uri,
    val requestHeaders: Map<String, String> = emptyMap(),
)

data class PlaybackRequest(
    val mediaId: String,
    val uri: Uri,
    val mimeType: String?,
    val headers: Map<String, String>,
    val isLive: Boolean,
    val drm: DrmConfiguration?,
    val displayTitle: String,
)

enum class SyncPhase { VALIDATING, FETCHING, PARSING, WRITING, FINALIZING, COMPLETE, FAILED }

data class SyncProgress(
    val phase: SyncPhase,
    val processed: Int,
    val discovered: Int?,
    val warnings: Int,
)
