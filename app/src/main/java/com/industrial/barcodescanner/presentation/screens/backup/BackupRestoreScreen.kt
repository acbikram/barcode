package com.industrial.barcodescanner.presentation.screens.backup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.presentation.components.GlassActionButton
import com.industrial.barcodescanner.presentation.components.GlassActionTone
import com.industrial.barcodescanner.presentation.components.GlassSectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val operationSuccessMsg = stringResource(R.string.operation_successful)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backupToUri(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromUri(context, uri)
        }
    }

    val catalogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCatalogFromUri(context, uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.backup_restore)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Protect your scans with a portable backup, or restore a previous working set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GlassActionButton(
                            label = stringResource(R.string.backup_database),
                            supportingText = "Save active barcode records as JSON",
                            tone = GlassActionTone.Success,
                            enabled = !uiState.isLoading,
                            onClick = { backupLauncher.launch("BarcodeToCsv_backup_${System.currentTimeMillis()}.json") }
                        )
                        GlassActionButton(
                            label = stringResource(R.string.restore_database),
                            supportingText = "Replace active records; previous records remain recoverable",
                            tone = GlassActionTone.Warning,
                            enabled = !uiState.isLoading,
                            onClick = { restoreLauncher.launch(arrayOf("application/json")) }
                        )
                    }
                }

                GlassSectionCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.update_catalog), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.update_catalog_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GlassActionButton(
                            label = "Import catalog file",
                            supportingText = "Choose a CSV or SQLite product catalog",
                            tone = GlassActionTone.Neutral,
                            enabled = !uiState.isLoading,
                            onClick = { catalogLauncher.launch(arrayOf("*/*")) }
                        )
                        GlassActionButton(
                            label = stringResource(R.string.update_catalog_wifi),
                            supportingText = "Discover a PC and receive its current catalog",
                            tone = GlassActionTone.Neutral,
                            enabled = !uiState.isLoading,
                            onClick = viewModel::pullCatalogFromPc
                        )
                    }
                }
            }

            if (uiState.isLoading) {
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
                            Text(stringResource(R.string.processing), style = MaterialTheme.typography.bodyLarge)
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
        val successMsg = uiState.catalogCount?.let {
            stringResource(R.string.catalog_import_done, it)
        } ?: operationSuccessMsg
        LaunchedEffect(Unit) {
            scope.launch {
                snackbarHostState.showSnackbar(successMsg)
                viewModel.resetSuccess()
            }
        }
    }
}
