package com.industrial.barcodescanner.presentation.screens.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.domain.model.ResolvedItem
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportSuccessMsg = stringResource(R.string.export_successful)
    val shareCsvLabel = stringResource(R.string.share_csv)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportToUri(uri) }

    val exportCount = uiState.exportItems.size

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.export_data)) }) },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {

                // ── Header ─────────────────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.export_all_records_csv), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.total_records_count_format, uiState.totalRecords), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // ── Selective export toggle ─────────────────────────────────────
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.export_selected_only), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                                Switch(checked = uiState.selectiveExportEnabled, onCheckedChange = { viewModel.setSelectiveExportEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = GreenAccent, checkedTrackColor = GreenAccent.copy(alpha = 0.4f)))
                            }
                            if (uiState.selectiveExportEnabled) {
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = uiState.selectionType == ExportSelectionType.FILTER, onClick = { viewModel.setSelectionType(ExportSelectionType.FILTER) }, label = { Text(stringResource(R.string.export_by_filter)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent.copy(alpha = 0.2f), selectedLabelColor = CyanAccent))
                                    FilterChip(selected = uiState.selectionType == ExportSelectionType.MANUAL, onClick = { viewModel.setSelectionType(ExportSelectionType.MANUAL) }, label = { Text(stringResource(R.string.export_by_items)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent.copy(alpha = 0.2f), selectedLabelColor = CyanAccent))
                                }
                                Spacer(Modifier.height(12.dp))
                                if (uiState.selectionType == ExportSelectionType.FILTER) {
                                    Text(stringResource(R.string.export_filter_by_tag), style = MaterialTheme.typography.labelLarge.copy(color = GreenAccent, fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TAG_TYPES.forEach { t -> FilterChip(selected = uiState.selectedTagTypes.contains(t), onClick = { viewModel.toggleTagTypeFilter(t) }, label = { Text(t) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenAccent.copy(alpha = 0.2f), selectedLabelColor = GreenAccent)) }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.export_filter_by_unit), style = MaterialTheme.typography.labelLarge.copy(color = OrangeAccent, fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        UNIT_TYPES.forEach { u -> FilterChip(selected = uiState.selectedUnitTypes.contains(u), onClick = { viewModel.toggleUnitTypeFilter(u) }, label = { Text(u) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrangeAccent.copy(alpha = 0.2f), selectedLabelColor = OrangeAccent)) }
                                    }
                                    TextButton(onClick = { viewModel.resetFilters() }) { Text(stringResource(R.string.export_reset_filters), color = SubtleGray) }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { viewModel.selectAllManualItems() }) { Text(stringResource(R.string.select_all), color = CyanAccent) }
                                        TextButton(onClick = { viewModel.clearManualSelection() }) { Text(stringResource(R.string.export_reset_filters), color = SubtleGray) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(if (exportCount == 0) stringResource(R.string.export_no_items_match) else stringResource(R.string.export_count_format, exportCount), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                            }
                        }
                    }
                }

                // ── Manual item list ────────────────────────────────────────────
                if (uiState.selectiveExportEnabled && uiState.selectionType == ExportSelectionType.MANUAL) {
                    items(uiState.allItems, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (uiState.manualSelectedIds.contains(item.id)) CyanAccent.copy(alpha = 0.12f) else SurfaceDark), shape = RoundedCornerShape(10.dp), onClick = { viewModel.toggleManualItemSelected(item.id) }) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = uiState.manualSelectedIds.contains(item.id), onCheckedChange = { viewModel.toggleManualItemSelected(item.id) }, colors = CheckboxDefaults.colors(checkedColor = CyanAccent))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.displayName, style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold), maxLines = 1)
                                    Text(stringResource(R.string.barcode_format, item.barcode), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(stringResource(R.string.tag_type_format, item.tagType), style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent))
                                        Text(stringResource(R.string.unit_type_format, item.unitType), style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent))
                                        Text(stringResource(R.string.copies_format, item.copies), style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── WiFi Print card ─────────────────────────────────────────────
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.wifi_print), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                            when (uiState.wifiState) {
                                WifiState.IDLE -> {
                                    Button(onClick = { viewModel.discoverPcs() }, modifier = Modifier.fillMaxWidth(), enabled = exportCount > 0) {
                                        Icon(Icons.Default.Wifi, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.wifi_discover))
                                    }
                                }
                                WifiState.DISCOVERING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        Text(stringResource(R.string.wifi_discovering), style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray))
                                    }
                                }
                                WifiState.PC_FOUND -> {
                                    if (uiState.discoveredPcs.size > 1) {
                                        Text(stringResource(R.string.wifi_select_pc), style = MaterialTheme.typography.labelMedium.copy(color = SubtleGray))
                                        uiState.discoveredPcs.forEach { pc ->
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = uiState.selectedPc == pc, onClick = { viewModel.selectPc(pc) })
                                                Text("${pc.name}  (${pc.ip}:${pc.port})", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    } else {
                                        Text("${uiState.discoveredPcs.first().name}  (${uiState.discoveredPcs.first().ip})", style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { viewModel.resetWifi() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.wifi_reset)) }
                                        Button(onClick = { viewModel.sendCsvToPC() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.wifi_send)) }
                                    }
                                }
                                WifiState.SENDING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = GreenAccent)
                                        Text(uiState.wifiStatusMessage, style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray))
                                    }
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GreenAccent, trackColor = GreenAccent.copy(alpha = 0.2f))
                                }
                                WifiState.PREVIEW, WifiState.NEEDS_DECISION -> {
                                    // Handled by full-screen dialogs below
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = CyanAccent)
                                        Text(uiState.wifiStatusMessage, style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent))
                                    }
                                }
                                WifiState.PRINTING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = GreenAccent)
                                        Text(uiState.wifiStatusMessage, style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent))
                                    }
                                }
                                WifiState.DONE -> {
                                    Card(colors = CardDefaults.cardColors(containerColor = GreenAccent.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)) {
                                        Text(stringResource(R.string.wifi_done_format, uiState.wifiPrintedCount), style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(14.dp))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { viewModel.resetWifi() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.wifi_reset)) }
                                        Button(onClick = { viewModel.discoverPcs() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.wifi_send)) }
                                    }
                                }
                                WifiState.ERROR -> {
                                    Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)), shape = RoundedCornerShape(8.dp)) {
                                        Text(uiState.wifiStatusMessage, style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed), modifier = Modifier.fillMaxWidth().padding(12.dp))
                                    }
                                    Button(onClick = { viewModel.discoverPcs() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wifi_retry)) }
                                }
                            }
                        }
                    }
                }

                // ── Save / Share CSV ────────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { saveLauncher.launch("BarcodeToCsv_${System.currentTimeMillis()}.csv") }, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isExporting && exportCount > 0) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.save_csv))
                        }
                        Button(onClick = { viewModel.shareAsCsv() }, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isExporting && exportCount > 0) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.share_csv))
                        }
                    }
                }
            }

            // Export loading overlay
            if (uiState.isExporting) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                    Card(modifier = Modifier.width(200.dp)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.exporting))
                        }
                    }
                }
            }
        }
    }

    // ── Preview dialog (all items resolved — no failures) ────────────────────
    if (uiState.wifiState == WifiState.PREVIEW && uiState.previewReadyItems.isNotEmpty()) {
        PrintPreviewDialog(
            readyItems = uiState.previewReadyItems,
            onPrint = { viewModel.confirmPrint() },
            onCancel = { viewModel.cancelPreview() }
        )
    }

    // ── Error/decision dialog (some items failed) ─────────────────────────────
    if (uiState.wifiState == WifiState.NEEDS_DECISION) {
        PrintDecisionDialog(
            readyItems = uiState.previewReadyItems,
            failedItems = uiState.previewFailedItems,
            onPrintReady = { viewModel.submitDecision(true) },
            onCancelAll = { viewModel.cancelDecision() }
        )
    }

    // ── Side effects ──────────────────────────────────────────────────────────
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) { scope.launch { snackbarHostState.showSnackbar(uiState.error!!); viewModel.clearError() } }
    }
    if (uiState.success) {
        LaunchedEffect(Unit) { scope.launch { snackbarHostState.showSnackbar(exportSuccessMsg); viewModel.resetSuccess() } }
    }
    uiState.shareFileUri?.let { uri ->
        LaunchedEffect(uri) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, shareCsvLabel))
            viewModel.consumeShareFileUri()
        }
    }
}

// ── Print Preview Dialog ──────────────────────────────────────────────────────

@Composable
private fun PrintPreviewDialog(
    readyItems: List<ResolvedItem>,
    onPrint: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceDark, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar
                Box(modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(16.dp)) {
                    Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                }
                Text(stringResource(R.string.preview_ready_format, readyItems.size), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                // Column headers
                PreviewTableHeader()
                HorizontalDivider(color = Color(0xFF30363D))

                // Item rows
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(readyItems) { item ->
                        PreviewItemRow(item)
                        HorizontalDivider(color = Color(0xFF1C2333))
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D))
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.preview_cancel)) }
                    Button(onClick = onPrint, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)) {
                        Text(stringResource(R.string.preview_print), color = Color(0xFF001100), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(stringResource(R.string.preview_col_item_code), style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent), modifier = Modifier.width(100.dp))
        Text(stringResource(R.string.preview_col_description), style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray), modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        Text(stringResource(R.string.preview_col_price), style = MaterialTheme.typography.labelSmall.copy(color = OrangeAccent), modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
        Text(stringResource(R.string.preview_col_copies), style = MaterialTheme.typography.labelSmall.copy(color = GreenAccent), modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PreviewItemRow(item: ResolvedItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.pos, style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent, fontFamily = FontFamily.Monospace), modifier = Modifier.width(100.dp), maxLines = 1)
        Text(item.eng.ifBlank { item.pos }, style = MaterialTheme.typography.bodySmall.copy(color = Color.White), modifier = Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("—", style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent), modifier = Modifier.width(60.dp), textAlign = TextAlign.End)  // price always "—" before printing
        Text("×${item.copies}", style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent, fontWeight = FontWeight.Bold), modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
    }
}

// ── Print Decision Dialog (some items failed) ─────────────────────────────────

@Composable
private fun PrintDecisionDialog(
    readyItems: List<ResolvedItem>,
    failedItems: List<ResolvedItem>,
    onPrintReady: () -> Unit,
    onCancelAll: () -> Unit
) {
    Dialog(onDismissRequest = onCancelAll) {
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceDark, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title
                Box(modifier = Modifier.fillMaxWidth().background(ErrorRed.copy(alpha = 0.2f)).padding(16.dp)) {
                    Text(stringResource(R.string.error_dialog_title), style = MaterialTheme.typography.titleMedium.copy(color = ErrorRed, fontWeight = FontWeight.Bold))
                }

                // Summary chips
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = GreenAccent.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.error_ready_format, readyItems.size), style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.error_failed_format, failedItems.size), style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    // Ready items section
                    if (readyItems.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.error_ready_format, readyItems.size), style = MaterialTheme.typography.labelLarge.copy(color = GreenAccent, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                        item { PreviewTableHeader() }
                        items(readyItems) { item ->
                            PreviewItemRow(item)
                            HorizontalDivider(color = Color(0xFF1C2333))
                        }
                    }

                    // Failed items section
                    if (failedItems.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item {
                            Row(modifier = Modifier.fillMaxWidth().background(ErrorRed.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(stringResource(R.string.error_failed_items_header), style = MaterialTheme.typography.labelLarge.copy(color = ErrorRed, fontWeight = FontWeight.Bold))
                            }
                        }
                        items(failedItems) { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.width(100.dp)) {
                                    Text(item.pos, style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontFamily = FontFamily.Monospace), maxLines = 1)
                                    Text(item.tag, style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray))
                                }
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    if (item.eng.isNotBlank()) {
                                        Text(item.eng, style = MaterialTheme.typography.bodySmall.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    // Show exact error reason from PC
                                    Text(
                                        item.reason.ifBlank { "Unknown error" },
                                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontSize = 11.sp),
                                        maxLines = 2
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2D1010))
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D))
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (readyItems.isNotEmpty()) {
                        Button(onClick = onPrintReady, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)) {
                            Text(stringResource(R.string.error_print_ready), color = Color(0xFF001100), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(onClick = onCancelAll, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                        Text(stringResource(R.string.error_cancel_all), color = ErrorRed)
                    }
                }
            }
        }
    }
}
