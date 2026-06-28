package com.industrial.barcodescanner.utils

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that checks for app updates every 24 hours.
 * If a newer version is available it fires a notification — no user
 * interaction needed.
 *
 * Uses Hilt injection so it can share the same singleton instances
 * as the rest of the app.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val info = context.packageManager
                .getPackageInfo(context.packageName, 0)
            val currentCode = PackageInfoCompat.getLongVersionCode(info).toInt()

            when (val result = UpdateChecker.check(currentCode)) {
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    ApkInstaller.postUpdateNotification(
                        context,
                        result.info.latestVersionName
                    )
                    Result.success()
                }
                is UpdateChecker.CheckResult.UpToDate -> Result.success()
                is UpdateChecker.CheckResult.Error   -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "update_check"

        /**
         * Schedules a periodic update check every 24 hours.
         * Safe to call multiple times — uses [ExistingPeriodicWorkPolicy.KEEP]
         * so a running schedule is never restarted unnecessarily.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // Spread the first run across the first 3 hours so it doesn't
                // fire immediately on every app launch.
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Call this if you want to cancel all scheduled update checks. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
