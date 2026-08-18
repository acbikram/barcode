package com.industrial.barcodescanner.presentation.screens.settings

import android.content.Context
import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.catalog.ProductCatalogOpenHelper
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.PreferencesManager
import com.industrial.barcodescanner.utils.ApkInstaller
import com.industrial.barcodescanner.utils.UpdateChecker
import com.industrial.barcodescanner.utils.WifiDiscovery
import com.industrial.barcodescanner.utils.WifiSender
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val repository: ScannedItemRepository,
    private val catalogOpenHelper: ProductCatalogOpenHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class SettingsUiState(
        val scanSound: Boolean = true,
        val vibration: Boolean = true,
        val catalogCount: Int = 0,
        val catalogLastUpdated: String = "",
        val catalogImporting: Boolean = false,
        val catalogImportResult: String? = null,
        val wifiCatalogState: WifiCatalogState = WifiCatalogState.IDLE,
        val wifiCatalogProgress: Float = 0f,
        val wifiCatalogStatus: String = "",
        // App update
        val updateState: UpdateState = UpdateState.IDLE,
        val updateVersionName: String = "",
        val updateApkUrl: String = "",          // stored so download doesn't need re-check
        val updateDownloadProgress: Int = 0,
        val updateError: String = "",
        val themeMode: String = "dark"
    )

    enum class WifiCatalogState { IDLE, DISCOVERING, DOWNLOADING, SUCCESS, ERROR }
    enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, ERROR }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.scanSoundFlow.collect { v -> _uiState.update { it.copy(scanSound = v) } }
        }
        viewModelScope.launch {
            preferencesManager.vibrationFlow.collect { v -> _uiState.update { it.copy(vibration = v) } }
        }
        viewModelScope.launch {
            preferencesManager.themeModeFlow.collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
        refreshCatalogInfo()
    }

    private fun refreshCatalogInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = catalogOpenHelper.productCount()
            val ts = context.getDatabasePath("products.db").lastModified()
            val label = if (ts > 0L && count > 0)
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()))
            else if (count == 0) "Empty — pull from PC or import a file"
            else "Bundled"
            _uiState.update { it.copy(catalogCount = count, catalogLastUpdated = label) }
        }
    }

    fun toggleScanSound(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setScanSound(enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVibration(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun clearAllRecords() {
        viewModelScope.launch { repository.deleteAllItems() }
    }

    // ── Catalog import (file picker) ─────────────────────────────────────────

    fun importCatalogFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(catalogImporting = true, catalogImportResult = null) }
            try {
                val count = withContext(Dispatchers.IO) {
                    // Copy to temp file first (content URIs aren't directly seekable)
                    val tmp = File(context.cacheDir, "catalog_import_tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: throw IllegalStateException("Cannot open file")
                    val n = catalogOpenHelper.importCatalog(tmp.inputStream())
                    tmp.delete()
                    n
                }
                refreshCatalogInfo()
                _uiState.update { it.copy(catalogImporting = false, catalogImportResult = "$count products loaded") }
            } catch (e: Exception) {
                _uiState.update { it.copy(catalogImporting = false, catalogImportResult = "Error: ${e.message}") }
            }
        }
    }

    fun clearCatalogImportResult() = _uiState.update { it.copy(catalogImportResult = null) }

    // ── Catalog pull from PC over WiFi (PTAGGDB1) ────────────────────────────

    fun pullCatalogFromPc() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(wifiCatalogState = WifiCatalogState.DISCOVERING,
                    wifiCatalogProgress = 0f, wifiCatalogStatus = "Searching for PC…")
            }
            val pcs = withContext(Dispatchers.IO) { WifiDiscovery.discover(context, 2500) }
            if (pcs.isEmpty()) {
                _uiState.update {
                    it.copy(wifiCatalogState = WifiCatalogState.ERROR,
                        wifiCatalogStatus = "No PC found. Make sure Price Tag app is open with WiFi receiver enabled.")
                }
                return@launch
            }
            val pc = pcs.first()
            _uiState.update {
                it.copy(wifiCatalogState = WifiCatalogState.DOWNLOADING,
                    wifiCatalogStatus = "Downloading catalog from ${pc.name}…")
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    val tmp = File(context.cacheDir, "catalog_wifi_tmp.db")
                    val totalBytes = tmp.outputStream().use { sink ->
                        WifiSender.pullCatalog(pc.ip, pc.port, sink)
                    }
                    val mb = totalBytes / 1_048_576.0
                    _uiState.update { s ->
                        s.copy(wifiCatalogStatus = "${"%.1f".format(mb)} MB downloaded", wifiCatalogProgress = 1f)
                    }
                    val n = catalogOpenHelper.importCatalog(tmp.inputStream())
                    tmp.delete()
                    n
                }
                refreshCatalogInfo()
                _uiState.update {
                    it.copy(wifiCatalogState = WifiCatalogState.SUCCESS,
                        wifiCatalogStatus = "✓ Catalog updated — $count products loaded")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(wifiCatalogState = WifiCatalogState.ERROR,
                        wifiCatalogStatus = e.message ?: "Download failed")
                }
            }
        }
    }

    private fun currentVersionCode(): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toInt()
    }

    fun resetWifiCatalogState() {
        _uiState.update {
            it.copy(wifiCatalogState = WifiCatalogState.IDLE, wifiCatalogProgress = 0f, wifiCatalogStatus = "")
        }
    }

    // ── App update ────────────────────────────────────────────────────────────

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateState = UpdateState.CHECKING, updateError = "") }
            val currentCode = currentVersionCode()
            when (val result = UpdateChecker.check(currentCode)) {
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    val info = result.info
                    _uiState.update {
                        it.copy(
                            updateState = UpdateState.AVAILABLE,
                            updateVersionName = info.latestVersionName,
                            updateApkUrl = info.apkDownloadUrl
                        )
                    }
                    ApkInstaller.postUpdateNotification(
                        context, info.latestVersionName
                    )
                }
                UpdateChecker.CheckResult.UpToDate ->
                    _uiState.update { it.copy(updateState = UpdateState.UP_TO_DATE) }
                is UpdateChecker.CheckResult.Error ->
                    _uiState.update { it.copy(updateState = UpdateState.ERROR, updateError = result.message) }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val apkUrl = _uiState.value.updateApkUrl
        val versionName = _uiState.value.updateVersionName
        if (apkUrl.isBlank()) { checkForUpdate(); return }

        viewModelScope.launch {
            _uiState.update { it.copy(updateState = UpdateState.DOWNLOADING, updateDownloadProgress = 0) }
            try {
                ApkInstaller.downloadAndInstall(
                    context     = context,
                    url         = apkUrl,
                    versionName = versionName,
                    onProgress  = { pct ->
                        _uiState.update { it.copy(updateDownloadProgress = pct) }
                    }
                )
                // State stays DOWNLOADING until user taps Install and app restarts
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        updateState = UpdateState.ERROR,
                        updateError = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    fun resetUpdateState() {
        _uiState.update { it.copy(updateState = UpdateState.IDLE, updateError = "", updateDownloadProgress = 0) }
    }
}
