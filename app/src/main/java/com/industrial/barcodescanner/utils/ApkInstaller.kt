package com.industrial.barcodescanner.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkInstaller {

    private const val CHANNEL_ID  = "app_update"
    private const val NOTIF_ID    = 9001

    /**
     * Downloads the APK from [url] into the app's cache directory,
     * then fires an install intent.
     *
     * [onProgress] is called on the IO thread with 0–100 progress values.
     */
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val destDir  = File(context.cacheDir, "updates").apply { mkdirs() }
        val destFile = File(destDir, "BarcodeToCSV-$versionName.apk")

        // Download with progress
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout    = 60_000
        }
        val total = conn.contentLength.toLong().coerceAtLeast(1L)
        conn.inputStream.use { input ->
            destFile.outputStream().use { output ->
                val buf = ByteArray(65_536)
                var downloaded = 0L
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 99))
                }
            }
        }
        conn.disconnect()
        onProgress(100)

        // Fire the install intent
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(install)
    }

    /** Posts a persistent "Update available" notification. */
    fun postUpdateNotification(context: Context, versionName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies when a new version is available" }
        )

        // Android 13+ requires a runtime grant before an app can post a notification.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        // Tapping the notification opens the app's launcher activity
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

        val pi = if (launchIntent != null) {
            PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available — v$versionName")
            .setContentText("Tap to open Barcode To CSV and update")
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}
