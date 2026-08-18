package com.industrial.barcodescanner.presentation.screens.recyclebin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.components.GlassActionButton
import com.industrial.barcodescanner.presentation.components.GlassActionTone
import com.industrial.barcodescanner.presentation.components.GlassSectionCard
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    navController: NavController,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(lifecycleOwner = lifecycleOwner)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recycle_bin), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                GlassSectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.recover_deleted_records), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.recycle_bin_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleGray
                        )
                    }
                }
            }

            if (uiState.items.isNotEmpty()) {
                item {
                    GlassSectionCard {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = viewModel::toggleSelectAll) {
                                    Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                                }
                                Text(
                                    if (uiState.selectionCount == 0) stringResource(R.string.deleted_records_count, uiState.items.size)
                                    else stringResource(R.string.selected_count, uiState.selectionCount),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (uiState.selectionCount > 0) {
                                GlassActionButton(
                                    label = stringResource(R.string.restore_selected),
                                    supportingText = stringResource(R.string.restore_selected_supporting),
                                    icon = Icons.Default.RestoreFromTrash,
                                    tone = GlassActionTone.Success,
                                    onClick = viewModel::restoreSelected
                                )
                                GlassActionButton(
                                    label = stringResource(R.string.delete_selected_permanently),
                                    supportingText = stringResource(R.string.delete_permanently_irreversible),
                                    icon = Icons.Default.DeleteForever,
                                    tone = GlassActionTone.Destructive,
                                    onClick = viewModel::permanentlyDeleteSelected
                                )
                            } else {
                                GlassActionButton(
                                    label = stringResource(R.string.empty_recycle_bin),
                                    supportingText = stringResource(R.string.empty_recycle_bin_supporting),
                                    icon = Icons.Default.DeleteForever,
                                    tone = GlassActionTone.Destructive,
                                    onClick = viewModel::requestEmpty
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.items.isEmpty()) {
                item {
                    GlassSectionCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = SubtleGray
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.recycle_bin_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.recycle_bin_empty_description), style = MaterialTheme.typography.bodySmall, color = SubtleGray)
                        }
                    }
                }
            } else {
                items(uiState.items, key = { it.id }) { item ->
                    RecycleBinItemRow(
                        item = item,
                        selected = item.id in uiState.selectedIds,
                        onToggleSelection = { viewModel.toggleSelection(item.id) },
                        onRestore = { viewModel.restoreItem(item.id) },
                        onDeleteForever = { viewModel.permanentlyDeleteItem(item.id) }
                    )
                }
            }
        }
    }

    if (uiState.showEmptyConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEmptyConfirmation,
            title = { Text(stringResource(R.string.empty_recycle_bin_question)) },
            text = { Text(stringResource(R.string.empty_recycle_bin_confirmation)) },
            confirmButton = {
                GlassActionButton(
                    label = stringResource(R.string.empty_permanently),
                    tone = GlassActionTone.Destructive,
                    onClick = viewModel::confirmEmpty
                )
            },
            dismissButton = {
                GlassActionButton(
                    label = stringResource(R.string.cancel),
                    tone = GlassActionTone.Neutral,
                    onClick = viewModel::dismissEmptyConfirmation
                )
            }
        )
    }
}

@Composable
private fun RecycleBinItemRow(
    item: ScannedItem,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    GlassSectionCard(selected = selected) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleSelection)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.barcode} • ${item.tagType} • ${item.unitType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.deletedAt?.let { deletedAt ->
                    Text(
                        "Deleted ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(deletedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SubtleGray
                    )
                }
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.restore), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_permanently), tint = ErrorRed)
            }
        }
    }
}
