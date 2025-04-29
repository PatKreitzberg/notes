// app/src/main/java/com/wyldsoft/notes/sync/SyncWorker.kt
package com.wyldsoft.notes.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wyldsoft.notes.NotesApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for sync operations
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as NotesApp
            val syncManager = app.syncManager

            // Check network conditions
            val networkMonitor = NetworkMonitor(applicationContext)
            if (!networkMonitor.canSync(syncManager.syncOnlyOnWifi)) {
                return@withContext Result.retry()
            }

            // Perform sync
            val success = syncManager.performSync()

            return@withContext if (success) {
                Result.success()
            } else {
                // Only retry if we had a connection error
                if (syncManager.syncState.value == SyncState.ERROR) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            // Log error
            android.util.Log.e("SyncWorker", "Sync failed: ${e.message}", e)

            // Retry on network errors
            return@withContext if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}