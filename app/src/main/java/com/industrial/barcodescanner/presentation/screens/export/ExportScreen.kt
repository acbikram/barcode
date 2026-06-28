package com.industrial.barcodescanner.presentation.screens.export

import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import com.industrial.barcodescanner.presentation.navigation.Screen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
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
    var showWifiDialog by remember { mutableStateOf(false) }
    val exportSuccessMsg = stringResource(R.string.export_successful)
    val shareCsvLabel = stringResource(R.string.share_csv)

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(context, uri)
        }
    }

    val exportCount = uiState.exportItems.size

    // If the user tapped "Reprint" in the Wi-Fi history, it stashed a CSV and
    // navigated here — pick it up and run the normal Share-WiFi send.
    LaunchedEffect(Unit) {
        viewModel.maybeStartPendingReprint()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.export_data)) }) },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.export_all_records_csv), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.total_records_count_format, uiState.totalRecords),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // ── Selective export toggle ───────────────────────────────────
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.export_selected_only),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Switch(
                                    checked = uiState.selectiveExportEnabled,
                                    onCheckedChange = { viewModel.setSelectiveExportEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = GreenAccent,
                                        checkedTrackColor = GreenAccent.copy(alpha = 0.4f)
                                    )
                                )
                            }

                            if (uiState.selectiveExportEnabled) {
                                Spacer(Modifier.height(12.dp))

                                // Selection type chips
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = uiState.selectionType == ExportSelectionType.FILTER,
                                        onClick = { viewModel.setSelectionType(ExportSelectionType.FILTER) },
                                        label = { Text(stringResource(R.string.export_by_filter)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                            selectedLabelColor = CyanAccent
                                        )
                                    )
                                    FilterChip(
                                        selected = uiState.selectionType == ExportSelectionType.MANUAL,
                                        onClick = { viewModel.setSelectionType(ExportSelectionType.MANUAL) },
                                        label = { Text(stringResource(R.string.export_by_items)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                            selectedLabelColor = CyanAccent
                                        )
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                if (uiState.selectionType == ExportSelectionType.FILTER) {
                                    Text(
                                        stringResource(R.string.export_filter_by_tag),
                                        style = MaterialTheme.typography.labelLarge.copy(color = GreenAccent, fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TAG_TYPES.forEach { tagType ->
                                            FilterChip(
                                                selected = uiState.selectedTagTypes.contains(tagType),
                                                onClick = { viewModel.toggleTagTypeFilter(tagType) },
                                                label = { Text(tagType) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = GreenAccent.copy(alpha = 0.2f),
                                                    selectedLabelColor = GreenAccent
                                                )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Text(
                                        stringResource(R.string.export_filter_by_unit),
                                        style = MaterialTheme.typography.labelLarge.copy(color = OrangeAccent, fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        UNIT_TYPES.forEach { unitType ->
                                            FilterChip(
                                                selected = uiState.selectedUnitTypes.contains(unitType),
                                                onClick = { viewModel.toggleUnitTypeFilter(unitType) },
                                                label = { Text(unitType) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = OrangeAccent.copy(alpha = 0.2f),
                                                    selectedLabelColor = OrangeAccent
                                                )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.resetFilters() }) {
                                        Text(stringResource(R.string.export_reset_filters), color = SubtleGray)
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { viewModel.selectAllManualItems() }) {
                                            Text(stringResource(R.string.select_all), color = CyanAccent)
                                        }
                                        TextButton(onClick = { viewModel.clearManualSelection() }) {
                                            Text(stringResource(R.string.export_reset_filters), color = SubtleGray)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (exportCount == 0) stringResource(R.string.export_no_items_match)
                                    else stringResource(R.string.export_count_format, exportCount),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                        }
                    }
                }

                // ── Manual item selection list ──────────────────────────────────
                if (uiState.selectiveExportEnabled && uiState.selectionType == ExportSelectionType.MANUAL) {
                    items(uiState.allItems, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.manualSelectedIds.contains(item.id))
                                    CyanAccent.copy(alpha = 0.12f) else SurfaceDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            onClick = { viewModel.toggleManualItemSelected(item.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = uiState.manualSelectedIds.contains(item.id),
                                    onCheckedChange = { viewModel.toggleManualItemSelected(item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = CyanAccent,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.barcode_format, item.barcode),
                                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = stringResource(R.string.tag_type_format, item.tagType),
                                            style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
                                        )
                                        Text(
                                            text = stringResource(R.string.unit_type_format, item.unitType),
                                            style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                                        )
                                        Text(
                                            text = stringResource(R.string.copies_format, item.copies),
                                            style = MaterialTheme.typography.bodySmall.copy(color = CyanAccent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Export action buttons ─────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { saveLauncher.launch("BarcodeToCsv_${System.currentTimeMillis()}.csv") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExporting && exportCount > 0
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_csv))
                        }

                        Button(
                            onClick = { viewModel.shareAsCsv(context) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExporting && exportCount > 0
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.share_csv))
                        }

                        Button(
                            onClick = { showWifiDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExporting && !uiState.wifiSending && exportCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            if (uiState.wifiSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Wifi, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.share_wifi))
                        }

                        OutlinedButton(
                            onClick = { viewModel.resendLastBatch() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.hasLastBatch && !uiState.wifiSending && !uiState.isExporting
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_resend_last))
                        }

                        OutlinedButton(
                            onClick = { navController.navigate(Screen.WifiHistory.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_history_button))
                        }
                    }
                }
            }

            // Loading overlay
            if (uiState.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.width(200.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.exporting), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!)
                viewModel.clearError()
            }
        }
    }

    if (uiState.success) {
        LaunchedEffect(Unit) {
            scope.launch {
                snackbarHostState.showSnackbar(exportSuccessMsg)
                viewModel.resetSuccess()
            }
        }
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

    // ── Share WiFi dialog ───────────────────────────────────────────────────
    if (showWifiDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.wifiSending) showWifiDialog = false },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
            title = { Text(stringResource(R.string.wifi_dialog_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.wifi_dialog_hint),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )

                    // What will print — same columns as the PC's Recent Print Jobs:
                    // POS, English, the exact Unit that will print, and Copies.
                    val reviewItems = uiState.exportItems
                    if (reviewItems.isNotEmpty()) {
                        Text(
                            stringResource(R.string.wifi_review_title, reviewItems.size),
                            style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.wifi_review_pos), modifier = Modifier.weight(1.1f),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wifi_review_eng), modifier = Modifier.weight(2f),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wifi_review_unit), modifier = Modifier.weight(0.9f),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wifi_review_copies), modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        val maxRows = 200
                        reviewItems.take(maxRows).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(row.itemCode ?: row.barcode, modifier = Modifier.weight(1.1f),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.productName ?: "—", modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(row.unitType, modifier = Modifier.weight(0.9f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(row.copies.toString(), modifier = Modifier.weight(0.8f),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (reviewItems.size > maxRows) {
                            Text(
                                stringResource(R.string.wifi_review_more, reviewItems.size - maxRows),
                                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                            )
                        }
                        HorizontalDivider()
                    }

                    // #1 — auto-discover PCs on the network
                    OutlinedButton(
                        onClick = { viewModel.discoverPcs() },
                        enabled = !uiState.discovering && !uiState.wifiSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.discovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_searching))
                        } else {
                            Text(stringResource(R.string.wifi_find_pcs))
                        }
                    }

                    // Show discovered PCs as tappable cards
                    uiState.discovered.forEach { pc ->
                        Surface(
                            onClick = { viewModel.selectPc(pc) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(pc.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${pc.ip}:${pc.port}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                        }
                    }

                    // Hint shown after a failed discovery — points user to manual IP entry
                    if (!uiState.discovering && uiState.discovered.isEmpty() && uiState.wifiHost.isBlank()) {
                        Text(
                            stringResource(R.string.wifi_no_pcs_hint),
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                    }

                    OutlinedTextField(
                        value = uiState.wifiHost,
                        onValueChange = { viewModel.setWifiHost(it) },
                        label = { Text(stringResource(R.string.wifi_server_ip)) },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        enabled = !uiState.wifiSending,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.wifiPort,
                        onValueChange = { viewModel.setWifiPort(it) },
                        label = { Text(stringResource(R.string.wifi_port)) },
                        singleLine = true,
                        enabled = !uiState.wifiSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // #3 — test the connection before sending
                    OutlinedButton(
                        onClick = { viewModel.testWifi() },
                        enabled = !uiState.testing && !uiState.wifiSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_testing))
                        } else {
                            Text(stringResource(R.string.wifi_test))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showWifiDialog = false; viewModel.shareViaWifi() },
                    enabled = !uiState.wifiSending && exportCount > 0
                ) { Text(stringResource(R.string.wifi_send)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWifiDialog = false },
                    enabled = !uiState.wifiSending
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ── "Waiting for PC" spinner (while sending and no decision pending) ─────
    if (uiState.wifiSending && uiState.wifiDecision == null) {
        val stageText = when (uiState.wifiStage) {
            "connecting" -> stringResource(R.string.wifi_stage_connecting)
            "checking"   -> stringResource(R.string.wifi_stage_checking)
            "printing"   -> stringResource(R.string.wifi_stage_printing)
            else         -> stringResource(R.string.wifi_stage_checking)
        }
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
            title = { Text(stringResource(R.string.wifi_working_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(stageText)
                }
            }
        )
    }

    // ── On-phone decision dialog (PC is waiting for the user's choice) ───────
    uiState.wifiDecision?.let { req ->
        val rowLabel = stringResource(R.string.wifi_row_label)
        val failedText = req.failed.joinToString("\n") { f ->
            "• $rowLabel ${f.row} — ${f.pos} — ${f.reason}"
        }
        val title: String
        val confirmLabel: String
        val confirmChoice: String
        val dismissLabel: String
        val dismissChoice: String
        when (val kind = req.kind) {
            is WifiDecisionKind.PrintOrCancel -> {
                title = stringResource(R.string.wifi_some_failed_title)
                confirmLabel = stringResource(R.string.wifi_print_ready_format, kind.readyCount)
                confirmChoice = "print"
                dismissLabel = stringResource(R.string.cancel)
                dismissChoice = "cancel"
            }
            is WifiDecisionKind.RetryLeft -> {
                title = stringResource(R.string.wifi_printed_title_format, kind.printedCount)
                confirmLabel = stringResource(R.string.wifi_retry_yes)
                confirmChoice = "retry"
                dismissLabel = stringResource(R.string.wifi_retry_no)
                dismissChoice = "no"
            }
            is WifiDecisionKind.ReprintSheets -> {
                title = stringResource(R.string.wifi_reprint_title_format, kind.failedSheets)
                confirmLabel = stringResource(R.string.wifi_reprint_yes)
                confirmChoice = "reprint"
                dismissLabel = stringResource(R.string.wifi_reprint_skip)
                dismissChoice = "skip"
            }
            WifiDecisionKind.RetryOrCancel -> {
                title = stringResource(R.string.wifi_none_printable_title)
                confirmLabel = stringResource(R.string.wifi_retry)
                confirmChoice = "retry"
                dismissLabel = stringResource(R.string.cancel)
                dismissChoice = "cancel"
            }
        }
        AlertDialog(
            onDismissRequest = { viewModel.submitWifiDecision(dismissChoice) },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
            title = { Text(title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (val kind = req.kind) {
                        is WifiDecisionKind.RetryLeft ->
                            Text(stringResource(R.string.wifi_retry_prompt, req.failed.size))
                        is WifiDecisionKind.ReprintSheets ->
                            Text(stringResource(R.string.wifi_reprint_prompt, kind.failedSheets, kind.printedCount))
                        is WifiDecisionKind.PrintOrCancel ->
                            if (kind.readyCount > 0) Text(stringResource(R.string.wifi_ready_count, kind.readyCount))
                        WifiDecisionKind.RetryOrCancel -> {}
                    }
                    if (failedText.isNotBlank() && req.kind !is WifiDecisionKind.ReprintSheets) {
                        Text(failedText, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitWifiDecision(confirmChoice) }) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.submitWifiDecision(dismissChoice) }) { Text(dismissLabel) }
            }
        )
    }

    // ── Share WiFi result → snackbar ─────────────────────────────────────────
    uiState.wifiInfo?.let { info ->
        val msg = when {
            info == "INVALID_INPUT" -> stringResource(R.string.wifi_invalid_input)
            info == "NO_PCS" -> stringResource(R.string.wifi_no_pcs)
            info.startsWith("TEST_OK:") ->
                stringResource(R.string.wifi_test_ok_format, info.removePrefix("TEST_OK:"))
            info.startsWith("TEST_FAIL:") ->
                stringResource(R.string.wifi_test_fail_format, info.removePrefix("TEST_FAIL:"))
            info == "EMPTY" -> stringResource(R.string.wifi_empty)
            info == "BUSY" -> stringResource(R.string.wifi_busy)
            info == "CANCELLED" -> stringResource(R.string.wifi_cancelled)
            info == "DONE_CLOSED" -> stringResource(R.string.wifi_done)
            info.startsWith("PRINTED_DONE:") ->
                stringResource(R.string.wifi_printed_rest_cancelled_format,
                    info.removePrefix("PRINTED_DONE:").toIntOrNull() ?: 0)
            info.startsWith("PRINTED:") ->
                stringResource(R.string.wifi_printed_format,
                    info.removePrefix("PRINTED:").toIntOrNull() ?: 0)
            info.startsWith("FAILED:") && info.contains("connect", true) ||
                info.startsWith("FAILED:") && info.contains("refused", true) ||
                info.startsWith("FAILED:") && info.contains("reach", true) ||
                info.startsWith("FAILED:") && info.contains("timed out", true) ||
                info.startsWith("FAILED:") && info.contains("ETIMEDOUT", true) ->
                stringResource(R.string.wifi_unreachable)
            info.startsWith("FAILED:") ->
                stringResource(R.string.wifi_failed_format, info.removePrefix("FAILED:"))
            else -> info
        }
        LaunchedEffect(info) {
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.consumeWifiInfo()
            }
        }
    }
}
