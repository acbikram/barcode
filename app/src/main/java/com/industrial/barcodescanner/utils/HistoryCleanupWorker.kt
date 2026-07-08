package com.industrial.barcodescanner.utils

import android.content.Context
import androidx.work.*
import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import java.util.concurrent.TimeUnit

/**
 * Deletes scan history items older than 8 hours.
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
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            val deleted = BarcodeDatabase.getInstance(context)
                .scannedItemDao()
                .deleteOlderThan(cutoff)
            if (deleted > 0) {
                android.util.Log.i("HistoryCleanup", "Deleted $deleted item(s) older than 8h")
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Items older than this are removed. */
        private const val MAX_AGE_MS = 8L * 60 * 60 * 1000   // 8 hours

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
