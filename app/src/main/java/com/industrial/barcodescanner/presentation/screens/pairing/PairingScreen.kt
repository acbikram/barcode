package com.industrial.barcodescanner.presentation.screens.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.presentation.screens.scan.components.ScannerView
import com.industrial.barcodescanner.presentation.screens.pairing.PairingStatusMessage
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    navController: NavController,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState == PairingConnectionState.QR_SCAN_READY && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_price_tag_pc)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        when (uiState.connectionState) {
            PairingConnectionState.QR_SCAN_READY -> {
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(Color.Black)
                    ) {
                        ScannerView(
                            cameraController = cameraController,
                            onBarcodeScanned = { barcode -> barcode.rawValue?.let(viewModel::onQrScanned) },
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.68f))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.qr_scan_ready),
                                style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.pair_scan_hint),
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { navController.popBackStack() }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                } else {
                    PairingContent(
                        padding = padding,
                        title = stringResource(R.string.camera_permission_required),
                        body = stringResource(R.string.pair_camera_permission_hint),
                        actionLabel = stringResource(R.string.grant_permission),
                        onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }

            PairingConnectionState.PAIRING,
            PairingConnectionState.TESTING -> {
                PairingContent(
                    padding = padding,
                    title = stringResource(
                        if (uiState.connectionState == PairingConnectionState.PAIRING) R.string.pairing
                        else R.string.testing
                    ),
                    body = stringResource(
                        if (uiState.connectionState == PairingConnectionState.PAIRING) R.string.pairing_waiting
                        else R.string.testing_waiting
                    ),
                    progress = true
                )
            }

            PairingConnectionState.PAIRED -> {
                val pc = uiState.pairedPc
                PairingContent(
                    padding = padding,
                    title = stringResource(R.string.paired_with_format, pc?.pcName.orEmpty()),
                    body = pc?.let { "${it.host}:${it.port}" }.orEmpty(),
                    icon = Icons.Default.Wifi,
                    actionLabel = stringResource(R.string.test_connection),
                    onAction = viewModel::testConnection,
                    secondaryLabel = stringResource(R.string.forget_this_pc),
                    onSecondary = viewModel::forgetPc,
                    message = uiState.message?.let { pairingMessageText(it) }
                )
            }

            PairingConnectionState.NOT_PAIRED,
            PairingConnectionState.CONNECTION_FAILED -> {
                PairingContent(
                    padding = padding,
                    title = stringResource(
                        if (uiState.connectionState == PairingConnectionState.NOT_PAIRED) R.string.not_paired
                        else R.string.connection_failed
                    ),
                    body = uiState.message?.let { pairingMessageText(it) }
                        ?: stringResource(R.string.pair_not_paired_hint),
                    icon = if (uiState.connectionState == PairingConnectionState.CONNECTION_FAILED) null else Icons.Default.QrCodeScanner,
                    actionLabel = stringResource(
                        if (uiState.pairedPc != null) R.string.test_connection else R.string.pair_price_tag_pc
                    ),
                    onAction = if (uiState.pairedPc != null) viewModel::testConnection else viewModel::openScanner,
                    secondaryLabel = if (uiState.pairedPc != null) stringResource(R.string.forget_this_pc) else null,
                    onSecondary = if (uiState.pairedPc != null) viewModel::forgetPc else null
                )
            }

            PairingConnectionState.CONFIRMING_PC -> Unit
        }
    }

    if (uiState.connectionState == PairingConnectionState.CONFIRMING_PC) {
        val invitation = uiState.invitation
        if (invitation != null) {
            AlertDialog(
                onDismissRequest = viewModel::cancelConfirmation,
                title = { Text(stringResource(R.string.confirm_pc_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(invitation.pcName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${invitation.host}:${invitation.port}", color = SubtleGray)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.confirm_pc_message))
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::confirmPairing) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = viewModel::cancelConfirmation) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
private fun pairingMessageText(message: PairingStatusMessage): String = when (message) {
    PairingStatusMessage.INVALID_OR_EXPIRED_QR -> stringResource(R.string.pair_qr_invalid_or_expired)
    PairingStatusMessage.EXPIRED_QR -> stringResource(R.string.pair_qr_expired)
    PairingStatusMessage.PAIRING_FAILED -> stringResource(R.string.pair_failed)
    PairingStatusMessage.CONNECTION_VERIFIED -> stringResource(R.string.pair_connection_verified)
    PairingStatusMessage.CONNECTION_FAILED -> stringResource(R.string.pair_connection_failed_hint)
}

@Composable
private fun PairingContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    message: String? = null,
    progress: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (progress) {
            CircularProgressIndicator(color = CyanAccent)
            Spacer(Modifier.height(18.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(18.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray))
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent))
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(secondaryLabel)
            }
        }
    }
}
