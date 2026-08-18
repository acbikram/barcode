package com.industrial.barcodescanner.presentation.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
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
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import com.industrial.barcodescanner.presentation.theme.AppDimens
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
    initialFilter: String = "ALL",
    initialSort: String = "NEWEST"
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showFilterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteFilteredDialog by remember { mutableStateOf(false) }

    // Apply the filter/sort passed from the dashboard on first composition
    LaunchedEffect(initialFilter, initialSort) {
        viewModel.applyInitialFilterAndSort(initialFilter, initialSort)
    }

    val sortLabel = when (uiState.sortOrder) {
        SortOrder.NEWEST -> stringResource(R.string.sort_newest)
        SortOrder.OLDEST -> stringResource(R.string.sort_oldest)
        SortOrder.COPIES -> stringResource(R.string.sort_copies)
    }

    val filterLabel = filterDisplayLabel(uiState.filter)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (uiState.selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.items_selected_format, uiState.selectedIds.size),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = CyanAccent)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.selectAllVisible() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all), tint = CyanAccent)
                        }
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = uiState.selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_selected),
                                tint = if (uiState.selectedIds.isNotEmpty()) ErrorRed else SubtleGray
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.history),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort), tint = CyanAccent)
                        }
                        Box {
                            FilterChip(
                                selected = uiState.filter != FILTER_ALL_KEY,
                                onClick = { showFilterMenu = true },
                                label = { Text(filterLabel, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = CyanAccent
                                )
                            )
                            DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_all)) },
                                    onClick = { viewModel.setFilter(FILTER_ALL_KEY); showFilterMenu = false }
                                )
                                HorizontalDivider()
                                TAG_TYPES.forEach { tagType ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tag_type_format, tagType)) },
                                        onClick = { viewModel.setFilter("TAG_$tagType"); showFilterMenu = false }
                                    )
                                }
                                HorizontalDivider()
                                UNIT_TYPES.forEach { unitType ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.unit_type_format, unitType)) },
                                        onClick = { viewModel.setFilter("UNIT_$unitType"); showFilterMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sort), tint = CyanAccent)
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_items)) },
                                    onClick = {
                                        viewModel.enterSelectionMode()
                                        showOverflowMenu = false
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete_filtered_format, viewModel.countForCurrentFilter()),
                                            color = ErrorRed
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteFilteredDialog = true
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                )
            }
        },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_by_name_or_barcode)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenPadding, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
            // Sort indicator
            Text(
                text = stringResource(
                    R.string.sort_items_summary_format,
                    sortLabel,
                    uiState.filteredItems.size
                ),
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                modifier = Modifier.padding(horizontal = AppDimens.ScreenPadding, vertical = 2.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppDimens.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ItemGap)
            ) {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        selectionMode = uiState.selectionMode,
                        selected = uiState.selectedIds.contains(item.id),
                        onClick = {
                            if (uiState.selectionMode) {
                                viewModel.toggleItemSelected(item.id)
                            } else {
                                navController.navigate(Screen.Detail.passId(item.id))
                            }
                        },
                        onLongClick = {
                            if (!uiState.selectionMode) viewModel.enterSelectionMode()
                            viewModel.toggleItemSelected(item.id)
                        }
                    )
                }
                if (uiState.filteredItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_items_found),
                                style = MaterialTheme.typography.bodyLarge.copy(color = SubtleGray)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete_selected)) },
            text = { Text(stringResource(R.string.delete_selected_confirm_format, uiState.selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteSelectedDialog = false
                }) { Text(stringResource(R.string.delete), color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDeleteFilteredDialog) {
        val count = viewModel.countForCurrentFilter()
        AlertDialog(
            onDismissRequest = { showDeleteFilteredDialog = false },
            title = { Text(stringResource(R.string.delete_filtered_format, count)) },
            text = { Text(stringResource(R.string.delete_filtered_confirm_format, count)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteByCurrentFilter()
                    showDeleteFilteredDialog = false
                }) { Text(stringResource(R.string.delete), color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFilteredDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!)
                viewModel.clearError()
            }
        }
    }
}

@Composable
private fun filterDisplayLabel(filter: String): String {
    return when {
        filter == FILTER_ALL_KEY -> stringResource(R.string.filter_all)
        filter.startsWith("TAG_") -> stringResource(R.string.tag_type_format, filter.removePrefix("TAG_"))
        filter.startsWith("UNIT_") -> stringResource(R.string.unit_type_format, filter.removePrefix("UNIT_"))
        else -> filter
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    item: ScannedItem,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (selected) CyanAccent.copy(alpha = 0.74f) else CyanAccent.copy(alpha = 0.30f),
                MaterialTheme.shapes.medium
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) CyanAccent.copy(alpha = 0.12f) else SurfaceDark
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                )
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                if (item.itemCode != null) {
                    Text(
                        text = stringResource(R.string.item_code_format, item.itemCode),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                if (item.productName != null || item.itemCode != null) {
                    Text(
                        text = stringResource(R.string.barcode_format, item.barcode),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryInfoChip(stringResource(R.string.tag_type_format, item.tagType), GreenAccent)
                    HistoryInfoChip(stringResource(R.string.unit_type_format, item.unitType), OrangeAccent)
                    HistoryInfoChip(stringResource(R.string.copies_format, item.copies), CyanAccent)
                }
                Text(
                    text = stringResource(R.string.scanned_format, formatTimestamp(item.createdAt)),
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
        }
    }
}

@Composable
private fun HistoryInfoChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(color = color),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
