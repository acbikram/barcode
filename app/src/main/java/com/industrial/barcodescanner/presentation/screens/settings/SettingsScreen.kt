package com.industrial.barcodescanner.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.presentation.theme.SurfaceVariant
import com.industrial.barcodescanner.utils.LanguageManager
import com.industrial.barcodescanner.presentation.navigation.Screen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val importSuccessMsg = if (uiState.catalogItemCount > 0)
        stringResource(R.string.catalog_import_success_format, uiState.catalogItemCount)
    else stringResource(R.string.catalog_import_success_format, 0)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.importCatalogDb(uri)
    }

    LaunchedEffect(uiState.catalogImportState) {
        when (uiState.catalogImportState) {
            SettingsViewModel.CatalogImportState.SUCCESS -> {
                scope.launch { snackbarHostState.showSnackbar(importSuccessMsg) }
                viewModel.resetCatalogImportState()
            }
            SettingsViewModel.CatalogImportState.ERROR -> {
                scope.launch { snackbarHostState.showSnackbar(uiState.catalogError ?: "Import failed") }
                viewModel.resetCatalogImportState()
            }
            else -> {}
        }
    }

    // ── Language selection ────────────────────────────────────────────────────
    var currentLanguage by remember { mutableStateOf(LanguageManager.getCurrentLanguage()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {

            // ── Language ───────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.language),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.language_description),
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                        Spacer(Modifier.height(12.dp))

                        LanguageOptionRow(
                            label = stringResource(R.string.language_system_default),
                            selected = currentLanguage == LanguageManager.AppLanguage.SYSTEM_DEFAULT,
                            onClick = {
                                currentLanguage = LanguageManager.AppLanguage.SYSTEM_DEFAULT
                                LanguageManager.setLanguage(LanguageManager.AppLanguage.SYSTEM_DEFAULT)
                            }
                        )
                        LanguageOptionRow(
                            label = stringResource(R.string.language_english),
                            selected = currentLanguage == LanguageManager.AppLanguage.ENGLISH,
                            onClick = {
                                currentLanguage = LanguageManager.AppLanguage.ENGLISH
                                LanguageManager.setLanguage(LanguageManager.AppLanguage.ENGLISH)
                            }
                        )
                        LanguageOptionRow(
                            label = stringResource(R.string.language_arabic),
                            selected = currentLanguage == LanguageManager.AppLanguage.ARABIC,
                            onClick = {
                                currentLanguage = LanguageManager.AppLanguage.ARABIC
                                LanguageManager.setLanguage(LanguageManager.AppLanguage.ARABIC)
                            }
                        )
                    }
                }
            }

            // ── Scan Settings ──────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.scan_settings),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.beep_on_scan),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    stringResource(R.string.beep_on_scan_subtitle),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Switch(
                                checked = uiState.scanSound,
                                onCheckedChange = viewModel::toggleScanSound,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GreenAccent,
                                    checkedTrackColor = GreenAccent.copy(alpha = 0.4f)
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.vibrate_on_scan),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    stringResource(R.string.vibrate_on_scan_subtitle),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Switch(
                                checked = uiState.vibration,
                                onCheckedChange = viewModel::toggleVibration,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GreenAccent,
                                    checkedTrackColor = GreenAccent.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }
            }

            // ── Product Catalog ────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.catalog_update),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent, fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(4.dp))

                        if (uiState.catalogItemCount > 0) {
                            Text(
                                stringResource(R.string.catalog_last_updated_format, uiState.catalogLastModified),
                                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                            )
                            Text(
                                stringResource(R.string.catalog_item_count_format, uiState.catalogItemCount),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GreenAccent, fontWeight = FontWeight.SemiBold
                                )
                            )
                        } else {
                            Text(
                                stringResource(R.string.catalog_empty_hint),
                                style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── WiFi pull button / progress ────────────────────────
                        when (uiState.wifiCatalogState) {
                            SettingsViewModel.WifiCatalogState.IDLE,
                            SettingsViewModel.WifiCatalogState.ERROR -> {
                                Button(
                                    onClick = { viewModel.pullCatalogFromPc() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanAccent.copy(alpha = 0.15f),
                                        contentColor = CyanAccent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.catalog_wifi_pull_button))
                                }
                                if (uiState.wifiCatalogState == SettingsViewModel.WifiCatalogState.ERROR) {
                                    Text(
                                        uiState.wifiCatalogStatus,
                                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            SettingsViewModel.WifiCatalogState.DISCOVERING -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanAccent)
                                    Text(stringResource(R.string.catalog_wifi_discovering), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                                }
                            }

                            SettingsViewModel.WifiCatalogState.DOWNLOADING -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanAccent)
                                        Text(uiState.wifiCatalogStatus, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                                    }
                                    LinearProgressIndicator(
                                        progress = { uiState.wifiCatalogProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = CyanAccent,
                                        trackColor = CyanAccent.copy(alpha = 0.15f)
                                    )
                                    Text(
                                        "${(uiState.wifiCatalogProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }

                            SettingsViewModel.WifiCatalogState.SUCCESS -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GreenAccent.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        uiState.wifiCatalogStatus,
                                        style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent, fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { viewModel.resetWifiCatalogState() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                    Text(stringResource(R.string.catalog_wifi_pull_button))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.catalog_description),
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                        Spacer(Modifier.height(8.dp))

                        // ── Manual file import ────────────────────────────────────
                        Button(
                            onClick = { importLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.catalogImportState != SettingsViewModel.CatalogImportState.IMPORTING,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariant,
                                contentColor = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (uiState.catalogImportState == SettingsViewModel.CatalogImportState.IMPORTING) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanAccent)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.catalog_importing))
                            } else {
                                Text(stringResource(R.string.catalog_import_button))
                            }
                        }
                    }
                }
            }

            // ── Data Management ────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.data_management),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { navController.navigate(Screen.BackupRestore.route) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariant,
                                contentColor   = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.backup_restore)) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorRed.copy(alpha = 0.15f),
                                contentColor   = ErrorRed
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.clear_all_records)) }
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
                TextButton(onClick = {
                    viewModel.clearAllRecords()
                    showClearDialog = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = CyanAccent,
                unselectedColor = SubtleGray
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
