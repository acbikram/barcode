package com.industrial.barcodescanner.presentation.screens.wifihistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.data.local.entity.WifiPrintHistoryEntity
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
private fun fmt(ts: Long): String = DT_FMT.format(
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
)

private data class SheetItem(val pos: String, val eng: String, val unit: String, val copies: Int, val price: String)

private fun parseSheetItems(json: String): List<SheetItem> = try {
    val a = JSONArray(json)
    (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let { o ->
            SheetItem(
                o.optString("pos", ""), o.optString("eng", ""),
                o.optString("unit", ""), o.optInt("copies", 1), o.optString("price", "")
            )
        }
    }
} catch (_: Exception) { emptyList() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiHistoryScreen(
    navController: NavController,
    viewModel: WifiHistoryViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val search by viewModel.search.collectAsState()
    val range by viewModel.range.collectAsState()
    val totals by viewModel.totals.collectAsState()
    var showClear by remember { mutableStateOf(false) }

    // Reprint actions stash a CSV then jump to Export, which sends it.
    fun goExport() {
        navController.navigate(Screen.Export.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = SurfaceDark,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wifi_history_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = { showClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.wifi_history_clear))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.setSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.wifi_history_search_hint)) }
            )

            // Today's totals
            Text(
                stringResource(R.string.wifi_history_today_totals, totals.pagesToday, totals.failedToday),
                style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent),
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // Date-range filter chips — scrollable so they fit on narrow screens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = listOf(
                    DateRange.ALL to R.string.wifi_history_range_all,
                    DateRange.TODAY to R.string.wifi_history_range_today,
                    DateRange.WEEK to R.string.wifi_history_range_week,
                    DateRange.MONTH to R.string.wifi_history_range_month
                )
                chips.forEach { (r, labelRes) ->
                    FilterChip(
                        selected = range == r,
                        onClick = { viewModel.setRange(r) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.wifi_history_empty),
                        color = SubtleGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(groups, key = { it.jobId }) { group ->
                        JobCard(
                            timestamp = group.timestamp,
                            sheetCount = group.sheets.size,
                            failedCount = group.failed.size,
                            onReprintJob = { viewModel.reprintJob(group); goExport() },
                            onRetryFailed = { viewModel.retryFailed(group); goExport() },
                            onDeleteJob = { viewModel.deleteJob(group.jobId) }
                        ) {
                            group.sheets.forEach { sheet ->
                                SheetRow(sheet = sheet, onReprint = { viewModel.reprintSheet(sheet); goExport() })
                            }
                            if (group.failed.isNotEmpty()) {
                                FailedSection(group.failed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.wifi_history_clear)) },
            text = { Text(stringResource(R.string.wifi_history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClear = false }) {
                    Text(stringResource(R.string.wifi_history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun JobCard(
    timestamp: Long,
    sheetCount: Int,
    failedCount: Int,
    onReprintJob: () -> Unit,
    onRetryFailed: () -> Unit,
    onDeleteJob: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(fmt(timestamp), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.wifi_history_job_summary, sheetCount, failedCount),
                        style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray)
                    )
                }
                IconButton(onClick = onDeleteJob) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.wifi_history_delete_job), tint = SubtleGray)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sheetCount > 0) {
                    OutlinedButton(onClick = onReprintJob, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wifi_history_reprint_job))
                    }
                }
                if (failedCount > 0) {
                    OutlinedButton(onClick = onRetryFailed, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wifi_history_retry_failed))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(sheet: WifiPrintHistoryEntity, onReprint: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val items = remember(sheet.id) { parseSheetItems(sheet.itemsJson) }
    val grouped = sheet.tagType.uppercase(Locale.getDefault()) in setOf("4PCS", "4PCS_DATE", "VEG")

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable(enabled = items.size > 1) { expanded = !expanded }
                ) {
                    Text(sheet.summary.ifBlank { sheet.posCode.ifBlank { "—" } }, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    val meta = buildString {
                        append("${stringResource(R.string.wifi_history_pos)}: ${sheet.posCode}")
                        append("  •  ${sheet.unitType} × ${sheet.copies}")
                        if (sheet.price.isNotBlank()) append("  •  ${sheet.price}")
                        if (grouped) append("  •  ${sheet.nTags}/4")
                    }
                    Text(meta, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                    Text(
                        stringResource(R.string.wifi_history_printed),
                        style = MaterialTheme.typography.labelSmall.copy(color = GreenAccent),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (items.size > 1) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = SubtleGray
                    )
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onReprint, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.wifi_history_reprint))
                }
            }
            if (expanded && items.size > 1) {
                Spacer(Modifier.height(6.dp))
                items.forEach { it ->
                    Text(
                        "• ${it.eng.ifBlank { it.pos }}  (${it.pos}, ${it.unit} × ${it.copies})",
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedSection(failed: List<WifiPrintHistoryEntity>) {
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.wifi_history_failed_section, failed.size),
        style = MaterialTheme.typography.labelMedium.copy(color = ErrorRed),
        fontWeight = FontWeight.Bold
    )
    failed.forEach { f ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(f.summary.ifBlank { f.posCode }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(
                "${stringResource(R.string.wifi_history_pos)}: ${f.posCode}  •  ${f.unitType} × ${f.copies}",
                style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray)
            )
            Text(
                stringResource(R.string.wifi_history_failed_format, f.reason),
                style = MaterialTheme.typography.labelSmall.copy(color = ErrorRed)
            )
        }
    }
}
