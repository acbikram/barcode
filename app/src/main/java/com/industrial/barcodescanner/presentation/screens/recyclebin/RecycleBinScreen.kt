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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
                title = { Text("Recycle Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                        Text("Recover deleted records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Deleted scans stay here until you permanently remove them. Restored records return to History, Export, and the dashboard.",
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
                                    Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                                }
                                Text(
                                    if (uiState.selectionCount == 0) "${uiState.items.size} deleted record(s)"
                                    else "${uiState.selectionCount} selected",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (uiState.selectionCount > 0) {
                                GlassActionButton(
                                    label = "Restore selected",
                                    supportingText = "Return selected records to active history",
                                    icon = Icons.Default.RestoreFromTrash,
                                    tone = GlassActionTone.Success,
                                    onClick = viewModel::restoreSelected
                                )
                                GlassActionButton(
                                    label = "Delete selected permanently",
                                    supportingText = "This action cannot be undone",
                                    icon = Icons.Default.DeleteForever,
                                    tone = GlassActionTone.Destructive,
                                    onClick = viewModel::permanentlyDeleteSelected
                                )
                            } else {
                                GlassActionButton(
                                    label = "Empty recycle bin",
                                    supportingText = "Permanently remove all deleted records",
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
                            Text("Recycle bin is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Deleted barcode records can be restored here.", style = MaterialTheme.typography.bodySmall, color = SubtleGray)
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
            title = { Text("Empty recycle bin?") },
            text = { Text("All deleted barcode records will be permanently removed. This cannot be undone.") },
            confirmButton = {
                GlassActionButton(
                    label = "Empty permanently",
                    tone = GlassActionTone.Destructive,
                    onClick = viewModel::confirmEmpty
                )
            },
            dismissButton = {
                GlassActionButton(
                    label = "Cancel",
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
                Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete permanently", tint = ErrorRed)
            }
        }
    }
}
