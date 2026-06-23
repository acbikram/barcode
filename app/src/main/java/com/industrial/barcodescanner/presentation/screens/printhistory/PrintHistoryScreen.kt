package com.industrial.barcodescanner.presentation.screens.printhistory

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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.domain.model.PrintItem
import com.industrial.barcodescanner.domain.model.PrintJob
import com.industrial.barcodescanner.domain.model.PrintSheet
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.presentation.theme.SurfaceVariant
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintHistoryScreen(
    navController: NavController,
    viewModel: PrintHistoryViewModel = hiltViewModel(),
    onReprintJob: (PrintJob) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.print_history),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = CyanAccent, fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    if (uiState.jobs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_all_records), tint = ErrorRed)
                        }
                    }
                }
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearch,
                label = { Text(stringResource(R.string.search_by_name_or_barcode)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredJobs.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.print_history_empty),
                        style = MaterialTheme.typography.bodyLarge.copy(color = SubtleGray),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Text(
                    "${uiState.filteredJobs.size} ${stringResource(R.string.print_jobs)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredJobs, key = { it.id }) { job ->
                        PrintJobCard(
                            job = job,
                            onReprint = { onReprintJob(job) },
                            onDelete = { viewModel.deleteJob(job.id) }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_records)) },
            text = { Text(stringResource(R.string.clear_all_records_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text(stringResource(R.string.clear), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrintJobCard(
    job: PrintJob,
    onReprint: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(job.timestamp), ZoneId.systemDefault()
    )
    val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dateTime)
    val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss").format(dateTime)

    // Build a compact description of the job — first few item names
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

            // ── Job header row ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // Date + Time
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dateStr, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray, fontWeight = FontWeight.Medium))
                        Text(timeStr, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                    }
                    Spacer(Modifier.height(4.dp))
                    // Description summary (expandable arrow prefix if multi-sheet)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (job.sheets.size > 1 || job.totalItems > 1) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            descSummary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (job.sheets.size > 1) CyanAccent else Color.White,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.print_job_sheets_format, job.totalSheets),
                            style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
                        )
                        Text(
                            stringResource(R.string.print_job_items_format, job.totalItems),
                            style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                        )
                        Text(
                            job.sheets.firstOrNull()?.tag ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent)
                        )
                    }
                }

                // Reprint button for the whole job
                Button(
                    onClick = onReprint,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reprint), color = CyanAccent, fontSize = 12.sp)
                }
            }

            // ── Expanded: one card per physical sheet ─────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    HorizontalDivider(color = Color(0xFF30363D), modifier = Modifier.padding(bottom = 10.dp))
                    job.sheets.forEachIndexed { idx, sheet ->
                        PrintSheetExpandedRow(
                            index = idx + 1,
                            sheet = sheet
                        )
                        if (idx < job.sheets.lastIndex) {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { Text(stringResource(R.string.print_job_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun PrintSheetExpandedRow(index: Int, sheet: PrintSheet) {
    // Sheet header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$index — ${sheet.tag} / ${sheet.unit}",
            style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
        )
        Text(
            "${sheet.nTags} tag(s) × ${sheet.copies} copy",
            style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray)
        )
    }
    // Item rows for this sheet
    sheet.items.forEach { item ->
        PrintItemRow(item = item)
    }
}

@Composable
private fun PrintItemRow(item: PrintItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Item code
        Text(
            item.pos,
            style = MaterialTheme.typography.bodySmall.copy(
                color = CyanAccent,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            modifier = Modifier.width(100.dp)
        )
        // Description
        Text(
            item.eng.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Price
        Text(
            item.price.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent, fontWeight = FontWeight.Bold),
            modifier = Modifier.width(50.dp)
        )
        // Copies
        Text(
            "×${item.copies}",
            style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent),
            modifier = Modifier.width(30.dp)
        )
    }
}
