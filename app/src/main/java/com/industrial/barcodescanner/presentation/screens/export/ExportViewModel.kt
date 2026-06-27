package com.industrial.barcodescanner.presentation.screens.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import com.industrial.barcodescanner.utils.CsvExporter
import com.industrial.barcodescanner.utils.PreferencesManager
import com.industrial.barcodescanner.utils.WifiDiscovery
import com.industrial.barcodescanner.utils.WifiPc
import com.industrial.barcodescanner.utils.WifiReprintBus
import com.industrial.barcodescanner.utils.WifiSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** How the export selection is being narrowed down. */
enum class ExportSelectionType { FILTER, MANUAL }

/** One item the PC could not print, shown in the on-phone decision dialog. */
data class WifiFailedItem(val row: Int, val pos: String, val reason: String)

/**
 * A decision the PC is waiting on, surfaced to the phone UI.
 *  - "print_or_cancel": some items failed but [readyCount] are ready → Print ready / Cancel
 *  - "retry_left":      the ready items were printed → Retry the [failed] ones? Yes / No
 *  - "retry_or_cancel": nothing was printable → Retry / Cancel
 */
data class WifiDecisionRequest(
    val kind: String,
    val readyCount: Int = 0,
    val printedCount: Int = 0,
    val failedSheets: Int = 0,
    val failed: List<WifiFailedItem> = emptyList()
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    private val preferencesManager: PreferencesManager,
    private val database: BarcodeDatabase,
    private val reprintBus: WifiReprintBus
) : ViewModel() {

    private val historyDao = database.wifiPrintHistoryDao()

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
        val shareFileUri: Uri? = null,
        // ── Share WiFi (push CSV straight to the PC over LAN) ──────────────
        val wifiHost: String = "",
        val wifiPort: String = "8765",
        val wifiSending: Boolean = false,
        /** "connecting" | "checking" | "printing" — drives the waiting spinner text. */
        val wifiStage: String? = null,
        /** Non-null when the PC is waiting for the user's choice on this phone. */
        val wifiDecision: WifiDecisionRequest? = null,
        // ── Auto-discovery / connection test ──────────────────────────────
        val discovering: Boolean = false,
        val discovered: List<WifiPc> = emptyList(),
        val testing: Boolean = false,
        /** Result line for a snackbar (success or failure); consumed by the UI. */
        val wifiInfo: String? = null,
        /** True once at least one batch has been sent (enables "Re-send last batch"). */
        val hasLastBatch: Boolean = false
    ) {
        val totalRecords: Int get() = allItems.size

        /** The items that would actually be written to the CSV. */
        val exportItems: List<ScannedItem>
            get() = if (!selectiveExportEnabled) {
                allItems
            } else when (selectionType) {
                ExportSelectionType.MANUAL -> allItems.filter { manualSelectedIds.contains(it.id) }
                ExportSelectionType.FILTER -> allItems.filter {
                    (selectedTagTypes.isEmpty() || selectedTagTypes.contains(it.tagType)) &&
                        (selectedUnitTypes.isEmpty() || selectedUnitTypes.contains(it.unitType))
                }
            }
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        observeItems()
        // Pre-fill the Share-WiFi dialog with the last PC address used.
        _uiState.update {
            it.copy(
                wifiHost = preferencesManager.getWifiHost(),
                wifiPort = preferencesManager.getWifiPort(),
                hasLastBatch = preferencesManager.getLastBatchCsv().isNotBlank()
            )
        }
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

    // ── Share WiFi ────────────────────────────────────────────────────────────

    fun setWifiHost(host: String) {
        _uiState.update { it.copy(wifiHost = host) }
    }

    fun setWifiPort(port: String) {
        _uiState.update { it.copy(wifiPort = port.filter { ch -> ch.isDigit() }.take(5)) }
    }

    private var pendingDecision: CompletableDeferred<String>? = null

    private sealed class WifiOutcome {
        data class RetryWith(val payload: ByteArray) : WifiOutcome()
        data class Finished(val message: String) : WifiOutcome()
    }

    /**
     * "Share WiFi" — send the selected items to the PC and run the two-way
     * conversation. The PC checks descriptions/prices; if some items can't be
     * printed, the user decides here (Print ready / Cancel), then is asked to
     * retry the leftovers. Retrying simply re-sends the failed rows.
     */
    fun shareViaWifi() {
        if (validateHostPort() == null) return
        val items = _uiState.value.exportItems
        if (items.isEmpty()) {
            _uiState.update { it.copy(wifiInfo = "EMPTY") }
            return
        }
        val bytes = WifiSender.csvBytes(items)
        // Remember this batch so it can be re-sent later (strip the leading BOM;
        // the PC accepts CSV with or without it).
        val csvText = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        rememberLastBatch(csvText)
        launchShare { bytes }
    }

    /** Re-send the most recent batch that was sent over Wi-Fi. */
    fun resendLastBatch() {
        if (validateHostPort() == null) return
        val csv = preferencesManager.getLastBatchCsv()
        if (csv.isBlank()) {
            _uiState.update { it.copy(wifiInfo = "EMPTY") }
            return
        }
        launchShare { WifiSender.csvBytesFromText(csv) }
    }

    private fun rememberLastBatch(csvText: String) {
        viewModelScope.launch { preferencesManager.setLastBatchCsv(csvText) }
        _uiState.update { it.copy(hasLastBatch = true) }
    }

    /** Re-send a single CSV (used by "Reprint" from the Wi-Fi history). */
    fun shareCsvViaWifi(csvText: String) {
        if (validateHostPort() == null) return
        launchShare { WifiSender.csvBytesFromText(csvText) }
    }

    /** If the user tapped Reprint in the history, send that item now. */
    fun maybeStartPendingReprint() {
        val csv = reprintBus.consume() ?: return
        shareCsvViaWifi(csv)
    }

    private fun validateHostPort(): Int? {
        val host = _uiState.value.wifiHost.trim()
        val portInt = _uiState.value.wifiPort.trim().toIntOrNull()
        if (host.isBlank() || portInt == null || portInt !in 1..65535) {
            _uiState.update { it.copy(wifiInfo = "INVALID_INPUT") }
            return null
        }
        return portInt
    }

    private fun launchShare(payloadProvider: suspend () -> ByteArray) {
        val host = _uiState.value.wifiHost.trim()
        val portInt = _uiState.value.wifiPort.trim().toIntOrNull() ?: return
        viewModelScope.launch {
            preferencesManager.setWifiHost(host)
            preferencesManager.setWifiPort(portInt.toString())
            _uiState.update { it.copy(wifiSending = true, wifiInfo = null, wifiStage = "connecting") }
            try {
                var payload = withContext(Dispatchers.IO) { payloadProvider() }
                var looping = true
                while (looping) {
                    when (val outcome = runWifiSession(host, portInt, payload)) {
                        is WifiOutcome.RetryWith -> {
                            payload = outcome.payload
                            _uiState.update { it.copy(wifiStage = "connecting") }
                        }
                        is WifiOutcome.Finished -> {
                            _uiState.update { it.copy(wifiInfo = outcome.message) }
                            looping = false
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(wifiInfo = "FAILED:${e.message ?: e.toString()}") }
            } finally {
                _uiState.update { it.copy(wifiSending = false, wifiStage = null, wifiDecision = null) }
            }
        }
    }

    private suspend fun runWifiSession(host: String, port: Int, payload: ByteArray): WifiOutcome =
        withContext(Dispatchers.IO) {
            WifiSender.Session(host, port).use { s ->
                s.sendCsv(payload)
                _uiState.update { it.copy(wifiStage = "checking") }
                while (true) {
                    val msg = s.readMessage() ?: return@use WifiOutcome.Finished("DONE_CLOSED")
                    when (msg.optString("type")) {
                        "busy"  -> return@use WifiOutcome.Finished("BUSY")
                        "error" -> return@use WifiOutcome.Finished("FAILED:${msg.optString("message")}")
                        "result" -> {
                            val ready = msg.optInt("ready", 0)
                            val failed = parseFailed(msg)
                            val retryCsv = msg.optString("retry_csv", "")
                            if (failed.isEmpty()) {
                                // Everything ready → PC prints; wait for "done".
                                _uiState.update { it.copy(wifiStage = "printing", wifiDecision = null) }
                            } else if (ready > 0) {
                                val choice = askDecision(
                                    WifiDecisionRequest("print_or_cancel", readyCount = ready, failed = failed)
                                )
                                if (choice == "print") {
                                    s.sendDecision("print")
                                    _uiState.update { it.copy(wifiStage = "printing") }
                                } else {
                                    s.sendDecision("cancel")
                                }
                            } else {
                                // Nothing printable → PC closes; decide retry locally.
                                // You asked to keep everything sent, so record the
                                // (all-failed) items before offering retry.
                                recordWifiHistory(msg)
                                val choice = askDecision(
                                    WifiDecisionRequest("retry_or_cancel", failed = failed)
                                )
                                return@use if (choice == "retry")
                                    WifiOutcome.RetryWith(WifiSender.csvBytesFromText(retryCsv))
                                else WifiOutcome.Finished("CANCELLED")
                            }
                        }
                        "done" -> return@use WifiOutcome.Finished("PRINTED:${msg.optInt("printed", 0)}")
                        "printed" -> {
                            val printed = msg.optInt("printed", 0)
                            val failedSheets = msg.optInt("failed_sheets", 0)
                            val failed = parseFailed(msg)
                            val retryCsv = msg.optString("retry_csv", "")
                            if (failedSheets > 0) {
                                // #6 — some sheets didn't print (printer jam etc.).
                                val choice = askDecision(
                                    WifiDecisionRequest(
                                        "reprint_sheets",
                                        printedCount = printed,
                                        failedSheets = failedSheets,
                                        failed = failed
                                    )
                                )
                                if (choice == "reprint") {
                                    s.sendDecision("reprint")   // loop → read next "printed"
                                } else {
                                    s.sendDecision("skip")
                                    recordWifiHistory(msg)
                                    return@use itemRetryOutcome(printed, failed, retryCsv)
                                }
                            } else {
                                recordWifiHistory(msg)
                                return@use itemRetryOutcome(printed, failed, retryCsv)
                            }
                        }
                        "cancelled" -> return@use WifiOutcome.Finished("CANCELLED")
                        "progress" -> {
                            // Heartbeat from the PC during a long check/print — keeps the
                            // connection alive and updates the spinner text.
                            val st = msg.optString("stage", "")
                            if (st.isNotBlank()) _uiState.update { it.copy(wifiStage = st) }
                        }
                        else -> { /* unknown message — keep reading */ }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                WifiOutcome.Finished("DONE_CLOSED")
            }
        }

    private fun parseFailed(msg: JSONObject): List<WifiFailedItem> {
        val arr = msg.optJSONArray("failed") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WifiFailedItem(o.optInt("row", 0), o.optString("pos", ""), o.optString("reason", ""))
        }
    }

    /**
     * Record one Share-WiFi job into the history: one row per printed physical
     * page (kind="sheet") plus one row per failed item (kind="failed"). All
     * share a jobId so the screen can group them. Auto-trims afterwards.
     */
    private suspend fun recordWifiHistory(msg: JSONObject) {
        val jobId = System.currentTimeMillis()
        val out = mutableListOf<WifiPrintHistoryEntity>()

        // Printed physical pages.
        msg.optJSONArray("sheets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val first = if (itemsArr.length() > 0) itemsArr.getJSONObject(0) else JSONObject()
                val firstEng = first.optString("eng", "")
                val firstPos = first.optString("pos", "")
                out.add(
                    WifiPrintHistoryEntity(
                        jobId = jobId, timestamp = jobId, kind = "sheet",
                        tagType = o.optString("tag", ""), unitType = o.optString("unit", ""),
                        copies = o.optInt("copies", 1),
                        nTags = o.optInt("n_tags", itemsArr.length()),
                        summary = firstEng.ifBlank { firstPos },
                        posCode = firstPos,
                        price = first.optString("price", ""),
                        reason = "",
                        itemsJson = itemsArr.toString()
                    )
                )
            }
        }

        // Failed items (from the full per-item list with status).
        msg.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("status", "") != "failed") continue
                val pos = o.optString("pos", "")
                val eng = o.optString("eng", "")
                val one = JSONArray().put(
                    JSONObject()
                        .put("pos", pos)
                        .put("eng", eng)
                        .put("unit", o.optString("unit", ""))
                        .put("copies", o.optInt("copies", 1))
                        .put("price", "")
                )
                out.add(
                    WifiPrintHistoryEntity(
                        jobId = jobId, timestamp = jobId, kind = "failed",
                        tagType = o.optString("tag", ""), unitType = o.optString("unit", ""),
                        copies = o.optInt("copies", 1), nTags = 1,
                        summary = eng.ifBlank { pos },
                        posCode = pos,
                        price = "",
                        reason = o.optString("reason", ""),
                        itemsJson = one.toString()
                    )
                )
            }
        }

        if (out.isEmpty()) return
        try {
            historyDao.insertAll(out)
            // Auto-trim: keep ~60 days and cap to 5000 rows.
            val cutoff = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
            historyDao.trimOlderThan(cutoff)
            historyDao.trimToCount(5000)
        } catch (_: Exception) {
        }
    }

    private suspend fun itemRetryOutcome(
        printed: Int,
        failed: List<WifiFailedItem>,
        retryCsv: String
    ): WifiOutcome {
        if (failed.isEmpty()) return WifiOutcome.Finished("PRINTED:$printed")
        val choice = askDecision(
            WifiDecisionRequest("retry_left", printedCount = printed, failed = failed)
        )
        return if (choice == "retry")
            WifiOutcome.RetryWith(WifiSender.csvBytesFromText(retryCsv))
        else WifiOutcome.Finished("PRINTED_DONE:$printed")
    }

    private suspend fun askDecision(req: WifiDecisionRequest): String {
        val deferred = CompletableDeferred<String>()
        pendingDecision = deferred
        _uiState.update { it.copy(wifiDecision = req, wifiStage = null) }
        val choice = deferred.await()
        _uiState.update { it.copy(wifiDecision = null) }
        return choice
    }

    /** Called by the UI when the user taps a button in the WiFi decision dialog. */
    fun submitWifiDecision(choice: String) {
        pendingDecision?.complete(choice)
        pendingDecision = null
    }

    fun consumeWifiInfo() {
        _uiState.update { it.copy(wifiInfo = null) }
    }

    /** #1 — find price-tag PCs on the LAN so the user doesn't type an IP. */
    fun discoverPcs() {
        viewModelScope.launch {
            _uiState.update { it.copy(discovering = true, discovered = emptyList()) }
            val pcs = withContext(Dispatchers.IO) { WifiDiscovery.discover(1500) }
            _uiState.update {
                it.copy(
                    discovering = false,
                    discovered = pcs,
                    wifiInfo = if (pcs.isEmpty()) "NO_PCS" else null
                )
            }
        }
    }

    fun selectPc(pc: WifiPc) {
        _uiState.update { it.copy(wifiHost = pc.ip, wifiPort = pc.port.toString(), discovered = emptyList()) }
    }

    /** #3 — test the connection to the entered address. */
    fun testWifi() {
        val host = _uiState.value.wifiHost.trim()
        val portInt = _uiState.value.wifiPort.trim().toIntOrNull()
        if (host.isBlank() || portInt == null || portInt !in 1..65535) {
            _uiState.update { it.copy(wifiInfo = "INVALID_INPUT") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true) }
            val info = try {
                val name = withContext(Dispatchers.IO) { WifiSender.ping(host, portInt) }
                "TEST_OK:$name"
            } catch (e: Exception) {
                "TEST_FAIL:${e.message ?: "unreachable"}"
            }
            _uiState.update { it.copy(testing = false, wifiInfo = info) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false) }
    }
}
