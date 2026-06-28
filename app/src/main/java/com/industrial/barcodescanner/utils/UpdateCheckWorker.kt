package com.industrial.barcodescanner.utils

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Plain CoroutineWorker (no Hilt) that checks for app updates every 24 hours.
 * UpdateChecker and ApkInstaller are plain objects — no injection needed.
 */
class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentCode = PackageInfoCompat.getLongVersionCode(info).toInt()

            when (val result = UpdateChecker.check(currentCode)) {
                is UpdateChecker.CheckResult.UpdateAvailable ->
                    ApkInstaller.postUpdateNotification(context, result.info.latestVersionName)
                        .let { Result.success() }
                is UpdateChecker.CheckResult.UpToDate -> Result.success()
                is UpdateChecker.CheckResult.Error   -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
