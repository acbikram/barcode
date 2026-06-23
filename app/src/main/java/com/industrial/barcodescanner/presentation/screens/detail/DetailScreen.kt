package com.industrial.barcodescanner.presentation.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.industrial.barcodescanner.presentation.screens.detail.viewmodel.DetailViewModel
import com.industrial.barcodescanner.presentation.screens.scan.CopiesPickerDialog
import com.industrial.barcodescanner.presentation.screens.scan.TagTypePickerDialog
import com.industrial.barcodescanner.presentation.screens.scan.UnitTypePickerDialog
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.utils.LanguageManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    itemId: Long,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.item_details)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.item != null) {
                val item = uiState.item!!
                val isArabic = LanguageManager.isArabic()
                val productName = if (isArabic) {
                    item.productNameArabic?.takeIf { it.isNotBlank() } ?: item.productName
                } else {
                    item.productName?.takeIf { it.isNotBlank() } ?: item.productNameArabic
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!productName.isNullOrBlank() && productName != item.displayName) {
                            Text(productName, style = MaterialTheme.typography.titleMedium)
                        }
                        if (!item.itemCode.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.item_code_format, item.itemCode),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = stringResource(R.string.barcode_format, item.barcode),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    DetailFieldRow(
                        label = stringResource(R.string.tag_type_label),
                        value = uiState.tagType,
                        onClick = { viewModel.openTagPicker() }
                    )
                    DetailFieldRow(
                        label = stringResource(R.string.unit_type_label),
                        value = uiState.unitType,
                        onClick = { viewModel.openUnitPicker() }
                    )
                    DetailFieldRow(
                        label = stringResource(R.string.copies_label),
                        value = uiState.copies.toString(),
                        onClick = { viewModel.openCopiesPicker() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveChanges() },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.save_changes))
                        }
                        Button(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !uiState.isSaving
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }

                if (uiState.showTagPicker) {
                    TagTypePickerDialog(
                        initialTagType = uiState.tagType,
                        productName = productName,
                        barcode = item.barcode,
                        onConfirm = { viewModel.onTagTypeSelected(it) },
                        onDismiss = { viewModel.dismissPickers() }
                    )
                }
                if (uiState.showUnitPicker) {
                    UnitTypePickerDialog(
                        initialUnitType = uiState.unitType,
                        productName = productName,
                        barcode = item.barcode,
                        onConfirm = { viewModel.onUnitTypeSelected(it) },
                        onDismiss = { viewModel.dismissPickers() }
                    )
                }
                if (uiState.showCopiesPicker) {
                    CopiesPickerDialog(
                        initialCopies = uiState.copies,
                        productName = productName,
                        barcode = item.barcode,
                        onConfirm = { viewModel.onCopiesSelected(it) },
                        onDismiss = { viewModel.dismissPickers() }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { Text(stringResource(R.string.delete_item_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
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

    if (uiState.navigateBack) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
    }
}

@Composable
private fun DetailFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray))
                Text(value, style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
            }
        }
    }
}
