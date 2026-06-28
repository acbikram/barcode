package com.industrial.barcodescanner.presentation.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.presentation.theme.SurfaceVariant
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Filter keys used when navigating to History with a pre-set filter
const val FILTER_ALL = "ALL"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // ── App header ──────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_title_header),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = CyanAccent
                    )
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.developed_by))
                        withStyle(SpanStyle(color = OrangeAccent, fontWeight = FontWeight.SemiBold)) {
                            append("Bikram Acharya")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = SubtleGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Empty catalog warning ─────────────────────────────────────────
            if (uiState.catalogEmpty) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OrangeAccent.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠", style = MaterialTheme.typography.bodyLarge.copy(color = OrangeAccent))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("No product catalog", style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent, fontWeight = FontWeight.Bold))
                            Text("Barcodes won't resolve — load a catalog in Settings.", style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                        }
                        TextButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Text(stringResource(R.string.settings), color = OrangeAccent)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Dashboard card ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Totals row ─────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ClickableStatCard(
                            label = stringResource(R.string.total_records),
                            value = uiState.totalRecords,
                            onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=NEWEST") }
                        )
                        ClickableStatCard(
                            label = stringResource(R.string.total_copies),
                            value = uiState.totalCopies,
                            accentColor = OrangeAccent,
                            onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=COPIES") }
                        )
                        if (uiState.wifiPagesToday > 0) {
                            ClickableStatCard(
                                label = "WiFi Today",
                                value = uiState.wifiPagesToday,
                                accentColor = GreenAccent,
                                width = 90.dp,
                                onClick = { navController.navigate(Screen.Export.route) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Tag Type breakdown ────────────────────────────────────
                    Text(
                        text = stringResource(R.string.tag_type_breakdown),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = GreenAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TAG_TYPES.forEach { tagType ->
                            ClickableStatCard(
                                label = tagType,
                                value = uiState.tagTypeCounts[tagType] ?: 0,
                                accentColor = GreenAccent,
                                width = 96.dp,
                                onClick = {
                                    navController.navigate("${Screen.History.BASE}?filter=TAG_$tagType&sort=NEWEST")
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Unit Type breakdown ───────────────────────────────────
                    Text(
                        text = stringResource(R.string.unit_type_breakdown),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = OrangeAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UNIT_TYPES.forEach { unitType ->
                            ClickableStatCard(
                                label = unitType,
                                value = uiState.unitTypeCounts[unitType] ?: 0,
                                accentColor = OrangeAccent,
                                width = 78.dp,
                                onClick = {
                                    navController.navigate("${Screen.History.BASE}?filter=UNIT_$unitType&sort=NEWEST")
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Recent scans section header (fixed, doesn't scroll) ──────────
            Text(
                text = stringResource(R.string.recent_scans),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Recent scans list — only this part scrolls ───────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.recentItems, key = { it.id }) { item ->
                    RecentItemCard(
                        item = item,
                        onClick = { navController.navigate(Screen.Detail.passId(item.id)) }
                    )
                }
                if (uiState.recentItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_recent_items), style = MaterialTheme.typography.bodyMedium)
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
}

@Composable
fun ClickableStatCard(
    label: String,
    value: Int,
    accentColor: Color = CyanAccent,
    width: androidx.compose.ui.unit.Dp = 140.dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                maxLines = 2
            )
        }
    }
}

@Composable
fun RecentItemCard(item: ScannedItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.tag_type_format, item.tagType),
                    style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent)
                )
                Text(
                    text = stringResource(R.string.unit_type_format, item.unitType),
                    style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent)
                )
                Text(
                    text = stringResource(R.string.copies_format, item.copies),
                    style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent)
                )
            }
            Text(
                text = stringResource(R.string.scanned_format, formatTimestamp(item.createdAt)),
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
