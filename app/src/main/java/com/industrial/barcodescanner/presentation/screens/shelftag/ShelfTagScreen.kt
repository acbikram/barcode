package com.industrial.barcodescanner.presentation.screens.shelftag

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.industrial.barcodescanner.presentation.navigation.Screen
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import com.industrial.barcodescanner.presentation.screens.scan.components.ScannerView
import com.industrial.barcodescanner.utils.ShelfTagPrinter

@Composable
fun ShelfTagScreen(navController: NavController, viewModel: ShelfTagViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var cameraMode by remember { mutableStateOf(false) }
    val cameraController = remember { LifecycleCameraController(context).apply { cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> cameraMode = granted }
    var showSetup by remember { mutableStateOf(false) }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { showSetup = true }
    fun openSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else showSetup = true
    }
    LaunchedEffect(state.message, state.error) {
        (state.message ?: state.error)?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Shelf Tag", fontWeight = FontWeight.Bold, color = CyanAccent) },
                navigationIcon = { IconButton(onClick = { navController.navigate(Screen.Home.route) }) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    Text("Shelf Tag", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = true, onCheckedChange = { navController.navigate(Screen.Home.route) })
                    IconButton(onClick = ::openSetup) { Icon(Icons.Default.Settings, "Setup", tint = OrangeAccent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                Text("Scan Item", style = MaterialTheme.typography.titleMedium, color = CyanAccent)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.barcode,
                        onValueChange = viewModel::setBarcode,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); viewModel.lookup() }),
                        label = { Text("Barcode") }
                    )
                    IconButton(onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraMode = !cameraMode
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    }) { Icon(if (cameraMode) Icons.Default.Keyboard else Icons.Default.QrCodeScanner, "Scan with camera", tint = GreenAccent) }
                    Button(onClick = { keyboard?.hide(); viewModel.lookup() }, enabled = !state.loading) { Text("Get Price") }
                }
                if (cameraMode) {
                    Spacer(Modifier.height(8.dp))
                    ScannerView(
                        cameraController = cameraController,
                        onBarcodeScanned = { barcode ->
                            barcode.rawValue?.let { value -> viewModel.setBarcode(value); cameraMode = false; viewModel.lookup(value) }
                        },
                        modifier = Modifier.fillMaxWidth().height(230.dp)
                    )
                }
            }
            item { SetupSummary(state, onClick = ::openSetup) }
            state.item?.let { shelfItem ->
                item { ShelfTagItemPreview(shelfItem) }
                item {
                    Button(
                        onClick = viewModel::print,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = GreenAccent)
                    ) {
                        Icon(Icons.Default.Print, null); Spacer(Modifier.size(8.dp)); Text("Print Price Tag")
                    }
                }
            }
            if (state.loading) item { CircularProgressIndicator(modifier = Modifier.padding(24.dp).size(32.dp)) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showSetup) ShelfTagSetupDialog(state, viewModel, onDismiss = { showSetup = false })
}

@Composable private fun SetupSummary(state: ShelfTagViewModel.UiState, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(Modifier.padding(12.dp)) {
            Text("Setup", color = OrangeAccent, fontWeight = FontWeight.Bold)
            Text("${state.mode}: ${state.host}:${state.port}  •  ${state.printerModel}  •  ${state.printerName.ifBlank { "No printer selected" }}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ShelfTagItemPreview(item: ShelfTagItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Item Details", color = CyanAccent, fontWeight = FontWeight.Bold)
            PairLine("POS Code", item.posCode); PairLine("Item ID", item.idItmPos)
            Text(item.englishDescription, fontWeight = FontWeight.Bold)
            Text(item.arabicDescription, modifier = Modifier.fillMaxWidth(), color = Color.LightGray)
            PriceLine("Each", item.eachUomEng, item.eachPrice, item.currency)
            PriceLine("Offer", item.offerUomEng, item.offerPrice, item.currency)
            PriceLine("Carton", item.cartonUomEng, item.cartonPrice, item.currency)
            if (item.country == "BH") Text("VAT: ${item.vatPercentage.toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun PairLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.LightGray); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun PriceLine(label: String, uom: String, price: String, currency: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("$label: $uom"); Text("$price $currency", fontWeight = FontWeight.Bold, color = GreenAccent) } }

@Composable private fun ShelfTagSetupDialog(state: ShelfTagViewModel.UiState, vm: ShelfTagViewModel, onDismiss: () -> Unit) {
    var expandedMode by remember { mutableStateOf(false) }; var expandedModel by remember { mutableStateOf(false) }
    val devices = remember(state.printerModel) { runCatching { ShelfTagPrinter().pairedPrinters(state.printerModel) }.getOrDefault(emptyList()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Shelf Tag Setup") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(state.host, { value -> vm.update { it.copy(host = value) } }, Modifier.weight(1f), singleLine = true, label = { Text("Server IP") })
                OutlinedTextField(state.port, { value -> vm.update { it.copy(port = value) } }, Modifier.size(width = 92.dp, height = 56.dp), singleLine = true, label = { Text("Port") })
            }
            BoxDropdown(state.mode, listOf("Socket", "Web API"), expandedMode, { expandedMode = !expandedMode }) { value -> vm.update { it.copy(mode = value) }; expandedMode = false }
            BoxDropdown(state.printerModel, listOf("Zebra", "Honeywell"), expandedModel, { expandedModel = !expandedModel }) { value -> vm.update { it.copy(printerModel = value) }; expandedModel = false }
            if (devices.isNotEmpty()) {
                Text("Paired printers", style = MaterialTheme.typography.labelMedium)
                devices.take(5).forEach { device ->
                    Text("${device.name}  (${device.address})", modifier = Modifier.fillMaxWidth().clickable { vm.update { it.copy(printerName = device.name ?: "", printerAddress = device.address) } }.padding(5.dp))
                }
            }
            OutlinedTextField(state.printerAddress, { value -> vm.update { it.copy(printerAddress = value) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Printer Bluetooth address") })
            OutlinedTextField(state.copies, { value -> vm.update { it.copy(copies = value) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Print Copies") })
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Save") } }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun BoxDropdown(value: String, options: List<String>, expanded: Boolean, onExpand: () -> Unit, onSelect: (String) -> Unit) {
    Column {
        OutlinedButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) { Text(value, modifier = Modifier.weight(1f)) }
        DropdownMenu(expanded = expanded, onDismissRequest = onExpand) { options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it) }) } }
    }
}
