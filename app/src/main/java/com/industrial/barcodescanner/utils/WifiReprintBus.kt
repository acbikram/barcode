package com.industrial.barcodescanner.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny hand-off used when the user taps "Reprint" in the Wi-Fi Print History.
 * The history screen stashes the CSV to re-send here and navigates to the
 * Export screen, which picks it up and runs the normal Share-WiFi flow
 * (so all the price-check / decision dialogs are reused, not duplicated).
 */
@Singleton
class WifiReprintBus @Inject constructor() {
    @Volatile
    private var pendingCsv: String? = null

    fun request(csv: String) {
        pendingCsv = csv
    }

    /** Returns the pending CSV (if any) and clears it. */
    fun consume(): String? {
        val v = pendingCsv
        pendingCsv = null
        return v
    }
}
