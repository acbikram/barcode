package com.industrial.barcodescanner.presentation.screens.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import com.industrial.barcodescanner.data.local.entity.toEntity
import com.industrial.barcodescanner.data.local.catalog.ProductCatalogOpenHelper
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.JsonBackup
import com.industrial.barcodescanner.utils.WifiDiscovery
import com.industrial.barcodescanner.utils.WifiSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    private val catalogHelper: ProductCatalogOpenHelper,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    data class BackupUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val catalogCount: Int? = null
    )

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun backupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false) }
            try {
                val items = repository.getAllItems().first()
                val entities: List<ScannedItemEntity> = items.map { it.toEntity() }
                val contentResolver: ContentResolver = context.contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    JsonBackup.exportToJson(outputStream, entities)
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun restoreFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false) }
            try {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val entities = JsonBackup.importFromJson(inputStream)
                    repository.deleteAllItems()
                    entities.forEach { repository.insertItem(it) }
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun importCatalogFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false, catalogCount = null) }
            try {
                val count = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        catalogHelper.importCatalog(input)
                    } ?: throw IllegalStateException("Could not open the selected file.")
                }
                _uiState.update { it.copy(isLoading = false, success = true, catalogCount = count) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Catalog import failed.") }
            }
        }
    }

    /** Pull the latest catalog .db from the PC over WiFi and import it. */
    fun pullCatalogFromPc() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false, catalogCount = null) }
            try {
                val count = withContext(Dispatchers.IO) {
                    val pc = WifiDiscovery.discover(context, 2500).firstOrNull()
                        ?: throw IllegalStateException(
                            "No PC found on the network. Make sure the PC app's WiFi receiver is ON and the phone is on the same WiFi.")
                    val tmp = java.io.File.createTempFile("catalog_pull", ".db")
                    try {
                        val bytes = tmp.outputStream().use { os ->
                            WifiSender.pullCatalog(pc.ip, pc.port, os)
                        }
                        if (bytes <= 0L) throw IllegalStateException(
                            "The PC has no catalog ready. Open a master file on the PC first.")
                        tmp.inputStream().use { catalogHelper.importCatalog(it) }
                    } finally {
                        tmp.delete()
                    }
                }
                _uiState.update { it.copy(isLoading = false, success = true, catalogCount = count) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Catalog update over WiFi failed.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false, catalogCount = null) }
    }
}
