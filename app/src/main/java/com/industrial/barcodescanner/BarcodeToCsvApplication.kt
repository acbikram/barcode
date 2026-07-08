package com.industrial.barcodescanner

import android.app.Application
import com.industrial.barcodescanner.utils.HistoryCleanupWorker
import com.industrial.barcodescanner.utils.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BarcodeToCsvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
        HistoryCleanupWorker.schedule(this)   // auto-delete scans older than 8h
    }
}
