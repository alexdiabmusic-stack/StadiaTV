package com.stadiatv.core.sync

import androidx.room.withTransaction
import com.stadiatv.core.database.CategoryDao
import com.stadiatv.core.database.CategoryEntity
import com.stadiatv.core.database.MediaItemDao
import com.stadiatv.core.database.MediaItemEntity
import com.stadiatv.core.database.SourceDao
import com.stadiatv.core.database.StadiaDatabase
import com.stadiatv.core.database.SyncDao
import com.stadiatv.core.database.SyncRunEntity
import com.stadiatv.core.model.SourceStatus
import com.stadiatv.core.model.SourceType
import com.stadiatv.core.util.StableIds
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val db: StadiaDatabase,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val mediaItemDao: MediaItemDao,
    private val syncDao: SyncDao,
    private val m3uAdapter: M3uSourceAdapter,
    private val xtreamAdapter: XtreamSourceAdapter,
) {
    suspend fun syncNow(sourceId: String): AdapterResult<Int> {
        val source = sourceDao.getSource(sourceId) ?: return AdapterResult.Failure(AdapterErrorCode.INVALID_URL, "Source not found")
        val adapter = if (source.type == SourceType.M3U) m3uAdapter else xtreamAdapter
        val now = Instant.now()
        val runId = "sync-${UUID.randomUUID()}"
        sourceDao.updateStatus(sourceId, SourceStatus.SYNCING, now, null, now)
        syncDao.insertRun(SyncRunEntity(runId, sourceId, now, null, "RUNNING", 0, 0, null))

        val categories = adapter.fetchCategories(sourceId)
        val live = adapter.fetchLiveChannels(sourceId)
        if (live is AdapterResult.Failure) {
            sourceDao.updateStatus(sourceId, SourceStatus.ERROR, now, live.code.name, Instant.now())
            syncDao.finishRun(runId, Instant.now(), "ERROR", 0, 0, live.redactedMessage)
            return live
        }
        val categoryPayloads = (categories as? AdapterResult.Success)?.value.orEmpty()
        val livePayloads = (live as AdapterResult.Success).value
        if (livePayloads.isEmpty()) {
            sourceDao.updateStatus(sourceId, SourceStatus.ERROR, now, AdapterErrorCode.EMPTY_CATALOGUE.name, Instant.now())
            syncDao.finishRun(runId, Instant.now(), "ERROR", 0, live.warnings.size, "Empty catalogue")
            return AdapterResult.Failure(AdapterErrorCode.EMPTY_CATALOGUE, "Empty catalogue")
        }

        db.withTransaction {
            categoryDao.upsertAll(categoryPayloads.map { CategoryEntity(it.id, it.sourceId, it.providerCategoryId, it.name, it.kind, it.sortOrder) })
            mediaItemDao.upsertAll(livePayloads.map {
                val updatedAt = Instant.now()
                MediaItemEntity(
                    id = it.id,
                    sourceId = it.sourceId,
                    providerItemId = it.providerItemId,
                    categoryId = it.categoryId,
                    kind = it.kind,
                    name = it.name,
                    normalizedName = StableIds.normalizeName(it.name),
                    logoUrl = it.logoUrl,
                    posterUrl = it.posterUrl,
                    epgId = it.epgId,
                    containerExtension = it.containerExtension,
                    directStreamUrl = it.directStreamUrl,
                    contentFingerprint = it.contentFingerprint,
                    lastSeenSyncId = runId,
                    unavailableSince = null,
                    missingSyncCount = 0,
                    createdAt = updatedAt,
                    updatedAt = updatedAt,
                )
            })
            mediaItemDao.markUnseenMissing(sourceId, runId, Instant.now(), Instant.now())
            mediaItemDao.purgeExpiredMissing(sourceId, 3)
            sourceDao.markSynced(sourceId, SourceStatus.READY, Instant.now(), Instant.now())
            syncDao.finishRun(runId, Instant.now(), "SUCCESS", livePayloads.size, live.warnings.size, null)
        }
        return AdapterResult.Success(livePayloads.size, live.warnings)
    }
}
