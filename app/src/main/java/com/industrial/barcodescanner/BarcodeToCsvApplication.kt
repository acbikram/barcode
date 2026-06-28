package com.industrial.barcodescanner

import android.app.Application
import com.industrial.barcodescanner.utils.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BarcodeToCsvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
    }
}
