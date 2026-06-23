package com.industrial.barcodescanner.presentation.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.repository.ProductCatalogRepository
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.PreferencesManager
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
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val repository: ScannedItemRepository,
    private val catalogRepository: ProductCatalogRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class SettingsUiState(
        val scanSound: Boolean = true,
        val vibration: Boolean = true,
        val catalogImportState: CatalogImportState = CatalogImportState.IDLE,
        val catalogLastModified: String = "",
        val catalogError: String? = null
    )

    enum class CatalogImportState { IDLE, IMPORTING, SUCCESS, ERROR }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.scanSoundFlow.collect { sound ->
                _uiState.update { it.copy(scanSound = sound) }
            }
        }
        viewModelScope.launch {
            preferencesManager.vibrationFlow.collect { vibration ->
                _uiState.update { it.copy(vibration = vibration) }
            }
        }
        loadCatalogInfo()
    }

    private fun loadCatalogInfo() {
        val ts = catalogRepository.catalogLastModified()
        val label = if (ts > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        } else "Bundled"
        _uiState.update { it.copy(catalogLastModified = label) }
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

    /**
     * Imports a products.db file the user picked via the file picker.
     * Copies the file to a temp location first (content URIs can't be
     * used directly as a File), then hands it to the catalog repository.
     */
    fun importCatalogDb(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(catalogImportState = CatalogImportState.IMPORTING, catalogError = null) }
            try {
                withContext(Dispatchers.IO) {
                    val tmp = File(context.cacheDir, "products_import.db")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not open file")
                    catalogRepository.importFromFile(tmp)
                    tmp.delete()
                }
                loadCatalogInfo()
                _uiState.update { it.copy(catalogImportState = CatalogImportState.SUCCESS) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        catalogImportState = CatalogImportState.ERROR,
                        catalogError = e.message ?: "Import failed"
                    )
                }
            }
        }
    }

    fun resetCatalogImportState() {
        _uiState.update { it.copy(catalogImportState = CatalogImportState.IDLE, catalogError = null) }
    }
}
