package com.industrial.barcodescanner.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.industrial.barcodescanner.R

/**
 * Keeps an in-flight Wi-Fi export alive while the user minimizes the app or
 * locks the device. The transfer itself remains in the ViewModel so its live
 * result and any PC decision remain visible when the user returns.
 */
class WifiTransferForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val stage = intent?.getStringExtra(EXTRA_STAGE) ?: STAGE_CONNECTING
        startForeground(NOTIFICATION_ID, buildNotification(stage))
        acquireWakeLock()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:WifiTransfer"
            ).apply { setReferenceCounted(false) }
        }
        wakeLock?.let { lock -> if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wifi_working_title),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(stage: String): Notification {
        val text = when (stage) {
            STAGE_PRINTING -> getString(R.string.wifi_stage_printing)
            STAGE_CHECKING -> getString(R.string.wifi_stage_checking)
            else -> getString(R.string.wifi_stage_connecting)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.wifi_working_title))
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wifi_transfer"
        private const val NOTIFICATION_ID = 6101
        private const val EXTRA_STAGE = "stage"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val STAGE_CONNECTING = "connecting"
        const val STAGE_CHECKING = "checking"
        const val STAGE_PRINTING = "printing"

        fun start(context: Context, stage: String = STAGE_CONNECTING) {
            val intent = Intent(context, WifiTransferForegroundService::class.java)
                .putExtra(EXTRA_STAGE, stage)
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(context: Context, stage: String) = start(context, stage)

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiTransferForegroundService::class.java))
        }
    }
}
