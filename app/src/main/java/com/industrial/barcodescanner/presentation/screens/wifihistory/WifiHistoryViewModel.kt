package com.industrial.barcodescanner.presentation.screens.wifihistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import com.industrial.barcodescanner.domain.repository.WifiPrintHistoryRepository
import com.industrial.barcodescanner.utils.WifiReprintBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** One Share-WiFi send: its printed pages and its failed items. */
data class WifiJobGroup(
    val jobId: Long,
    val timestamp: Long,
    val sheets: List<WifiPrintHistoryEntity>,
    val failed: List<WifiPrintHistoryEntity>
)

/** Date range filter for the history list. */
enum class DateRange { ALL, TODAY, WEEK, MONTH }

/** Quick totals shown at the top of the history screen. */
data class HistoryTotals(val pagesToday: Int, val failedToday: Int, val pagesAll: Int)

@HiltViewModel
class WifiHistoryViewModel @Inject constructor(
    private val historyRepository: WifiPrintHistoryRepository,
    private val reprintBus: WifiReprintBus
) : ViewModel() {

    // Thread-safe formatter (unlike SimpleDateFormat)
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search

    private val _range = MutableStateFlow(DateRange.ALL)
    val range: StateFlow<DateRange> = _range

    /** History grouped by job (newest first), with search + date filters applied. */
    val groups: StateFlow<List<WifiJobGroup>> =
        combine(historyRepository.getAll(), _search, _range) { all, q, range ->
            val cutoff = cutoffFor(range)
            val ql = q.trim().lowercase(Locale.getDefault())
            val filtered = all.filter { e ->
                e.timestamp >= cutoff && (ql.isEmpty() ||
                    e.posCode.lowercase(Locale.getDefault()).contains(ql) ||
                    e.summary.lowercase(Locale.getDefault()).contains(ql) ||
                    e.tagType.lowercase(Locale.getDefault()).contains(ql) ||
                    dateFmt.format(
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(e.timestamp), ZoneId.systemDefault())
                    ).contains(ql))
            }
            filtered.groupBy { it.jobId }
                .map { (jid, rows) ->
                    WifiJobGroup(
                        jobId = jid,
                        timestamp = rows.maxOf { it.timestamp },
                        sheets = rows.filter { it.kind == "sheet" },
                        failed = rows.filter { it.kind == "failed" }
                    )
                }
                .sortedByDescending { it.timestamp }
        }
        .flowOn(Dispatchers.Default) // heavy filter/group off the main thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Today's printed-page and failed counts. */
    val totals: StateFlow<HistoryTotals> =
        historyRepository.getAll()
            .map { all ->
                val t0 = startOfToday()
                val today = all.filter { it.timestamp >= t0 }
                HistoryTotals(
                    pagesToday  = today.count { it.kind == "sheet" },
                    failedToday = today.count { it.kind == "failed" },
                    pagesAll    = all.count   { it.kind == "sheet" }
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryTotals(0, 0, 0))

    fun setSearch(q: String) { _search.value = q }
    fun setRange(r: DateRange) { _range.value = r }

    private fun startOfToday(): Long {
        return LocalDateTime.now()
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
            .atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }

    private fun cutoffFor(range: DateRange): Long = when (range) {
        DateRange.ALL   -> 0L
        DateRange.TODAY -> startOfToday()
        DateRange.WEEK  -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        DateRange.MONTH -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    }

    // ── Reprint ───────────────────────────────────────────────────────────
    fun reprintSheet(sheet: WifiPrintHistoryEntity) = stash(listOf(sheet))
    fun reprintJob(group: WifiJobGroup) = stash(group.sheets)
    fun retryFailed(group: WifiJobGroup) = stash(group.failed)

    private fun stash(entities: List<WifiPrintHistoryEntity>) {
        if (entities.isEmpty()) return
        // If a reprint is already queued, show a warning via the bus result
        reprintBus.request(buildCsv(entities))
    }

    private fun buildCsv(entities: List<WifiPrintHistoryEntity>): String {
        val header = "pos_code,price,tag_type,unit_type,copies,custom_eng,custom_ara"
        val rows = ArrayList<String>()
        var skipped = 0
        for (e in entities) {
            val arr = try {
                JSONArray(e.itemsJson)
            } catch (_: Exception) {
                // itemsJson is corrupted — fall back to the entity's own top-level fields
                skipped++
                val pos = e.posCode.ifBlank { continue }
                rows.add("$pos,,${e.tagType},${e.unitType},${e.copies},,")
                continue
            }
            if (arr.length() == 0) {
                // No items array stored — use entity fields directly
                val pos = e.posCode.ifBlank { continue }
                rows.add("$pos,,${e.tagType},${e.unitType},${e.copies},,")
                continue
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pos = o.optString("pos", "").ifBlank { skipped++; continue }
                val unit = o.optString("unit", e.unitType)
                val copies = o.optInt("copies", 1)
                rows.add("$pos,,${e.tagType},$unit,$copies,,")
            }
        }
        if (skipped > 0) {
            android.util.Log.w("WifiReprintBus", "buildCsv: $skipped row(s) skipped due to missing data")
        }
        return (listOf(header) + rows).joinToString("\n") + "\n"
    }

    fun deleteJob(jobId: Long) { viewModelScope.launch { historyRepository.deleteJob(jobId) } }
    fun clearAll() { viewModelScope.launch { historyRepository.deleteAll() } }
}
