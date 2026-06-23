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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
}
