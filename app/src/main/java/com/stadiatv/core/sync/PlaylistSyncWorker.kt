package com.stadiatv.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PlaylistSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        return when (syncManager.syncNow(sourceId)) {
            is AdapterResult.Success -> Result.success()
            is AdapterResult.Failure -> Result.retry()
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
    }
}
