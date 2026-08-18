package com.industrial.barcodescanner.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.components.GlassActionButton
import com.industrial.barcodescanner.presentation.components.GlassActionTone
import com.industrial.barcodescanner.presentation.components.GlassSectionCard
import com.industrial.barcodescanner.presentation.components.GlassSelectableOption
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.theme.AppDimens
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.presentation.theme.SurfaceVariant
import com.industrial.barcodescanner.utils.LanguageManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(LanguageManager.getCurrentLanguage()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importCatalogFromUri(uri)
    }

    // Show snackbar when import finishes
    LaunchedEffect(uiState.catalogImportResult) {
        val result = uiState.catalogImportResult ?: return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(result)
            viewModel.clearCatalogImportResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
            contentPadding = PaddingValues(vertical = AppDimens.CompactScreenPadding)
        ) {
            // ── Appearance ──────────────────────────────────────────────────
            item {
                SettingsCard(
                    title = stringResource(R.string.appearance),
                    collapsedSummary = stringResource(
                        R.string.theme_summary_format,
                        when (uiState.themeMode) {
                            "light" -> stringResource(R.string.theme_light)
                            "system" -> stringResource(R.string.theme_system)
                            else -> stringResource(R.string.theme_dark)
                        }
                    )
                ) {
                    Text(
                        "Choose the interface appearance that is most comfortable for your environment.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                    Spacer(Modifier.height(12.dp))
                    AppearanceOptionRow(
                        title = stringResource(R.string.theme_dark),
                        subtitle = stringResource(R.string.theme_dark_settings_subtitle),
                        selected = uiState.themeMode == "dark",
                        onClick = { viewModel.setThemeMode("dark") }
                    )
                    AppearanceOptionRow(
                        title = stringResource(R.string.theme_light),
                        subtitle = stringResource(R.string.theme_light_settings_subtitle),
                        selected = uiState.themeMode == "light",
                        onClick = { viewModel.setThemeMode("light") }
                    )
                    AppearanceOptionRow(
                        title = stringResource(R.string.theme_system),
                        subtitle = stringResource(R.string.theme_system_settings_subtitle),
                        selected = uiState.themeMode == "system",
                        onClick = { viewModel.setThemeMode("system") }
                    )
                }
            }

            // ── Language ────────────────────────────────────────────────────
            item {
                SettingsCard(
                    title = stringResource(R.string.language),
                    collapsedSummary = stringResource(
                        R.string.language_current_format,
                        when (currentLanguage) {
                            LanguageManager.AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                            LanguageManager.AppLanguage.ARABIC -> stringResource(R.string.language_arabic)
                            else -> stringResource(R.string.language_system_default)
                        }
                    )
                ) {
                    Text(stringResource(R.string.language_description), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        LanguageManager.AppLanguage.SYSTEM_DEFAULT to stringResource(R.string.language_system_default),
                        LanguageManager.AppLanguage.ENGLISH to stringResource(R.string.language_english),
                        LanguageManager.AppLanguage.ARABIC to stringResource(R.string.language_arabic)
                    ).forEach { (lang, label) ->
                        LanguageOptionRow(label = label, selected = currentLanguage == lang) {
                            currentLanguage = lang
                            LanguageManager.setLanguage(lang)
                        }
                    }
                }
            }

            // ── Scan Settings ───────────────────────────────────────────────
            item {
                SettingsCard(
                    title = stringResource(R.string.scan_settings),
                    collapsedSummary = stringResource(R.string.settings_tap_to_expand)
                ) {
                    SwitchRow(
                        title = stringResource(R.string.beep_on_scan),
                        subtitle = stringResource(R.string.beep_on_scan_subtitle),
                        checked = uiState.scanSound,
                        onCheckedChange = viewModel::toggleScanSound
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = stringResource(R.string.vibrate_on_scan),
                        subtitle = stringResource(R.string.vibrate_on_scan_subtitle),
                        checked = uiState.vibration,
                        onCheckedChange = viewModel::toggleVibration
                    )
                }
            }

            // ── Product Catalog ─────────────────────────────────────────────
            item {
                SettingsCard(
                    title = stringResource(R.string.update_catalog),
                    collapsedSummary = stringResource(R.string.settings_tap_to_expand)
                ) {
                    // Catalog info
                    if (uiState.catalogCount > 0) {
                        Text(
                            "${uiState.catalogCount} products  •  ${uiState.catalogLastUpdated}",
                            style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent, fontWeight = FontWeight.SemiBold)
                        )
                    } else {
                        Text(
                            uiState.catalogLastUpdated,
                            style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                        )
                    }
                    Text(
                        stringResource(R.string.update_catalog_hint),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                    Spacer(Modifier.height(12.dp))

                    // WiFi pull
                    when (uiState.wifiCatalogState) {
                        SettingsViewModel.WifiCatalogState.IDLE,
                        SettingsViewModel.WifiCatalogState.ERROR -> {
                            Button(
                                onClick = { viewModel.pullCatalogFromPc() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent.copy(alpha = 0.15f), contentColor = CyanAccent),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.update_catalog_wifi))
                            }
                            if (uiState.wifiCatalogState == SettingsViewModel.WifiCatalogState.ERROR) {
                                Text(uiState.wifiCatalogStatus, style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed), modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        SettingsViewModel.WifiCatalogState.DISCOVERING -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanAccent)
                                Text(uiState.wifiCatalogStatus, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
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
                                    modifier = Modifier.fillMaxWidth(), color = CyanAccent, trackColor = CyanAccent.copy(alpha = 0.15f)
                                )
                            }
                        }
                        SettingsViewModel.WifiCatalogState.SUCCESS -> {
                            Card(colors = CardDefaults.cardColors(containerColor = GreenAccent.copy(alpha = 0.12f)), shape = MaterialTheme.shapes.small) {
                                Text(uiState.wifiCatalogStatus, style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent, fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth().padding(12.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = { viewModel.resetWifiCatalogState() }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                                Text(stringResource(R.string.update_catalog_wifi))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))

                    // File import
                    if (uiState.catalogImporting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanAccent)
                            Text(stringResource(R.string.importing), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { fileLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        ) { Text(stringResource(R.string.import_catalog_file)) }
                    }
                }
            }

            // ── App Updates ─────────────────────────────────────────────────
            item {
                val currentVersion = remember {
                    try {
                        val info = context.packageManager.getPackageInfo(context.packageName, 0)
                        val code = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toInt()
                        "v${info.versionName} ($code)"
                    } catch (_: Exception) { "Unknown" }
                }

                SettingsCard(
                    title = stringResource(R.string.update_app_title),
                    collapsedSummary = stringResource(R.string.settings_tap_to_expand)
                ) {
                    Text(
                        stringResource(R.string.update_current_version_format, currentVersion),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                    Spacer(Modifier.height(12.dp))

                    when (uiState.updateState) {
                        SettingsViewModel.UpdateState.IDLE -> {
                            Button(
                                onClick = { viewModel.checkForUpdate() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small
                            ) { Text(stringResource(R.string.update_check_button)) }
                        }

                        SettingsViewModel.UpdateState.CHECKING -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanAccent)
                                Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                            }
                        }

                        SettingsViewModel.UpdateState.UP_TO_DATE -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = GreenAccent.copy(alpha = 0.12f)),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    stringResource(R.string.update_up_to_date),
                                    style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent, fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = { viewModel.resetUpdateState() }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                                Text(stringResource(R.string.update_check_button))
                            }
                        }

                        SettingsViewModel.UpdateState.AVAILABLE -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyanAccent.copy(alpha = 0.12f)),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    stringResource(R.string.update_available_format, uiState.updateVersionName),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = MaterialTheme.shapes.small
                            ) { Text(stringResource(R.string.update_now_button), color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold) }
                        }

                        SettingsViewModel.UpdateState.DOWNLOADING -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyanAccent)
                                    Text(
                                        stringResource(R.string.update_downloading_format, uiState.updateDownloadProgress),
                                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { uiState.updateDownloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = CyanAccent,
                                    trackColor = CyanAccent.copy(alpha = 0.15f)
                                )
                            }
                        }

                        SettingsViewModel.UpdateState.ERROR -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(uiState.updateError, style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed), modifier = Modifier.fillMaxWidth().padding(12.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                                Text(stringResource(R.string.update_check_button))
                            }
                        }
                    }
                }
            }

            // ── Data Management ─────────────────────────────────────────────
            item {
                SettingsCard(
                    title = stringResource(R.string.data_management),
                    collapsedSummary = stringResource(R.string.settings_tap_to_expand)
                ) {
                    GlassActionButton(
                        label = stringResource(R.string.backup_restore),
                        supportingText = stringResource(R.string.backup_restore_supporting),
                        tone = GlassActionTone.Neutral,
                        onClick = { navController.navigate(Screen.BackupRestore.route) }
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.recycle_bin),
                        supportingText = stringResource(R.string.recycle_bin_supporting),
                        tone = GlassActionTone.Neutral,
                        onClick = { navController.navigate(Screen.RecycleBin.route) }
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.clear_all_records),
                        supportingText = stringResource(R.string.clear_all_records_supporting),
                        tone = GlassActionTone.Destructive,
                        onClick = { showClearDialog = true }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_records)) },
            text = { Text(stringResource(R.string.clear_all_records_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.clearAllRecords(); showClearDialog = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    collapsedSummary: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    GlassSectionCard(modifier = Modifier.fillMaxWidth(), selected = expanded) {
        Column(modifier = Modifier.padding(AppDimens.CardPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                    if (!expanded) {
                        Text(
                            collapsedSummary ?: stringResource(R.string.settings_tap_to_expand),
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = CyanAccent
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = GreenAccent, checkedTrackColor = GreenAccent.copy(alpha = 0.4f)))
    }
}

@Composable
private fun AppearanceOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    GlassSelectableOption(
        label = title,
        detail = subtitle,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassSelectableOption(label = label, selected = selected, onClick = onClick)
}
