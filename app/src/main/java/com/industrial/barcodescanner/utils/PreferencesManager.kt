package com.industrial.barcodescanner.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val languagePromptShownKey = booleanPreferencesKey("language_prompt_shown")
    private val themePromptShownKey = booleanPreferencesKey("theme_prompt_shown")
    private val lastTagTypeKey = stringPreferencesKey("last_tag_type")
    private val lastUnitTypeKey = stringPreferencesKey("last_unit_type")
    private val wifiHostKey = stringPreferencesKey("wifi_host")
    private val wifiPortKey = stringPreferencesKey("wifi_port")
    private val lastBatchCsvKey = stringPreferencesKey("last_batch_csv")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val notificationPermissionPromptShownKey = booleanPreferencesKey("notification_permission_prompt_shown")
    private val shelfTagHostKey = stringPreferencesKey("shelf_tag_host")
    private val shelfTagPortKey = stringPreferencesKey("shelf_tag_port")
    private val shelfTagModeKey = stringPreferencesKey("shelf_tag_mode")
    private val shelfTagPrinterModelKey = stringPreferencesKey("shelf_tag_printer_model")
    private val shelfTagPrinterNameKey = stringPreferencesKey("shelf_tag_printer_name")
    private val shelfTagPrinterAddressKey = stringPreferencesKey("shelf_tag_printer_address")
    private val shelfTagMediaKey = stringPreferencesKey("shelf_tag_media")
    private val shelfTagLabelSizeKey = stringPreferencesKey("shelf_tag_label_size")
    private val shelfTagCopiesKey = stringPreferencesKey("shelf_tag_copies")
    private val shelfTagArabicKey = booleanPreferencesKey("shelf_tag_arabic")
    private val shelfTagCalibrateKey = booleanPreferencesKey("shelf_tag_calibrate")

    data class ShelfTagSettings(
        val host: String, val port: String, val mode: String, val printerModel: String,
        val printerName: String, val printerAddress: String, val media: String,
        val labelSize: String, val copies: String, val arabic: Boolean, val calibrate: Boolean
    )

    val shelfTagSettingsFlow: Flow<ShelfTagSettings> = context.dataStore.data.map { p ->
        ShelfTagSettings(
            p[shelfTagHostKey] ?: "192.168.1.1", p[shelfTagPortKey] ?: "11000", p[shelfTagModeKey] ?: "Socket",
            p[shelfTagPrinterModelKey] ?: "Zebra", p[shelfTagPrinterNameKey] ?: "", p[shelfTagPrinterAddressKey] ?: "",
            p[shelfTagMediaKey] ?: "Label With Gaps", p[shelfTagLabelSizeKey] ?: "4.13 X 2 Inch",
            p[shelfTagCopiesKey] ?: "1", p[shelfTagArabicKey] ?: true, p[shelfTagCalibrateKey] ?: false
        )
    }

    /** One of dark, light, or system. Defaults to the original dark appearance. */
    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "dark"
    }

    /** True once the user has been shown the first-launch language picker. */
    val languagePromptShownFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[languagePromptShownKey] ?: false
    }

    /** True once the user has completed the first-launch appearance choice. */
    val themePromptShownFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[themePromptShownKey] ?: false
    }

    /** Prevents repeatedly prompting users who have already made a notification choice. */
    val notificationPermissionPromptShownFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[notificationPermissionPromptShownKey] ?: false
    }

    suspend fun setLanguagePromptShown() {
        context.dataStore.edit { prefs ->
            prefs[languagePromptShownKey] = true
        }
    }

    suspend fun setThemePromptShown() {
        context.dataStore.edit { prefs ->
            prefs[themePromptShownKey] = true
        }
    }

    suspend fun setNotificationPermissionPromptShown() {
        context.dataStore.edit { prefs ->
            prefs[notificationPermissionPromptShownKey] = true
        }
    }

    suspend fun setThemeMode(mode: String) {
        require(mode in setOf("dark", "light", "system")) { "Unsupported theme mode: $mode" }
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
    }

    val lastTagTypeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lastTagTypeKey] ?: "A4"
    }

    val lastUnitTypeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lastUnitTypeKey] ?: "PCS"
    }

    val wifiHostFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[wifiHostKey] ?: ""
    }

    val wifiPortFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[wifiPortKey] ?: "8765"
    }

    val lastBatchCsvFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lastBatchCsvKey] ?: ""
    }

    /**
     * Remembers the last Tag Type / Unit Type the user picked while scanning,
     * so the next scan's pickers default to the same selection until the
     * user changes it. Copies always defaults back to 1 (not remembered).
     */
    suspend fun setLastTagType(tagType: String) {
        context.dataStore.edit { prefs -> prefs[lastTagTypeKey] = tagType }
    }

    suspend fun setLastUnitType(unitType: String) {
        context.dataStore.edit { prefs -> prefs[lastUnitTypeKey] = unitType }
    }

    /**
     * Remembers the last PC address used by "Share WiFi" so the dialog
     * pre-fills it next time. Port defaults to 8765 (the PC's default).
     */
    suspend fun setWifiHost(host: String) {
        context.dataStore.edit { prefs -> prefs[wifiHostKey] = host }
    }

    suspend fun setWifiPort(port: String) {
        context.dataStore.edit { prefs -> prefs[wifiPortKey] = port }
    }

    /** Stores the most recently sent batch CSV so it can be re-sent later. */
    suspend fun setLastBatchCsv(csv: String) {
        context.dataStore.edit { prefs -> prefs[lastBatchCsvKey] = csv }
    }

    suspend fun saveShelfTagSettings(
        host: String, port: String, mode: String, printerModel: String, printerName: String,
        printerAddress: String, media: String, labelSize: String, copies: String,
        arabic: Boolean, calibrate: Boolean
    ) {
        context.dataStore.edit { p ->
            p[shelfTagHostKey] = host; p[shelfTagPortKey] = port; p[shelfTagModeKey] = mode
            p[shelfTagPrinterModelKey] = printerModel; p[shelfTagPrinterNameKey] = printerName
            p[shelfTagPrinterAddressKey] = printerAddress; p[shelfTagMediaKey] = media
            p[shelfTagLabelSizeKey] = labelSize; p[shelfTagCopiesKey] = copies
            p[shelfTagArabicKey] = arabic; p[shelfTagCalibrateKey] = calibrate
        }
    }

}
