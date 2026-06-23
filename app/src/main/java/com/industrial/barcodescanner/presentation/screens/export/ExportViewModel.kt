package com.industrial.barcodescanner.presentation.screens.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.PrintJob
import com.industrial.barcodescanner.domain.model.PrintSheet
import com.industrial.barcodescanner.domain.model.ResolvedItem
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.CsvExporter
import com.industrial.barcodescanner.utils.LocalFileServer
import com.industrial.barcodescanner.utils.PrintHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class ExportSelectionType { FILTER, MANUAL }

/** State machine for the WiFi print flow. */
enum class WifiState {
    IDLE, DISCOVERING, PC_FOUND, SENDING,
    PREVIEW,          // PC resolved everything — show preview before printing
    NEEDS_DECISION,   // some items failed — show errors, wait for user
    PRINTING,         // waiting for PC to finish printing
    DONE, ERROR
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    private val historyManager: PrintHistoryManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class ExportUiState(
        val allItems: List<ScannedItem> = emptyList(),
        val selectiveExportEnabled: Boolean = false,
        val selectionType: ExportSelectionType = ExportSelectionType.FILTER,
        val selectedTagTypes: Set<String> = emptySet(),
        val selectedUnitTypes: Set<String> = emptySet(),
        val manualSelectedIds: Set<Long> = emptySet(),
        val isExporting: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val shareFileUri: Uri? = null,
        // WiFi state
        val wifiState: WifiState = WifiState.IDLE,
        val discoveredPcs: List<LocalFileServer.PcInfo> = emptyList(),
        val selectedPc: LocalFileServer.PcInfo? = null,
        val wifiStatusMessage: String = "",
        // Preview / decision
        val previewReadyItems: List<ResolvedItem> = emptyList(),
        val previewFailedItems: List<ResolvedItem> = emptyList(),
        val previewSheets: List<PrintSheet> = emptyList(),
        // Done
        val wifiPrintedCount: Int = 0
    ) {
        val totalRecords: Int get() = allItems.size

        val exportItems: List<ScannedItem>
            get() {
                val filtered = if (!selectiveExportEnabled) allItems
                else when (selectionType) {
                    ExportSelectionType.MANUAL -> allItems.filter { manualSelectedIds.contains(it.id) }
                    ExportSelectionType.FILTER -> allItems.filter {
                        (selectedTagTypes.isEmpty() || selectedTagTypes.contains(it.tagType)) &&
                            (selectedUnitTypes.isEmpty() || selectedUnitTypes.contains(it.unitType))
                    }
                }
                return filtered.sortedBy { it.createdAt }
            }
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    // Hold open TCP connection results across coroutines
    private var pendingPreview: LocalFileServer.PushResult.Preview? = null
    private var pendingDecision: LocalFileServer.PushResult.NeedsDecision? = null

    init { observeItems() }

    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { items -> _uiState.update { it.copy(allItems = items) } }
        }
    }

    // ── Selective export ─────────────────────────────────────────────────────

    fun setSelectiveExportEnabled(enabled: Boolean) {
        _uiState.update {
            if (enabled) it.copy(selectiveExportEnabled = true)
            else it.copy(selectiveExportEnabled = false, selectedTagTypes = emptySet(), selectedUnitTypes = emptySet(), manualSelectedIds = emptySet())
        }
    }

    fun setSelectionType(type: ExportSelectionType) = _uiState.update { it.copy(selectionType = type) }

    fun toggleTagTypeFilter(tagType: String) {
        _uiState.update {
            it.copy(selectedTagTypes = if (it.selectedTagTypes.contains(tagType)) it.selectedTagTypes - tagType else it.selectedTagTypes + tagType)
        }
    }

    fun toggleUnitTypeFilter(unitType: String) {
        _uiState.update {
            it.copy(selectedUnitTypes = if (it.selectedUnitTypes.contains(unitType)) it.selectedUnitTypes - unitType else it.selectedUnitTypes + unitType)
        }
    }

    fun toggleManualItemSelected(id: Long) {
        _uiState.update {
            it.copy(manualSelectedIds = if (it.manualSelectedIds.contains(id)) it.manualSelectedIds - id else it.manualSelectedIds + id)
        }
    }

    fun selectAllManualItems() = _uiState.update { it.copy(manualSelectedIds = it.allItems.map { item -> item.id }.toSet()) }
    fun clearManualSelection() = _uiState.update { it.copy(manualSelectedIds = emptySet()) }
    fun resetFilters() = _uiState.update { it.copy(selectedTagTypes = emptySet(), selectedUnitTypes = emptySet(), manualSelectedIds = emptySet()) }

    // ── Save / Share CSV ─────────────────────────────────────────────────────

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.exportItems
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { CsvExporter.writeCsv(it, items) }
                }
                _uiState.update { it.copy(isExporting = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun shareAsCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.exportItems
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "BarcodeToCsv_${System.currentTimeMillis()}.csv")
                    FileOutputStream(file).use { CsvExporter.writeCsv(it, items) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                _uiState.update { it.copy(isExporting = false, shareFileUri = uri) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun consumeShareFileUri() = _uiState.update { it.copy(shareFileUri = null) }

    // ── WiFi Print ───────────────────────────────────────────────────────────

    fun discoverPcs() {
        viewModelScope.launch {
            _uiState.update { it.copy(wifiState = WifiState.DISCOVERING, discoveredPcs = emptyList(), wifiStatusMessage = "Searching for PCs…") }
            val pcs = LocalFileServer.discoverPcs()
            if (pcs.isEmpty()) {
                _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "No PC found. Make sure Price Tag app is open with WiFi receiver enabled.") }
            } else {
                _uiState.update { it.copy(wifiState = WifiState.PC_FOUND, discoveredPcs = pcs, selectedPc = pcs.first(), wifiStatusMessage = "Found ${pcs.size} PC(s)") }
            }
        }
    }

    fun selectPc(pc: LocalFileServer.PcInfo) = _uiState.update { it.copy(selectedPc = pc) }

    /** Step 2: send CSV and get the preview/decision from PC. */
    fun sendCsvToPC() {
        val pc = _uiState.value.selectedPc ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(wifiState = WifiState.SENDING, wifiStatusMessage = "Analysing items on ${pc.name}…") }
            val csvBytes = LocalFileServer.buildCsvBytes(_uiState.value.exportItems)

            when (val result = LocalFileServer.pushCsvAndGetPreview(pc, csvBytes,
                onStatus = { msg -> _uiState.update { it.copy(wifiStatusMessage = msg) } }
            )) {
                is LocalFileServer.PushResult.Preview -> {
                    pendingPreview = result
                    _uiState.update {
                        it.copy(
                            wifiState = WifiState.PREVIEW,
                            previewReadyItems = result.readyItems,
                            previewFailedItems = emptyList(),
                            previewSheets = result.sheets,
                            wifiStatusMessage = "${result.readyItems.size} item(s) ready to print"
                        )
                    }
                }
                is LocalFileServer.PushResult.NeedsDecision -> {
                    pendingDecision = result
                    _uiState.update {
                        it.copy(
                            wifiState = WifiState.NEEDS_DECISION,
                            previewReadyItems = result.readyItems,
                            previewFailedItems = result.failedItems,
                            previewSheets = result.sheets,
                            wifiStatusMessage = "${result.readyItems.size} ready, ${result.failedItems.size} failed"
                        )
                    }
                }
                is LocalFileServer.PushResult.Busy -> {
                    _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "PC is busy. Try again.") }
                }
                is LocalFileServer.PushResult.Error -> {
                    _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "Error: ${result.message}") }
                }
            }
        }
    }

    /** User tapped Print on the preview screen (no failures). */
    fun confirmPrint() {
        val preview = pendingPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(wifiState = WifiState.PRINTING, wifiStatusMessage = "Printing…") }
            val final = LocalFileServer.confirmPrint(preview)
            if (final.error != null) {
                _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "Print error: ${final.error}") }
            } else {
                saveToHistory(final.sheets)
                _uiState.update { it.copy(wifiState = WifiState.DONE, wifiPrintedCount = final.printed, wifiStatusMessage = "✓ Printed ${final.printed} sheet(s)") }
            }
            pendingPreview = null
        }
    }

    fun cancelPreview() {
        pendingPreview?.let { LocalFileServer.cancelPreview(it) }
        pendingPreview = null
        resetWifi()
    }

    /** User decided after seeing failures: print=true → Print ready, false → cancel all. */
    fun submitDecision(print: Boolean) {
        val decision = pendingDecision ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(wifiState = WifiState.PRINTING, wifiStatusMessage = if (print) "Printing ready items…" else "Cancelling…") }
            val final = LocalFileServer.sendDecision(print, decision)
            if (final.error != null) {
                _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "Print error: ${final.error}") }
            } else if (final.cancelled) {
                resetWifi()
            } else {
                saveToHistory(final.sheets)
                _uiState.update { it.copy(wifiState = WifiState.DONE, wifiPrintedCount = final.printed, wifiStatusMessage = "✓ Printed ${final.printed} sheet(s)") }
            }
            pendingDecision = null
        }
    }

    fun cancelDecision() {
        pendingDecision?.let { LocalFileServer.cancelDecision(it) }
        pendingDecision = null
        resetWifi()
    }

    private fun saveToHistory(sheets: List<PrintSheet>) {
        if (sheets.isEmpty()) return
        viewModelScope.launch {
            historyManager.saveJob(PrintJob(sheets = sheets))
        }
    }

    fun resetWifi() {
        _uiState.update {
            it.copy(
                wifiState = WifiState.IDLE, discoveredPcs = emptyList(),
                selectedPc = null, wifiStatusMessage = "",
                previewReadyItems = emptyList(), previewFailedItems = emptyList(),
                previewSheets = emptyList(), wifiPrintedCount = 0
            )
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun resetSuccess() = _uiState.update { it.copy(success = false) }
}
