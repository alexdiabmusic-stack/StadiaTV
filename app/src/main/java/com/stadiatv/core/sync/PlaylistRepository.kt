package com.stadiatv.core.sync

import com.stadiatv.core.database.MediaItemDao
import com.stadiatv.core.database.PlaybackHistoryDao
import com.stadiatv.core.database.PlaybackHistoryEntity
import com.stadiatv.core.database.SourceDao
import com.stadiatv.core.database.toDomain
import com.stadiatv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val sourceDao: SourceDao,
    private val mediaItemDao: MediaItemDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
) {
    fun observeLiveChannels(limit: Int = 500): Flow<List<MediaItem>> =
        mediaItemDao.observeLive(limit).map { items -> items.map { it.toDomain() } }

    fun observeSources() = sourceDao.observeSources().map { list -> list.map { it.toDomain() } }

    fun searchChannels(query: String, limit: Int = 50): Flow<List<MediaItem>> =
        mediaItemDao.search(query.trim().lowercase(), limit).map { items -> items.map { it.toDomain() } }

    suspend fun recordPlayback(mediaItemId: String, positionMs: Long, durationMs: Long?, completed: Boolean) {
        playbackHistoryDao.upsert(
            PlaybackHistoryEntity(
                mediaItemId = mediaItemId,
                lastPlayedAt = Instant.now(),
                positionMs = positionMs,
                durationMs = durationMs,
                completed = completed,
            ),
        )
    }
}
