package com.stadiatv.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stadiatv.core.model.MediaKind
import com.stadiatv.core.model.SourceStatus
import com.stadiatv.core.model.SourceType
import java.time.Instant

@Entity(
    tableName = "playlist_sources",
    indices = [Index("type"), Index("status"), Index("enabled")],
)
data class PlaylistSourceEntity(
    @PrimaryKey val id: String,
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

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = PlaylistSourceEntity::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sourceId"), Index(value = ["sourceId", "providerCategoryId"]), Index("kind")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val providerCategoryId: String?,
    val name: String,
    val kind: MediaKind,
    val sortOrder: Int,
)

@Entity(
    tableName = "media_items",
    foreignKeys = [ForeignKey(
        entity = PlaylistSourceEntity::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "kind", "providerItemId"]),
        Index("categoryId"),
        Index("normalizedName"),
        Index("epgId"),
        Index("lastSeenSyncId"),
        Index("unavailableSince"),
    ],
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
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
    val missingSyncCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "programmes",
    foreignKeys = [ForeignKey(
        entity = PlaylistSourceEntity::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("sourceId"),
        Index(value = ["channelEpgId", "startAt"]),
        Index(value = ["sourceId", "channelEpgId", "startAt", "contentHash"], unique = true),
    ],
)
data class ProgrammeEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val channelEpgId: String,
    val startAt: Instant,
    val endAt: Instant?,
    val title: String,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
    val contentHash: String,
)

@Entity(tableName = "favorites", indices = [Index("createdAt")])
data class FavoriteEntity(
    @PrimaryKey val mediaItemId: String,
    val createdAt: Instant,
)

@Entity(tableName = "playback_history", indices = [Index("lastPlayedAt")])
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaItemId: String,
    val lastPlayedAt: Instant,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
)

@Entity(
    tableName = "epg_channel_overrides",
    indices = [Index(value = ["sourceId", "mediaItemId"], unique = true), Index("channelEpgId")],
)
data class EpgChannelOverrideEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val mediaItemId: String,
    val channelEpgId: String,
    val createdAt: Instant,
)

@Entity(tableName = "sync_runs", indices = [Index("sourceId"), Index("startedAt"), Index("status")])
data class SyncRunEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: String,
    val itemCount: Int,
    val warningCount: Int,
    val redactedError: String?,
)

@Entity(tableName = "source_metadata", indices = [Index(value = ["sourceId", "key"], unique = true)])
data class SourceMetadataEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val key: String,
    val value: String,
    val updatedAt: Instant,
)

@Entity(tableName = "series", indices = [Index("sourceId"), Index("providerSeriesId")])
data class SeriesEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val providerSeriesId: String?,
    val name: String,
    val posterUrl: String?,
    val updatedAt: Instant,
)

@Entity(tableName = "episodes", indices = [Index("seriesId"), Index("providerEpisodeId")])
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val seriesId: String,
    val providerEpisodeId: String?,
    val name: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val containerExtension: String?,
    val updatedAt: Instant,
)
