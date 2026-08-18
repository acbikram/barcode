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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val scanSoundKey = booleanPreferencesKey("scan_sound")
    private val vibrationKey = booleanPreferencesKey("vibration")
    private val languagePromptShownKey = booleanPreferencesKey("language_prompt_shown")
    private val lastTagTypeKey = stringPreferencesKey("last_tag_type")
    private val lastUnitTypeKey = stringPreferencesKey("last_unit_type")
    private val wifiHostKey = stringPreferencesKey("wifi_host")
    private val wifiPortKey = stringPreferencesKey("wifi_port")
    private val lastBatchCsvKey = stringPreferencesKey("last_batch_csv")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val scanSoundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[scanSoundKey] ?: true
    }

    val vibrationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[vibrationKey] ?: true
    }

    /** One of dark, light, or system. Defaults to the original dark appearance. */
    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "dark"
    }

    /** True once the user has been shown the first-launch language picker. */
    val languagePromptShownFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[languagePromptShownKey] ?: false
    }

    suspend fun setLanguagePromptShown() {
        context.dataStore.edit { prefs ->
            prefs[languagePromptShownKey] = true
        }
    }

    suspend fun setScanSound(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[scanSoundKey] = enabled
        }
    }

    suspend fun setVibration(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[vibrationKey] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        require(mode in setOf("dark", "light", "system")) { "Unsupported theme mode: $mode" }
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
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

    fun getLastTagType(): String = runCatching {
        runBlocking { context.dataStore.data.map { it[lastTagTypeKey] ?: "A4" }.first() }
    }.getOrDefault("A4")

    fun getLastUnitType(): String = runCatching {
        runBlocking { context.dataStore.data.map { it[lastUnitTypeKey] ?: "PCS" }.first() }
    }.getOrDefault("PCS")

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

    fun getWifiHost(): String = runCatching {
        runBlocking { context.dataStore.data.map { it[wifiHostKey] ?: "" }.first() }
    }.getOrDefault("")

    fun getWifiPort(): String = runCatching {
        runBlocking { context.dataStore.data.map { it[wifiPortKey] ?: "8765" }.first() }
    }.getOrDefault("8765")

    /** Stores the most recently sent batch CSV so it can be re-sent later. */
    suspend fun setLastBatchCsv(csv: String) {
        context.dataStore.edit { prefs -> prefs[lastBatchCsvKey] = csv }
    }

    fun getLastBatchCsv(): String = runCatching {
        runBlocking { context.dataStore.data.map { it[lastBatchCsvKey] ?: "" }.first() }
    }.getOrDefault("")

    /**
     * Synchronous helpers used by the ViewModel.
     * DataStore keeps its state in memory after the first load, so these
     * runBlocking calls are effectively instant after app start.
     */
    fun isScanSoundEnabled(): Boolean = runCatching {
        runBlocking { scanSoundFlow.first() }
    }.getOrDefault(true)

    fun isVibrationEnabled(): Boolean = runCatching {
        runBlocking { vibrationFlow.first() }
    }.getOrDefault(true)
}
