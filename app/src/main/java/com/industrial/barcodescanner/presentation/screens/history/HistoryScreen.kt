package com.industrial.barcodescanner.presentation.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.domain.model.PrintItem
import com.industrial.barcodescanner.domain.model.PrintJob
import com.industrial.barcodescanner.domain.model.PrintSheet
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.screens.printhistory.PrintHistoryViewModel
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.presentation.theme.SurfaceVariant
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    scanHistoryViewModel: HistoryViewModel = hiltViewModel(),
    printHistoryViewModel: PrintHistoryViewModel = hiltViewModel(),
    initialFilter: String = "ALL",
    initialSort: String = "NEWEST"
) {
    // 0 = Scan History, 1 = Print Jobs
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialFilter, initialSort) {
        scanHistoryViewModel.applyInitialFilterAndSort(initialFilter, initialSort)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // ── Toggle row ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToggleTab(
                    label = stringResource(R.string.history),
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                ToggleTab(
                    label = stringResource(R.string.print_history),
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedTab == 0) {
                ScanHistoryTab(navController = navController, viewModel = scanHistoryViewModel)
            } else {
                PrintJobsTab(viewModel = printHistoryViewModel)
            }
        }
    }
}

@Composable
private fun ToggleTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) CyanAccent else SurfaceDark,
            contentColor   = if (selected) Color.Black  else SubtleGray
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(4.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: Scan History
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ScanHistoryTab(navController: NavController, viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showFilterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteFilteredDialog by remember { mutableStateOf(false) }

    val sortLabel = when (uiState.sortOrder) {
        SortOrder.NEWEST -> stringResource(R.string.sort_newest)
        SortOrder.OLDEST -> stringResource(R.string.sort_oldest)
        SortOrder.COPIES -> stringResource(R.string.sort_copies)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TopBar actions row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.selectionMode) {
                IconButton(onClick = { viewModel.exitSelectionMode() }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = SubtleGray)
                }
                Text(stringResource(R.string.items_selected_format, uiState.selectedIds.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                IconButton(onClick = { viewModel.selectAllVisible() }) { Icon(Icons.Default.SelectAll, contentDescription = null, tint = CyanAccent) }
                IconButton(onClick = { showDeleteSelectedDialog = true }, enabled = uiState.selectedIds.isNotEmpty()) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = if (uiState.selectedIds.isNotEmpty()) ErrorRed else SubtleGray)
                }
            } else {
                Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                IconButton(onClick = { viewModel.toggleSortOrder() }) { Icon(Icons.Default.Sort, contentDescription = null, tint = CyanAccent) }
                Box {
                    FilterChip(selected = uiState.filter != FILTER_ALL_KEY, onClick = { showFilterMenu = true }, label = { Text(filterDisplayLabel(uiState.filter), style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent.copy(alpha = 0.2f), selectedLabelColor = CyanAccent))
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = { viewModel.setFilter(FILTER_ALL_KEY); showFilterMenu = false })
                        HorizontalDivider()
                        TAG_TYPES.forEach { t -> DropdownMenuItem(text = { Text(stringResource(R.string.tag_type_format, t)) }, onClick = { viewModel.setFilter("TAG_$t"); showFilterMenu = false }) }
                        HorizontalDivider()
                        UNIT_TYPES.forEach { u -> DropdownMenuItem(text = { Text(stringResource(R.string.unit_type_format, u)) }, onClick = { viewModel.setFilter("UNIT_$u"); showFilterMenu = false }) }
                    }
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = CyanAccent) }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.select_items)) }, onClick = { viewModel.enterSelectionMode(); showOverflowMenu = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_filtered_format, viewModel.countForCurrentFilter()), color = ErrorRed) }, onClick = { showDeleteFilteredDialog = true; showOverflowMenu = false })
                    }
                }
            }
        }

        OutlinedTextField(value = uiState.searchQuery, onValueChange = viewModel::updateSearchQuery, label = { Text(stringResource(R.string.search_by_name_or_barcode)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) }, singleLine = true, shape = RoundedCornerShape(10.dp))

        Text(stringResource(R.string.sort_items_summary_format, sortLabel, uiState.filteredItems.size), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray), modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.filteredItems, key = { it.id }) { item ->
                HistoryItemCard(item = item, selectionMode = uiState.selectionMode, selected = uiState.selectedIds.contains(item.id),
                    onClick = {
                        if (uiState.selectionMode) viewModel.toggleItemSelected(item.id)
                        else navController.navigate(Screen.Detail.passId(item.id))
                    },
                    onLongClick = { if (!uiState.selectionMode) viewModel.enterSelectionMode(); viewModel.toggleItemSelected(item.id) }
                )
            }
            if (uiState.filteredItems.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_items_found), style = MaterialTheme.typography.bodyLarge.copy(color = SubtleGray)) } }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(onDismissRequest = { showDeleteSelectedDialog = false }, title = { Text(stringResource(R.string.delete_selected)) }, text = { Text(stringResource(R.string.delete_selected_confirm_format, uiState.selectedIds.size)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteSelected(); showDeleteSelectedDialog = false }) { Text(stringResource(R.string.delete), color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showDeleteSelectedDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDeleteFilteredDialog) {
        val count = viewModel.countForCurrentFilter()
        AlertDialog(onDismissRequest = { showDeleteFilteredDialog = false }, title = { Text(stringResource(R.string.delete_filtered_format, count)) }, text = { Text(stringResource(R.string.delete_filtered_confirm_format, count)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteByCurrentFilter(); showDeleteFilteredDialog = false }) { Text(stringResource(R.string.delete), color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showDeleteFilteredDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun filterDisplayLabel(filter: String): String = when {
    filter == FILTER_ALL_KEY -> stringResource(R.string.filter_all)
    filter.startsWith("TAG_") -> stringResource(R.string.tag_type_format, filter.removePrefix("TAG_"))
    filter.startsWith("UNIT_") -> stringResource(R.string.unit_type_format, filter.removePrefix("UNIT_"))
    else -> filter
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(item: ScannedItem, selectionMode: Boolean = false, selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = if (selected) CyanAccent.copy(alpha = 0.12f) else SurfaceDark), shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) { Checkbox(checked = selected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = CyanAccent)); Spacer(Modifier.width(4.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold), maxLines = 1)
                if (item.itemCode != null) Text(stringResource(R.string.item_code_format, item.itemCode), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                if (item.productName != null || item.itemCode != null) Text(stringResource(R.string.barcode_format, item.barcode), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tag_type_format, item.tagType), style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent))
                    Text(stringResource(R.string.unit_type_format, item.unitType), style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent))
                    Text(stringResource(R.string.copies_format, item.copies), style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent))
                }
                Text(stringResource(R.string.scanned_format, formatTimestamp(item.createdAt)), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 2: Print Jobs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrintJobsTab(viewModel: PrintHistoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.print_history), style = MaterialTheme.typography.titleLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
            if (uiState.jobs.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) }
            }
        }

        OutlinedTextField(value = uiState.searchQuery, onValueChange = viewModel::updateSearch, label = { Text(stringResource(R.string.search_by_name_or_barcode)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) }, singleLine = true, shape = RoundedCornerShape(10.dp))

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (uiState.filteredJobs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.print_history_empty), style = MaterialTheme.typography.bodyLarge.copy(color = SubtleGray), textAlign = TextAlign.Center)
            }
        } else {
            Text("${uiState.filteredJobs.size} ${stringResource(R.string.print_jobs)}", style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray), modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.filteredJobs, key = { it.id }) { job ->
                    PrintJobCard(job = job, onDelete = { viewModel.deleteJob(job.id) })
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(onDismissRequest = { showClearDialog = false }, title = { Text(stringResource(R.string.clear_all_records)) }, text = { Text(stringResource(R.string.clear_all_records_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) { Text(stringResource(R.string.clear), color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrintJobCard(job: PrintJob, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(job.timestamp), ZoneId.systemDefault())
    val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dateTime)
    val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss").format(dateTime)

    val descSummary = remember(job) {
        val names = job.sheets.flatMap { it.items }.mapNotNull { it.eng.takeIf { s -> s.isNotBlank() } }.distinct().take(3)
        when {
            names.isEmpty() -> job.sheets.firstOrNull()?.items?.firstOrNull()?.pos ?: "—"
            names.size == 1 -> names[0]
            names.size == 2 -> "${names[0].take(20)} … ${names[1].take(20)}"
            else -> "${names[0].take(16)} … +${job.totalItems - 1} more"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { expanded = !expanded },
            onLongClick = { showDeleteDialog = true }
        ),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dateStr, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray, fontWeight = FontWeight.Medium))
                        Text(timeStr, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (job.sheets.size > 1 || job.totalItems > 1) {
                            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                        }
                        Text(descSummary, style = MaterialTheme.typography.bodyMedium.copy(color = if (job.sheets.size > 1) CyanAccent else Color.White, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.print_job_sheets_format, job.totalSheets), style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent))
                        Text(stringResource(R.string.print_job_items_format, job.totalItems), style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent))
                        Text(job.sheets.firstOrNull()?.tag ?: "", style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFF30363D), modifier = Modifier.padding(bottom = 10.dp))
                    job.sheets.forEachIndexed { idx, sheet ->
                        // Sheet header
                        Row(modifier = Modifier.fillMaxWidth().background(SurfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("#${idx + 1} — ${sheet.tag} / ${sheet.unit}", style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                            Text("${sheet.nTags} tag(s) × ${sheet.copies} copy", style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray))
                        }
                        // Items on that sheet
                        sheet.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.pos, style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent, fontFamily = FontFamily.Monospace), modifier = Modifier.width(100.dp), maxLines = 1)
                                Text(item.eng.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall.copy(color = Color.White), modifier = Modifier.weight(1f).padding(horizontal = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.price.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent, fontWeight = FontWeight.Bold), modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                                Text("×${item.copies}", style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent), modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                            }
                        }
                        if (idx < job.sheets.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text(stringResource(R.string.delete_item)) }, text = { Text(stringResource(R.string.print_job_delete_confirm)) },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text(stringResource(R.string.delete), color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
