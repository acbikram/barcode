package com.industrial.barcodescanner.presentation.screens.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.domain.repository.WifiPrintHistoryRepository
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

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    private val historyRepository: WifiPrintHistoryRepository,
    private val preferencesManager: PreferencesManager,
    private val reprintBus: WifiReprintBus
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
        // Share WiFi
        val wifiHost: String = "",
        val wifiPort: String = "8765",
        val wifiSending: Boolean = false,
        val wifiStage: String? = null,
        val wifiDecision: WifiDecisionRequest? = null,
        val discovering: Boolean = false,
        val discovered: List<WifiPc> = emptyList(),
        val testing: Boolean = false,
        val wifiInfo: String? = null,
        val hasLastBatch: Boolean = false
    ) {
        val totalRecords: Int get() = allItems.size

        val exportItems: List<ScannedItem>
            get() = (if (!selectiveExportEnabled) allItems
            else when (selectionType) {
                ExportSelectionType.MANUAL -> allItems.filter { manualSelectedIds.contains(it.id) }
                ExportSelectionType.FILTER -> allItems.filter {
                    (selectedTagTypes.isEmpty() || selectedTagTypes.contains(it.tagType)) &&
                        (selectedUnitTypes.isEmpty() || selectedUnitTypes.contains(it.unitType))
                }
            }).sortedBy { it.createdAt }
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        observeItems()
        _uiState.update {
            it.copy(
                wifiHost = preferencesManager.getWifiHost(),
                wifiPort = preferencesManager.getWifiPort(),
                hasLastBatch = preferencesManager.getLastBatchCsv().isNotBlank()
            )
        }
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .flowOn(Dispatchers.Default) // DB emission off the main thread
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

    fun selectAllManualItems() = _uiState.update { it.copy(manualSelectedIds = it.allItems.map { i -> i.id }.toSet()) }
    fun clearManualSelection() = _uiState.update { it.copy(manualSelectedIds = emptySet()) }
    fun resetFilters() = _uiState.update { it.copy(selectedTagTypes = emptySet(), selectedUnitTypes = emptySet(), manualSelectedIds = emptySet()) }

    // ── Save / Share CSV ─────────────────────────────────────────────────────

    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.exportItems
                val cr: ContentResolver = context.contentResolver
                cr.openOutputStream(uri)?.use { CsvExporter.writeCsv(it, items) }
                _uiState.update { it.copy(isExporting = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun shareAsCsv(context: Context) {
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

    // ── Share WiFi ────────────────────────────────────────────────────────────

    fun setWifiHost(host: String) = _uiState.update { it.copy(wifiHost = host) }
    fun setWifiPort(port: String) = _uiState.update { it.copy(wifiPort = port.filter { ch -> ch.isDigit() }.take(5)) }

    private var pendingDecision: CompletableDeferred<String>? = null

    private sealed class WifiOutcome {
        data class RetryWith(val payload: ByteArray) : WifiOutcome()
        data class Finished(val message: String) : WifiOutcome()
    }

    fun shareViaWifi() {
        if (validateHostPort() == null) return
        val items = _uiState.value.exportItems
        if (items.isEmpty()) { _uiState.update { it.copy(wifiInfo = "EMPTY") }; return }
        val bytes = WifiSender.csvBytes(items)
        val csvText = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        rememberLastBatch(csvText)
        launchShare { bytes }
    }

    fun resendLastBatch() {
        if (validateHostPort() == null) return
        val csv = preferencesManager.getLastBatchCsv()
        if (csv.isBlank()) { _uiState.update { it.copy(wifiInfo = "EMPTY") }; return }
        launchShare { WifiSender.csvBytesFromText(csv) }
    }

    private fun rememberLastBatch(csvText: String) {
        viewModelScope.launch { preferencesManager.setLastBatchCsv(csvText) }
        _uiState.update { it.copy(hasLastBatch = true) }
    }

    fun shareCsvViaWifi(csvText: String) {
        if (validateHostPort() == null) return
        launchShare { WifiSender.csvBytesFromText(csvText) }
    }

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
                                _uiState.update { it.copy(wifiStage = "printing", wifiDecision = null) }
                            } else if (ready > 0) {
                                val choice = askDecision(
                                    WifiDecisionRequest(WifiDecisionKind.PrintOrCancel(ready), failed)
                                )
                                if (choice == "print") {
                                    s.sendDecision("print")
                                    _uiState.update { it.copy(wifiStage = "printing") }
                                } else {
                                    s.sendDecision("cancel")
                                }
                            } else {
                                recordWifiHistory(msg)
                                val choice = askDecision(
                                    WifiDecisionRequest(WifiDecisionKind.RetryOrCancel, failed)
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
                                val choice = askDecision(
                                    WifiDecisionRequest(
                                        WifiDecisionKind.ReprintSheets(printed, failedSheets), failed
                                    )
                                )
                                if (choice == "reprint") {
                                    s.sendDecision("reprint")
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
                            val st = msg.optString("stage", "")
                            if (st.isNotBlank()) _uiState.update { it.copy(wifiStage = st) }
                        }
                        else -> {}
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

    private suspend fun recordWifiHistory(msg: JSONObject) {
        val jobId = System.currentTimeMillis()
        val out = mutableListOf<WifiPrintHistoryEntity>()
        msg.optJSONArray("sheets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val first = if (itemsArr.length() > 0) itemsArr.getJSONObject(0) else JSONObject()
                out.add(WifiPrintHistoryEntity(
                    jobId = jobId, timestamp = jobId, kind = "sheet",
                    tagType = o.optString("tag", ""), unitType = o.optString("unit", ""),
                    copies = o.optInt("copies", 1), nTags = o.optInt("n_tags", itemsArr.length()),
                    summary = first.optString("eng", "").ifBlank { first.optString("pos", "") },
                    posCode = first.optString("pos", ""), price = first.optString("price", ""),
                    reason = "", itemsJson = itemsArr.toString()
                ))
            }
        }
        msg.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("status", "") != "failed") continue
                val pos = o.optString("pos", ""); val eng = o.optString("eng", "")
                val one = JSONArray().put(JSONObject().put("pos", pos).put("eng", eng)
                    .put("unit", o.optString("unit", "")).put("copies", o.optInt("copies", 1)).put("price", ""))
                out.add(WifiPrintHistoryEntity(
                    jobId = jobId, timestamp = jobId, kind = "failed",
                    tagType = o.optString("tag", ""), unitType = o.optString("unit", ""),
                    copies = o.optInt("copies", 1), nTags = 1,
                    summary = eng.ifBlank { pos }, posCode = pos, price = "",
                    reason = o.optString("reason", ""), itemsJson = one.toString()
                ))
            }
        }
        if (out.isEmpty()) return
        try {
            historyRepository.insertAll(out)
            historyRepository.trimOlderThan(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)
            historyRepository.trimToCount(5000)
        } catch (_: Exception) {}
    }

    private suspend fun itemRetryOutcome(printed: Int, failed: List<WifiFailedItem>, retryCsv: String): WifiOutcome {
        if (failed.isEmpty()) return WifiOutcome.Finished("PRINTED:$printed")
        val choice = askDecision(WifiDecisionRequest(WifiDecisionKind.RetryLeft(printed), failed))
        return if (choice == "retry") WifiOutcome.RetryWith(WifiSender.csvBytesFromText(retryCsv))
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

    fun submitWifiDecision(choice: String) {
        pendingDecision?.complete(choice)
        pendingDecision = null
    }

    fun consumeWifiInfo() = _uiState.update { it.copy(wifiInfo = null) }

    fun discoverPcs() {
        viewModelScope.launch {
            _uiState.update { it.copy(discovering = true, discovered = emptyList()) }
            val pcs = withContext(Dispatchers.IO) { WifiDiscovery.discover(1500) }
            _uiState.update {
                it.copy(discovering = false, discovered = pcs,
                    wifiInfo = if (pcs.isEmpty()) "NO_PCS" else null)
            }
        }
    }

    fun selectPc(pc: WifiPc) =
        _uiState.update { it.copy(wifiHost = pc.ip, wifiPort = pc.port.toString(), discovered = emptyList()) }

    fun testWifi() {
        val host = _uiState.value.wifiHost.trim()
        val portInt = _uiState.value.wifiPort.trim().toIntOrNull()
        if (host.isBlank() || portInt == null || portInt !in 1..65535) {
            _uiState.update { it.copy(wifiInfo = "INVALID_INPUT") }; return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true) }
            val info = try {
                val name = withContext(Dispatchers.IO) { WifiSender.ping(host, portInt) }
                "TEST_OK:$name"
            } catch (e: Exception) { "TEST_FAIL:${e.message ?: "unreachable"}" }
            _uiState.update { it.copy(testing = false, wifiInfo = info) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun resetSuccess() = _uiState.update { it.copy(success = false) }
}
