package com.industrial.barcodescanner.presentation.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.catalog.ProductCatalogOpenHelper
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.PreferencesManager
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        val catalogImportResult: String? = null,   // null = idle, "" = error, "N products" = success
        val wifiCatalogState: WifiCatalogState = WifiCatalogState.IDLE,
        val wifiCatalogProgress: Float = 0f,
        val wifiCatalogStatus: String = ""
    )

    enum class WifiCatalogState { IDLE, DISCOVERING, DOWNLOADING, SUCCESS, ERROR }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.scanSoundFlow.collect { v -> _uiState.update { it.copy(scanSound = v) } }
        }
        viewModelScope.launch {
            preferencesManager.vibrationFlow.collect { v -> _uiState.update { it.copy(vibration = v) } }
        }
        refreshCatalogInfo()
    }

    private fun refreshCatalogInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = catalogOpenHelper.productCount()
            val ts = context.getDatabasePath("products.db").lastModified()
            val label = if (ts > 0L && count > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
            } else if (count == 0) "Empty — pull from PC or import a file"
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
            val pcs = withContext(Dispatchers.IO) { WifiDiscovery.discover(2000) }
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

    fun resetWifiCatalogState() {
        _uiState.update {
            it.copy(wifiCatalogState = WifiCatalogState.IDLE, wifiCatalogProgress = 0f, wifiCatalogStatus = "")
        }
    }
}
