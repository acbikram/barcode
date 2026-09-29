package com.industrial.barcodescanner.presentation.screens.shelftag

import android.provider.Settings
import androidx.lifecycle.ViewModel
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.utils.PreferencesManager
import com.industrial.barcodescanner.utils.ShelfTagClient
import com.industrial.barcodescanner.utils.ShelfTagPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ShelfTagViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val client = ShelfTagClient()
    private val printer = ShelfTagPrinter()
    data class UiState(
        val barcode: String = "",
        val item: ShelfTagItem? = null,
        val host: String = "192.168.1.1",
        val port: String = "11000",
        val mode: String = "Socket",
        val printerModel: String = "Zebra",
        val printerName: String = "",
        val printerAddress: String = "",
        val media: String = "Label With Gaps",
        val labelSize: String = "4.13 X 2 Inch",
        val copies: String = "1",
        val arabic: Boolean = true,
        val calibrate: Boolean = false,
        val loading: Boolean = false,
        val message: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.shelfTagSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    host = settings.host, port = settings.port, mode = settings.mode,
                    printerModel = settings.printerModel, printerName = settings.printerName,
                    printerAddress = settings.printerAddress, media = settings.media,
                    labelSize = settings.labelSize, copies = settings.copies,
                    arabic = settings.arabic, calibrate = settings.calibrate
                )
            }
        }
    }

    fun setBarcode(value: String) { _uiState.value = _uiState.value.copy(barcode = value) }
    fun update(transform: (UiState) -> UiState) { _uiState.value = transform(_uiState.value) }

    fun lookup(barcodeOverride: String? = null) {
        val state = _uiState.value
        val barcode = (barcodeOverride ?: state.barcode).trim()
        if (barcode.isEmpty()) { _uiState.value = state.copy(error = "Enter or scan a barcode"); return }
        viewModelScope.launch {
            _uiState.value = state.copy(loading = true, error = null, message = null)
            runCatching {
                client.lookup(barcode, deviceId(), state.host.trim(), state.port.toIntOrNull() ?: 11000, state.mode)
            }.onSuccess { item ->
                _uiState.value = _uiState.value.copy(item = item, barcode = "", loading = false, message = "Item Details downloaded")
                saveSettings()
            }.onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, error = error.message ?: "Unable to download item") }
        }
    }

    fun print() {
        val state = _uiState.value
        val item = state.item ?: run { _uiState.value = state.copy(error = "Download an item before printing"); return }
        if (state.printerAddress.isBlank()) { _uiState.value = state.copy(error = "Select a paired label printer in Setup"); return }
        viewModelScope.launch {
            _uiState.value = state.copy(loading = true, error = null, message = null)
            runCatching { printer.print(state.printerModel, state.printerAddress, item, state.copies.toIntOrNull() ?: 1, state.arabic) }
                .onSuccess { _uiState.value = _uiState.value.copy(loading = false, message = "Printed successfully"); saveSettings() }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = "Unable to connect the printer: ${it.message ?: "unknown error"}") }
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, error = null) }

    private fun deviceId(): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "ANDROID"
    private suspend fun saveSettings() {
        val s = _uiState.value
        preferences.saveShelfTagSettings(s.host, s.port, s.mode, s.printerModel, s.printerName, s.printerAddress, s.media, s.labelSize, s.copies, s.arabic, s.calibrate)
    }
}
