package com.industrial.barcodescanner.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.utils.PairingQrValidation
import com.industrial.barcodescanner.utils.PairedPriceTagPc
import com.industrial.barcodescanner.utils.PriceTagPairingInvitation
import com.industrial.barcodescanner.utils.PriceTagPairingQr
import com.industrial.barcodescanner.utils.PriceTagPairingService
import com.industrial.barcodescanner.utils.SecurePairingStore
import com.industrial.barcodescanner.utils.WifiPriceTagPairingClient
import com.industrial.barcodescanner.utils.WifiSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** User-visible PC connection states; sensitive values never appear in them. */
enum class PairingConnectionState {
    NOT_PAIRED,
    QR_SCAN_READY,
    CONFIRMING_PC,
    PAIRING,
    PAIRED,
    TESTING,
    CONNECTION_FAILED
}

enum class PairingStatusMessage {
    INVALID_OR_EXPIRED_QR,
    EXPIRED_QR,
    PAIRING_FAILED,
    CONNECTION_VERIFIED,
    CONNECTION_FAILED
}

data class PairingUiState(
    val connectionState: PairingConnectionState = PairingConnectionState.NOT_PAIRED,
    val invitation: PriceTagPairingInvitation? = null,
    val pairedPc: PairedPriceTagPc? = null,
    val message: PairingStatusMessage? = null
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingStore: SecurePairingStore
) : ViewModel() {

    private val pairingService = PriceTagPairingService(pairingStore, WifiPriceTagPairingClient)
    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        val existing = pairingStore.pairedPc()
        _uiState.value = if (existing == null) {
            PairingUiState(PairingConnectionState.NOT_PAIRED)
        } else {
            PairingUiState(PairingConnectionState.PAIRED, pairedPc = existing)
        }
    }

    fun openScanner() {
        if (_uiState.value.pairedPc == null) {
            _uiState.update { it.copy(connectionState = PairingConnectionState.QR_SCAN_READY, message = null) }
        }
    }

    fun onQrScanned(rawValue: String) {
        if (_uiState.value.connectionState != PairingConnectionState.QR_SCAN_READY) return
        when (val result = PriceTagPairingQr.validate(rawValue, System.currentTimeMillis() / 1000L)) {
            is PairingQrValidation.Valid -> _uiState.update {
                it.copy(
                    connectionState = PairingConnectionState.CONFIRMING_PC,
                    invitation = result.invitation,
                    message = null
                )
            }
            is PairingQrValidation.Invalid -> _uiState.update {
                it.copy(
                    connectionState = PairingConnectionState.CONNECTION_FAILED,
                    invitation = null,
                    message = PairingStatusMessage.INVALID_OR_EXPIRED_QR
                )
            }
        }
    }

    fun cancelConfirmation() {
        _uiState.update {
            it.copy(connectionState = PairingConnectionState.QR_SCAN_READY, invitation = null, message = null)
        }
    }

    fun confirmPairing() {
        val invitation = _uiState.value.invitation ?: return
        if (invitation.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L) {
            _uiState.update {
                it.copy(
                    connectionState = PairingConnectionState.CONNECTION_FAILED,
                    invitation = null,
                    message = PairingStatusMessage.EXPIRED_QR
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = PairingConnectionState.PAIRING, message = null) }
            val paired = runCatching {
                withContext(Dispatchers.IO) { pairingService.pair(invitation) }
            }.getOrNull()
            if (paired == null) {
                _uiState.update {
                    it.copy(
                        connectionState = PairingConnectionState.CONNECTION_FAILED,
                        invitation = null,
                        message = PairingStatusMessage.PAIRING_FAILED
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        connectionState = PairingConnectionState.PAIRED,
                        invitation = null,
                        pairedPc = paired,
                        message = null
                    )
                }
            }
        }
    }

    fun testConnection() {
        val paired = _uiState.value.pairedPc ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = PairingConnectionState.TESTING, message = null) }
            val successful = runCatching {
                withContext(Dispatchers.IO) {
                    WifiSender.ping(paired.host, paired.port, paired.deviceToken)
                }
            }.isSuccess
            _uiState.update {
                if (successful) it.copy(
                    connectionState = PairingConnectionState.PAIRED,
                    message = PairingStatusMessage.CONNECTION_VERIFIED
                ) else it.copy(
                    connectionState = PairingConnectionState.CONNECTION_FAILED,
                    message = PairingStatusMessage.CONNECTION_FAILED
                )
            }
        }
    }

    fun forgetPc() {
        pairingStore.forgetPairedPc()
        _uiState.value = PairingUiState(PairingConnectionState.NOT_PAIRED)
    }
}
