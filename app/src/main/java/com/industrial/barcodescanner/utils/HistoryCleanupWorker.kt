package com.industrial.barcodescanner.utils

import android.content.Context
import androidx.work.*
import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import java.util.concurrent.TimeUnit

/**
 * Deletes active scan history items older than 24 hours and keeps recycled
 * records available for restoration for a separate recovery window.
 *
 * Runs every hour via WorkManager, plus once immediately on every app
 * launch (see [runNow]) so stale items never survive an app restart.
 */
class HistoryCleanupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dao = BarcodeDatabase.getInstance(context).scannedItemDao()
            val activeDeleted = dao.deleteOlderThan(System.currentTimeMillis() - ACTIVE_MAX_AGE_MS)
            val recycledDeleted = dao.deleteRecycledOlderThan(System.currentTimeMillis() - RECYCLE_BIN_MAX_AGE_MS)
            if (activeDeleted > 0 || recycledDeleted > 0) {
                android.util.Log.i(
                    "HistoryCleanup",
                    "Removed $activeDeleted expired active item(s) and $recycledDeleted expired recycled item(s)"
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Active history is intentionally short-lived for the printing workflow. */
        private const val ACTIVE_MAX_AGE_MS = 24L * 60 * 60 * 1000   // 24 hours
        /** Restorable records remain available long enough to recover accidental deletions. */
        private const val RECYCLE_BIN_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

        private const val PERIODIC_NAME = "history_cleanup_periodic"
        private const val ONESHOT_NAME  = "history_cleanup_now"

        /** Schedules the hourly cleanup + runs one immediately. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)

            // Hourly periodic cleanup
            wm.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HistoryCleanupWorker>(1, TimeUnit.HOURS).build()
            )

            // Immediate one-shot cleanup on app launch
            wm.enqueueUniqueWork(
                ONESHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HistoryCleanupWorker>().build()
            )
        }
    }
}
