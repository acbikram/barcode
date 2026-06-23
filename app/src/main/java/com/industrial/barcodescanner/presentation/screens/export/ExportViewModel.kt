package com.industrial.barcodescanner.presentation.screens.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** How the export selection is being narrowed down. */
enum class ExportSelectionType { FILTER, MANUAL }

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ScannedItemRepository
) : ViewModel() {

    data class ExportUiState(
        val allItems: List<ScannedItem> = emptyList(),
        /** Master toggle — when off, everything is exported. */
        val selectiveExportEnabled: Boolean = false,
        val selectionType: ExportSelectionType = ExportSelectionType.FILTER,
        val selectedTagTypes: Set<String> = emptySet(),
        val selectedUnitTypes: Set<String> = emptySet(),
        val manualSelectedIds: Set<Long> = emptySet(),
        val isExporting: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        /** Set once a CSV has been written to a shareable cache file; consumed by the UI to launch a share sheet. */
        val shareFileUri: Uri? = null
    ) {
        val totalRecords: Int get() = allItems.size

        /** The items that would actually be written to the CSV, in scan order (oldest first). */
        val exportItems: List<ScannedItem>
            get() {
                val filtered = if (!selectiveExportEnabled) {
                    allItems
                } else when (selectionType) {
                    ExportSelectionType.MANUAL -> allItems.filter { manualSelectedIds.contains(it.id) }
                    ExportSelectionType.FILTER -> allItems.filter {
                        (selectedTagTypes.isEmpty() || selectedTagTypes.contains(it.tagType)) &&
                            (selectedUnitTypes.isEmpty() || selectedUnitTypes.contains(it.unitType))
                    }
                }
                // Always export in chronological order — first scanned → first row in CSV
                return filtered.sortedBy { it.createdAt }
            }
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /**
     * Continuously collects the items Flow from Room so the displayed
     * record count always reflects the latest scans without needing to
     * leave and re-enter this screen.
     */
    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { items ->
                    _uiState.update { it.copy(allItems = items) }
                }
        }
    }

    // ── Selective export controls ────────────────────────────────────────────

    fun setSelectiveExportEnabled(enabled: Boolean) {
        _uiState.update {
            if (enabled) {
                it.copy(selectiveExportEnabled = true)
            } else {
                it.copy(
                    selectiveExportEnabled = false,
                    selectedTagTypes = emptySet(),
                    selectedUnitTypes = emptySet(),
                    manualSelectedIds = emptySet()
                )
            }
        }
    }

    fun setSelectionType(type: ExportSelectionType) {
        _uiState.update { it.copy(selectionType = type) }
    }

    fun toggleTagTypeFilter(tagType: String) {
        _uiState.update {
            val updated = if (it.selectedTagTypes.contains(tagType)) {
                it.selectedTagTypes - tagType
            } else {
                it.selectedTagTypes + tagType
            }
            it.copy(selectedTagTypes = updated)
        }
    }

    fun toggleUnitTypeFilter(unitType: String) {
        _uiState.update {
            val updated = if (it.selectedUnitTypes.contains(unitType)) {
                it.selectedUnitTypes - unitType
            } else {
                it.selectedUnitTypes + unitType
            }
            it.copy(selectedUnitTypes = updated)
        }
    }

    fun toggleManualItemSelected(id: Long) {
        _uiState.update {
            val updated = if (it.manualSelectedIds.contains(id)) {
                it.manualSelectedIds - id
            } else {
                it.manualSelectedIds + id
            }
            it.copy(manualSelectedIds = updated)
        }
    }

    fun selectAllManualItems() {
        _uiState.update { it.copy(manualSelectedIds = it.allItems.map { item -> item.id }.toSet()) }
    }

    fun clearManualSelection() {
        _uiState.update { it.copy(manualSelectedIds = emptySet()) }
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                selectedTagTypes = emptySet(),
                selectedUnitTypes = emptySet(),
                manualSelectedIds = emptySet()
            )
        }
    }

    // ── Export actions ────────────────────────────────────────────────────────

    /** "Save CSV" — write the CSV directly to a user-chosen location via SAF. */
    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.exportItems
                val contentResolver: ContentResolver = context.contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    CsvExporter.writeCsv(outputStream, items)
                }
                _uiState.update { it.copy(isExporting = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    /**
     * "Share CSV" — write the CSV to a file under the app's cache directory
     * (covered by the existing FileProvider `cache-path` entry), then expose
     * a `content://` URI via [shareFileUri] for the UI to hand off to
     * [android.content.Intent.ACTION_SEND].
     */
    fun shareAsCsv(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.exportItems
                val uri = withContext(Dispatchers.IO) {
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, "BarcodeToCsv_${System.currentTimeMillis()}.csv")
                    FileOutputStream(file).use { outputStream ->
                        CsvExporter.writeCsv(outputStream, items)
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
                _uiState.update { it.copy(isExporting = false, shareFileUri = uri) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    /** Called by the UI once it has launched the share sheet for [shareFileUri]. */
    fun consumeShareFileUri() {
        _uiState.update { it.copy(shareFileUri = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false) }
    }
}
