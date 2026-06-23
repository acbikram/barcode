package com.industrial.barcodescanner.presentation.screens.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.utils.CsvExporter
import com.industrial.barcodescanner.utils.LocalFileServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class ExportSelectionType { FILTER, MANUAL }

enum class WifiState {
    IDLE,           // nothing happening
    DISCOVERING,    // scanning LAN for PCs
    PC_FOUND,       // at least one PC found, waiting for user to pick
    SENDING,        // pushing CSV to the selected PC
    WAITING_DECISION, // PC sent back partial results, waiting for user to decide
    DONE,           // PC printed successfully
    ERROR           // something went wrong
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class ExportUiState(
        val allItems: List<ScannedItem> = emptyList(),
        // Selective export
        val selectiveExportEnabled: Boolean = false,
        val selectionType: ExportSelectionType = ExportSelectionType.FILTER,
        val selectedTagTypes: Set<String> = emptySet(),
        val selectedUnitTypes: Set<String> = emptySet(),
        val manualSelectedIds: Set<Long> = emptySet(),
        // Regular export
        val isExporting: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val shareFileUri: Uri? = null,
        // WiFi
        val wifiState: WifiState = WifiState.IDLE,
        val discoveredPcs: List<LocalFileServer.PcInfo> = emptyList(),
        val selectedPc: LocalFileServer.PcInfo? = null,
        val wifiStatusMessage: String = "",
        val wifiPrintedCount: Int = 0,
        val wifiFailedItems: List<LocalFileServer.FailedItem> = emptyList(),
        val wifiRetryCsv: String = ""
    ) {
        val totalRecords: Int get() = allItems.size

        /** Items to export — always in scan order (first scanned = first row). */
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
                return filtered.sortedBy { it.createdAt }
            }
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

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
            else it.copy(
                selectiveExportEnabled = false,
                selectedTagTypes = emptySet(),
                selectedUnitTypes = emptySet(),
                manualSelectedIds = emptySet()
            )
        }
    }

    fun setSelectionType(type: ExportSelectionType) = _uiState.update { it.copy(selectionType = type) }

    fun toggleTagTypeFilter(tagType: String) {
        _uiState.update {
            val updated = if (it.selectedTagTypes.contains(tagType)) it.selectedTagTypes - tagType else it.selectedTagTypes + tagType
            it.copy(selectedTagTypes = updated)
        }
    }

    fun toggleUnitTypeFilter(unitType: String) {
        _uiState.update {
            val updated = if (it.selectedUnitTypes.contains(unitType)) it.selectedUnitTypes - unitType else it.selectedUnitTypes + unitType
            it.copy(selectedUnitTypes = updated)
        }
    }

    fun toggleManualItemSelected(id: Long) {
        _uiState.update {
            val updated = if (it.manualSelectedIds.contains(id)) it.manualSelectedIds - id else it.manualSelectedIds + id
            it.copy(manualSelectedIds = updated)
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
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, "BarcodeToCsv_${System.currentTimeMillis()}.csv")
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

    /** Step 1: Discover PCs on the LAN running Price_Tag_Final.py */
    fun discoverPcs() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wifiState = WifiState.DISCOVERING,
                    discoveredPcs = emptyList(),
                    wifiStatusMessage = "Searching for PCs…"
                )
            }
            val pcs = LocalFileServer.discoverPcs(timeoutMs = 2500)
            if (pcs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        wifiState = WifiState.ERROR,
                        wifiStatusMessage = "No PC found. Make sure Price Tag app is open and WiFi receiver is enabled."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        wifiState = WifiState.PC_FOUND,
                        discoveredPcs = pcs,
                        selectedPc = pcs.first(),
                        wifiStatusMessage = "Found ${pcs.size} PC(s)"
                    )
                }
            }
        }
    }

    fun selectPc(pc: LocalFileServer.PcInfo) = _uiState.update { it.copy(selectedPc = pc) }

    /** Step 2: Push CSV to the selected PC */
    fun sendCsvToPC() {
        val pc = _uiState.value.selectedPc ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(wifiState = WifiState.SENDING, wifiStatusMessage = "Sending CSV to ${pc.name}…") }
            val csvBytes = LocalFileServer.buildCsvBytes(_uiState.value.exportItems)

            val result = LocalFileServer.pushCsv(
                pc = pc,
                csvBytes = csvBytes,
                onStatus = { msg -> _uiState.update { it.copy(wifiStatusMessage = msg) } },
                onDecisionNeeded = { ready, failed, retry ->
                    _uiState.update {
                        it.copy(
                            wifiState = WifiState.WAITING_DECISION,
                            wifiFailedItems = failed,
                            wifiRetryCsv = retry,
                            wifiStatusMessage = "$ready ready, ${failed.size} failed — Print ready items?"
                        )
                    }
                    // Wait for user decision (suspends until they tap Print or Cancel)
                    var decision: Boolean? = null
                    while (decision == null) {
                        kotlinx.coroutines.delay(200)
                        decision = _pendingDecision
                    }
                    _pendingDecision = null
                    decision
                }
            )

            when (result) {
                is LocalFileServer.PushResult.Done -> _uiState.update {
                    it.copy(
                        wifiState = WifiState.DONE,
                        wifiPrintedCount = result.printed,
                        wifiStatusMessage = "✓ Printed ${result.printed} item(s)"
                    )
                }
                is LocalFileServer.PushResult.PartialDone -> _uiState.update {
                    it.copy(
                        wifiState = WifiState.DONE,
                        wifiPrintedCount = result.printed,
                        wifiFailedItems = result.failed,
                        wifiRetryCsv = result.retryCsv,
                        wifiStatusMessage = "Printed ${result.printed}, ${result.failed.size} failed"
                    )
                }
                is LocalFileServer.PushResult.Busy -> _uiState.update {
                    it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "PC is busy with another job. Try again.")
                }
                is LocalFileServer.PushResult.Error -> _uiState.update {
                    it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "Error: ${result.message}")
                }
            }
        }
    }

    // Used by the coroutine in sendCsvToPC to pass the user's Print/Cancel decision
    @Volatile private var _pendingDecision: Boolean? = null

    fun submitWifiDecision(print: Boolean) { _pendingDecision = print }

    fun resetWifi() {
        _pendingDecision = null
        _uiState.update {
            it.copy(
                wifiState = WifiState.IDLE,
                discoveredPcs = emptyList(),
                selectedPc = null,
                wifiStatusMessage = "",
                wifiPrintedCount = 0,
                wifiFailedItems = emptyList(),
                wifiRetryCsv = ""
            )
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun resetSuccess() = _uiState.update { it.copy(success = false) }
}
