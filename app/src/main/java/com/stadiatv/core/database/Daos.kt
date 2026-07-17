package com.stadiatv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.stadiatv.core.model.MediaKind
import com.stadiatv.core.model.SourceStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SourceDao {
    @Query("SELECT * FROM playlist_sources ORDER BY createdAt DESC")
    fun observeSources(): Flow<List<PlaylistSourceEntity>>

    @Query("SELECT * FROM playlist_sources WHERE id = :id")
    suspend fun getSource(id: String): PlaylistSourceEntity?

    @Upsert
    suspend fun upsert(source: PlaylistSourceEntity)

    @Query("UPDATE playlist_sources SET status = :status, lastAttemptAt = :attemptAt, lastErrorCode = :errorCode, updatedAt = :updatedAt WHERE id = :sourceId")
    suspend fun updateStatus(sourceId: String, status: SourceStatus, attemptAt: Instant?, errorCode: String?, updatedAt: Instant)

    @Query("UPDATE playlist_sources SET status = :status, lastSuccessfulSyncAt = :syncedAt, updatedAt = :updatedAt WHERE id = :sourceId")
    suspend fun markSynced(sourceId: String, status: SourceStatus, syncedAt: Instant, updatedAt: Instant)

    @Query("DELETE FROM playlist_sources WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId ORDER BY sortOrder, name")
    fun observeCategories(sourceId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)
}

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE kind IN ('LIVE','RADIO') AND unavailableSince IS NULL ORDER BY name LIMIT :limit")
    fun observeLive(limit: Int = 500): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE sourceId = :sourceId AND kind = :kind AND providerItemId = :providerItemId LIMIT 1")
    suspend fun findProviderItem(sourceId: String, kind: MediaKind, providerItemId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE normalizedName LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<MediaItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET unavailableSince = :missingAt, missingSyncCount = missingSyncCount + 1, updatedAt = :updatedAt WHERE sourceId = :sourceId AND lastSeenSyncId != :syncRunId AND unavailableSince IS NULL")
    suspend fun markUnseenMissing(sourceId: String, syncRunId: String, missingAt: Instant, updatedAt: Instant)

    @Query("DELETE FROM media_items WHERE sourceId = :sourceId AND unavailableSince IS NOT NULL AND missingSyncCount >= :graceSyncs")
    suspend fun purgeExpiredMissing(sourceId: String, graceSyncs: Int)
}

@Dao
interface ProgrammeDao {
    @Query("SELECT * FROM programmes WHERE channelEpgId = :epgId AND startAt < :to AND (endAt IS NULL OR endAt > :from) ORDER BY startAt")
    fun observeWindow(epgId: String, from: Instant, to: Instant): Flow<List<ProgrammeEntity>>

    @Upsert
    suspend fun upsertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE endAt < :before")
    suspend fun deleteOlderThan(before: Instant)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaItemId = :mediaItemId")
    suspend fun remove(mediaItemId: String)
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clear()
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: SyncRunEntity)

    @Query("UPDATE sync_runs SET finishedAt = :finishedAt, status = :status, itemCount = :itemCount, warningCount = :warningCount, redactedError = :redactedError WHERE id = :runId")
    suspend fun finishRun(runId: String, finishedAt: Instant, status: String, itemCount: Int, warningCount: Int, redactedError: String?)
}

@Dao
interface MetadataDao {
    @Upsert
    suspend fun upsertAll(values: List<SourceMetadataEntity>)

    @Query("SELECT * FROM source_metadata WHERE sourceId = :sourceId AND key = :key LIMIT 1")
    suspend fun get(sourceId: String, key: String): SourceMetadataEntity?
}
