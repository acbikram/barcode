package com.industrial.barcodescanner.presentation.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.presentation.components.BottomNavigationBar
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import com.industrial.barcodescanner.presentation.theme.AppDimens
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

const val FILTER_ALL = "ALL"

private const val WHATSAPP_DEVELOPER_LINK =
    "https://wa.me/9779860874001?text=Hi%20Bikram,%20I%20reached%20you%20through%20the%20Barcode%20To%20CSV%20application%20can%20you%20respond%20me?"

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    // Explicitly use Compose UI's owner. This avoids the missing lifecycle
    // CompositionLocal that caused a fatal startup exception on Android 16.
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(lifecycleOwner = lifecycleOwner)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.Scan.route) },
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text(stringResource(R.string.scan)) },
                containerColor = GreenAccent,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = MaterialTheme.shapes.large
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AppDimens.ScreenPadding)
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.app_title_header),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = CyanAccent
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.developed_by),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            color = SubtleGray
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_DEVELOPER_LINK))
                                )
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = stringResource(R.string.whatsapp_contact_bikram),
                            tint = GreenAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Bikram Acharya",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                color = OrangeAccent
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.catalogEmpty) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OrangeAccent.copy(alpha = 0.12f)),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.38f))
                ) {
                    Row(
                        modifier = Modifier.padding(AppDimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = OrangeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "No product catalog",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = OrangeAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                "Barcodes will not resolve until a catalog is loaded in Settings.",
                                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                            )
                        }
                        TextButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Text(stringResource(R.string.settings), color = OrangeAccent)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = stringResource(R.string.dashboard),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanAccent.copy(alpha = 0.36f), MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(AppDimens.CardPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClickableStatCard(
                            label = stringResource(R.string.total_records),
                            value = uiState.totalRecords,
                            modifier = Modifier.weight(1f),
                            width = null,
                            onClick = {
                                navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=NEWEST")
                            }
                        )
                        ClickableStatCard(
                            label = stringResource(R.string.total_copies),
                            value = uiState.totalCopies,
                            accentColor = OrangeAccent,
                            modifier = Modifier.weight(1f),
                            width = null,
                            onClick = {
                                navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=COPIES")
                            }
                        )
                        if (uiState.wifiPagesToday > 0) {
                            ClickableStatCard(
                                label = "Wi‑Fi today",
                                value = uiState.wifiPagesToday,
                                accentColor = GreenAccent,
                                modifier = Modifier.weight(1f),
                                width = null,
                                onClick = { navController.navigate(Screen.Export.route) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardBreakdown(
                        title = stringResource(R.string.tag_type_breakdown),
                        titleColor = GreenAccent,
                        values = TAG_TYPES.map { it to (uiState.tagTypeCounts[it] ?: 0) },
                        accent = GreenAccent,
                        onItemClick = { tagType ->
                            navController.navigate("${Screen.History.BASE}?filter=TAG_$tagType&sort=NEWEST")
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardBreakdown(
                        title = stringResource(R.string.unit_type_breakdown),
                        titleColor = OrangeAccent,
                        values = UNIT_TYPES.map { it to (uiState.unitTypeCounts[it] ?: 0) },
                        accent = OrangeAccent,
                        onItemClick = { unitType ->
                            navController.navigate("${Screen.History.BASE}?filter=UNIT_$unitType&sort=NEWEST")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SectionGap))
            Text(
                text = stringResource(R.string.recent_scans),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(uiState.recentItems, key = { it.id }) { item ->
                    RecentItemCard(
                        item = item,
                        onClick = { navController.navigate(Screen.Detail.passId(item.id)) }
                    )
                }
                if (uiState.recentItems.isEmpty()) {
                    item {
                        EmptyRecentScans(onScan = { navController.navigate(Screen.Scan.route) })
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
private fun DashboardBreakdown(
    title: String,
    titleColor: Color,
    values: List<Pair<String, Int>>,
    accent: Color,
    onItemClick: (String) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(color = titleColor, fontWeight = FontWeight.Bold)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { (label, value) ->
            ClickableStatCard(
                label = label,
                value = value,
                accentColor = accent,
                width = 104.dp,
                onClick = { onItemClick(label) }
            )
        }
    }
}

@Composable
fun ClickableStatCard(
    label: String,
    value: Int,
    accentColor: Color = CyanAccent,
    width: Dp? = 140.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .border(1.dp, accentColor.copy(alpha = 0.46f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.CardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(42.dp)
                    .background(accentColor, RoundedCornerShape(50))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun RecentItemCard(item: ScannedItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanAccent.copy(alpha = 0.30f), MaterialTheme.shapes.medium),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(AppDimens.CardPadding)) {
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
                    style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                )
            }
            if (item.productName != null || item.itemCode != null) {
                Text(
                    text = stringResource(R.string.barcode_format, item.barcode),
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(text = stringResource(R.string.tag_type_format, item.tagType), color = GreenAccent)
                InfoChip(text = stringResource(R.string.unit_type_format, item.unitType), color = OrangeAccent)
                InfoChip(text = stringResource(R.string.copies_format, item.copies), color = CyanAccent)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.scanned_format, formatTimestamp(item.createdAt)),
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
            )
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
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

@Composable
private fun EmptyRecentScans(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = GreenAccent,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = stringResource(R.string.no_recent_items),
            style = MaterialTheme.typography.titleSmall.copy(color = GreenAccent)
        )
        Text(
            text = "Start scanning to build your barcode history.",
            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
        )
        Button(
            onClick = onScan,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenAccent,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text(stringResource(R.string.scan))
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
