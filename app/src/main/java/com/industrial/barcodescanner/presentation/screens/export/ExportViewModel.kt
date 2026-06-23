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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class ExportSelectionType { FILTER, MANUAL }

enum class WifiState {
    IDLE, DISCOVERING, PC_FOUND,
    SENDING,          // CSV sent, waiting for PC analysis
    LIVE_ITEMS,       // animating resolved items list before showing dialog
    PREVIEW,          // all resolved — show preview, wait for Print tap
    NEEDS_DECISION,   // some failed — show error list, wait for decision
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
        // Live item-by-item progress (animates during SENDING/LIVE_ITEMS)
        val liveItems: List<ResolvedItem> = emptyList(),
        val liveItemsTotal: Int = 0,
        // Preview / decision
        val previewReadyItems: List<ResolvedItem> = emptyList(),
        val previewFailedItems: List<ResolvedItem> = emptyList(),
        val previewSheets: List<PrintSheet> = emptyList(),
        // Print progress (pages)
        val printingSheetsDone: Int = 0,
        val printingTotalSheets: Int = 0,
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
        _uiState.update { it.copy(selectedTagTypes = if (it.selectedTagTypes.contains(tagType)) it.selectedTagTypes - tagType else it.selectedTagTypes + tagType) }
    }

    fun toggleUnitTypeFilter(unitType: String) {
        _uiState.update { it.copy(selectedUnitTypes = if (it.selectedUnitTypes.contains(unitType)) it.selectedUnitTypes - unitType else it.selectedUnitTypes + unitType) }
    }

    fun toggleManualItemSelected(id: Long) {
        _uiState.update { it.copy(manualSelectedIds = if (it.manualSelectedIds.contains(id)) it.manualSelectedIds - id else it.manualSelectedIds + id) }
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

    fun sendCsvToPC() {
        val pc = _uiState.value.selectedPc ?: return
        val exportItems = _uiState.value.exportItems
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wifiState = WifiState.SENDING,
                    wifiStatusMessage = "Sending ${exportItems.size} item(s) to ${pc.name}…",
                    liveItems = emptyList(),
                    liveItemsTotal = exportItems.size
                )
            }
            val csvBytes = LocalFileServer.buildCsvBytes(exportItems)

            when (val result = LocalFileServer.pushCsvAndGetPreview(pc, csvBytes,
                onStatus = { msg -> _uiState.update { it.copy(wifiStatusMessage = msg) } }
            )) {
                is LocalFileServer.PushResult.Preview -> {
                    pendingPreview = result
                    animateLiveItems(result.readyItems, emptyList()) {
                        _uiState.update {
                            it.copy(
                                wifiState = WifiState.PREVIEW,
                                previewReadyItems = result.readyItems,
                                previewFailedItems = emptyList(),
                                previewSheets = result.sheets,
                                printingTotalSheets = result.sheets.size,
                                wifiStatusMessage = "${result.readyItems.size} item(s) ready"
                            )
                        }
                    }
                }
                is LocalFileServer.PushResult.NeedsDecision -> {
                    pendingDecision = result
                    animateLiveItems(result.readyItems, result.failedItems) {
                        _uiState.update {
                            it.copy(
                                wifiState = WifiState.NEEDS_DECISION,
                                previewReadyItems = result.readyItems,
                                previewFailedItems = result.failedItems,
                                previewSheets = result.sheets,
                                printingTotalSheets = result.sheets.size,
                                wifiStatusMessage = "${result.readyItems.size} ready, ${result.failedItems.size} failed"
                            )
                        }
                    }
                }
                is LocalFileServer.PushResult.Busy ->
                    _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = "PC is busy. Try again shortly.") }
                is LocalFileServer.PushResult.Error ->
                    _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = result.message) }
            }
        }
    }

    /**
     * Animates resolved items appearing in the live list one by one.
     * Speed adapts to the batch size so it never feels too slow for large jobs.
     */
    private suspend fun animateLiveItems(
        readyItems: List<ResolvedItem>,
        failedItems: List<ResolvedItem>,
        onComplete: () -> Unit
    ) {
        val allItems = readyItems + failedItems
        _uiState.update { it.copy(wifiState = WifiState.LIVE_ITEMS, liveItems = emptyList(), liveItemsTotal = allItems.size) }
        val delayMs = when {
            allItems.size <= 10  -> 90L
            allItems.size <= 50  -> 45L
            allItems.size <= 200 -> 15L
            else                 ->  5L
        }
        allItems.forEachIndexed { idx, item ->
            _uiState.update { state ->
                state.copy(
                    liveItems = state.liveItems + item,
                    wifiStatusMessage = "${idx + 1} / ${allItems.size}"
                )
            }
            delay(delayMs)
        }
        delay(300)
        onComplete()
    }

    /** User tapped Print on the preview screen (no failures). */
    fun confirmPrint() {
        val preview = pendingPreview ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wifiState = WifiState.PRINTING,
                    wifiStatusMessage = "Printing ${preview.sheets.size} page(s)…",
                    printingSheetsDone = 0,
                    printingTotalSheets = preview.sheets.size
                )
            }
            // Animate the page counter while we wait for the PC
            val counterJob = launch { simulatePrintProgress(preview.sheets.size) }
            val final = LocalFileServer.confirmPrint(preview)
            counterJob.cancel()
            handleFinalResult(final)
            pendingPreview = null
        }
    }

    fun cancelPreview() {
        pendingPreview?.let { LocalFileServer.cancelPreview(it) }
        pendingPreview = null
        resetWifi()
    }

    fun submitDecision(print: Boolean) {
        val decision = pendingDecision ?: return
        viewModelScope.launch {
            if (!print) {
                LocalFileServer.cancelDecision(decision)
                pendingDecision = null
                resetWifi()
                return@launch
            }
            _uiState.update {
                it.copy(
                    wifiState = WifiState.PRINTING,
                    wifiStatusMessage = "Printing ${decision.sheets.size} page(s)…",
                    printingSheetsDone = 0,
                    printingTotalSheets = decision.sheets.size
                )
            }
            val counterJob = launch { simulatePrintProgress(decision.sheets.size) }
            val final = LocalFileServer.sendDecision(print, decision)
            counterJob.cancel()
            handleFinalResult(final)
            pendingDecision = null
        }
    }

    fun cancelDecision() {
        pendingDecision?.let { LocalFileServer.cancelDecision(it) }
        pendingDecision = null
        resetWifi()
    }

    /**
     * While we wait for the PC to finish printing, animate the page counter
     * incrementing. The speed is estimated from the batch size; the real
     * count from the PC overrides it once printing finishes.
     */
    private suspend fun simulatePrintProgress(totalSheets: Int) {
        val estimatedSecPerPage = 8L   // rough estimate: ~8 seconds per page
        val delayPerStep = (estimatedSecPerPage * 1000L / maxOf(totalSheets, 1)).coerceIn(500, 3000)
        for (i in 1..totalSheets) {
            delay(delayPerStep)
            _uiState.update {
                it.copy(
                    printingSheetsDone = i,
                    wifiStatusMessage = "Printing page $i / $totalSheets…"
                )
            }
        }
    }

    private fun handleFinalResult(final: LocalFileServer.FinalResult) {
        if (final.error != null) {
            _uiState.update { it.copy(wifiState = WifiState.ERROR, wifiStatusMessage = final.error) }
        } else if (final.cancelled) {
            resetWifi()
        } else {
            saveToHistory(final.sheets)
            _uiState.update {
                it.copy(
                    wifiState = WifiState.DONE,
                    wifiPrintedCount = final.printed,
                    printingSheetsDone = final.printed,
                    wifiStatusMessage = "✓ Printed ${final.printed} page(s)"
                )
            }
        }
    }

    private fun saveToHistory(sheets: List<PrintSheet>) {
        if (sheets.isEmpty()) return
        viewModelScope.launch { historyManager.saveJob(PrintJob(sheets = sheets)) }
    }

    fun resetWifi() {
        _uiState.update {
            it.copy(
                wifiState = WifiState.IDLE, discoveredPcs = emptyList(),
                selectedPc = null, wifiStatusMessage = "",
                liveItems = emptyList(), liveItemsTotal = 0,
                previewReadyItems = emptyList(), previewFailedItems = emptyList(),
                previewSheets = emptyList(), printingSheetsDone = 0,
                printingTotalSheets = 0, wifiPrintedCount = 0
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun resetSuccess() = _uiState.update { it.copy(success = false) }
}
