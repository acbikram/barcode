package com.industrial.barcodescanner.presentation.screens.scan.viewmodel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.entity.ScannedItemEntity
import com.industrial.barcodescanner.data.local.entity.toEntity
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.domain.repository.ProductCatalogRepository
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import com.industrial.barcodescanner.utils.PreferencesManager
import com.industrial.barcodescanner.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    private val productCatalogRepository: ProductCatalogRepository,
    private val preferencesManager: PreferencesManager,
    private val soundManager: SoundManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class ScanUiState(
        val scannerInactive: Boolean = false,
        /** Barcode shown on the camera overlay immediately after detection. */
        val detectedBarcode: String = "",
        val showTagTypeDialog: Boolean = false,
        val showCopiesDialog: Boolean = false,
        val showDuplicateDialog: Boolean = false,
        val pendingBarcode: String = "",
        val pendingItemCode: String? = null,
        val pendingProductName: String? = null,
        val pendingProductNameArabic: String? = null,
        val pendingTagType: String = "A4",
        val pendingUnitType: String = "PCS",
        val pendingCopies: Int = 1,
        val duplicateExistingCopies: Int = 0,
        val duplicateNewCopies: Int = 0,
        val duplicateItemId: Long = 0,
        // Edit dialog state
        val showEditDialog: Boolean = false,
        val showEditTagPicker: Boolean = false,
        val showEditUnitPicker: Boolean = false,
        val showEditCopiesPicker: Boolean = false,
        val editItemId: Long = 0,
        val editBarcode: String = "",
        val editItemCode: String? = null,
        val editProductName: String? = null,
        val editProductNameArabic: String? = null,
        val editTagType: String = "A4",
        val editUnitType: String = "PCS",
        val editCopies: Int = 1,
        // Delete confirm dialog state
        val showDeleteConfirmDialog: Boolean = false,
        val deleteItemId: Long = 0,
        // Manual barcode entry mode
        val showManualMode: Boolean = false,
        // Camera torch preference for the active scan session. The screen applies
        // it only while the CameraX controller is bound to camera scan mode.
        val torchEnabled: Boolean = false
    )

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private data class ScanPreferences(
        val lastTagType: String = "A4",
        val lastUnitType: String = "PCS"
    )

    private val scanPreferences: StateFlow<ScanPreferences> = combine(
        preferencesManager.lastTagTypeFlow,
        preferencesManager.lastUnitTypeFlow
    ) { lastTagType, lastUnitType ->
        ScanPreferences(lastTagType, lastUnitType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanPreferences())

    /** Top-20 most recently scanned items, ordered and limited by Room. */
    val recentScans: StateFlow<List<ScannedItem>> = repository.getRecentItems(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var scanTimeoutJob: Job = Job().also { it.cancel() }

    init {
        startInactivityTimer()
    }

    fun startScanner() {
        _uiState.update { it.copy(scannerInactive = false) }
        startInactivityTimer()
    }

    fun stopScanner() {
        scanTimeoutJob.cancel()
    }

    private fun startInactivityTimer() {
        scanTimeoutJob.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(10000)
            _uiState.update { it.copy(scannerInactive = true) }
        }
    }

    fun restartScanner() {
        _uiState.update { it.copy(scannerInactive = false) }
        startInactivityTimer()
    }

    /** Toggles the CameraX torch for this scan session. */
    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    // -- Manual barcode entry mode --------------------------------------------

    fun enterManualMode() {
        stopScanner()
        _uiState.update { it.copy(showManualMode = true, scannerInactive = true) }
    }

    fun exitManualMode() {
        _uiState.update { it.copy(showManualMode = false) }
        restartScanner()
    }

    /** Called when the user submits a manually typed barcode. */
    fun onManualBarcodeEntered(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(showManualMode = false, scannerInactive = false) }
        onBarcodeScanned(trimmed)
    }

    // -- Scan flow -------------------------------------------------------------

    fun onBarcodeScanned(barcode: String) {
        if (_uiState.value.scannerInactive) return
        if (_uiState.value.pendingBarcode.isNotEmpty()) return  // already processing one
        stopScanner()

        val preferences = scanPreferences.value
        // Scan feedback is intentionally always enabled for the barcode workflow.
        soundManager.playSingleBeep()
        vibrateSingle()

        val lastTagType = preferences.lastTagType
        val lastUnitType = preferences.lastUnitType

        _uiState.update {
            it.copy(
                detectedBarcode = barcode,
                scannerInactive = true,
                pendingBarcode = barcode,
                pendingItemCode = null,
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingTagType = lastTagType,
                pendingUnitType = lastUnitType,
                pendingCopies = 1,
                showTagTypeDialog = true
            )
        }

        viewModelScope.launch {
            val product = productCatalogRepository.lookup(barcode)
            if (_uiState.value.pendingBarcode == barcode) {
                _uiState.update {
                    it.copy(
                        pendingItemCode = product?.itemCode,
                        pendingProductName = product?.name,
                        pendingProductNameArabic = product?.nameArabic,
                        pendingUnitType = resolveUnitType(product?.unit, lastUnitType)
                    )
                }
            }
        }
    }

    /**
     * Resolves the Unit Type (PCS/PKT/CTN/KGS) automatically from the
     * product catalog's unit field, instead of asking the user — mirroring
     * how Near Expiry derives its display unit from the catalog. Falls back
     * to the last-used Unit Type (or PCS) when the catalog has no usable
     * unit info.
     */
    private fun resolveUnitType(catalogUnit: String?, fallback: String): String {
        val normalized = catalogUnit?.trim()?.uppercase() ?: return fallback
        return when {
            normalized.isEmpty() -> fallback
            UNIT_TYPES.contains(normalized) -> normalized
            normalized.startsWith("PC") -> "PCS"
            normalized.startsWith("PKT") || normalized.startsWith("PACK") -> "PKT"
            normalized.startsWith("CTN") || normalized.startsWith("CARTON") || normalized.startsWith("BOX") -> "CTN"
            normalized.startsWith("KG") || normalized.startsWith("KILO") -> "KGS"
            else -> fallback
        }
    }

    /** Language-aware product name for the pending barcode's dialogs. */
    fun pendingDisplayName(isArabic: Boolean): String? {
        val state = _uiState.value
        return if (isArabic) {
            state.pendingProductNameArabic?.takeIf { it.isNotBlank() } ?: state.pendingProductName
        } else {
            state.pendingProductName?.takeIf { it.isNotBlank() } ?: state.pendingProductNameArabic
        }
    }

    fun onTagTypeSelected(tagType: String) {
        viewModelScope.launch { preferencesManager.setLastTagType(tagType) }
        _uiState.update {
            it.copy(
                pendingTagType = tagType,
                showTagTypeDialog = false,
                showCopiesDialog = true
            )
        }
    }

    fun onCopiesSelected(copies: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val barcode = state.pendingBarcode
            val tagType = state.pendingTagType
            val unitType = state.pendingUnitType
            val existing = repository.findByBarcodeTagUnit(barcode, tagType, unitType)

            if (existing != null) {
                _uiState.update {
                    it.copy(
                        duplicateExistingCopies = existing.copies,
                        duplicateNewCopies = copies,
                        duplicateItemId = existing.id,
                        pendingCopies = copies,
                        showCopiesDialog = false,
                        showDuplicateDialog = true
                    )
                }
            } else {
                val newItem = ScannedItemEntity(
                    barcode = barcode,
                    itemCode = state.pendingItemCode,
                    productName = state.pendingProductName,
                    productNameArabic = state.pendingProductNameArabic,
                    tagType = tagType,
                    unitType = unitType,
                    copies = copies,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertItem(newItem)
                resetPendingState()
                startInactivityTimer()
            }
        }
    }

    fun mergeDuplicateItem() {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = repository.getItemById(state.duplicateItemId) ?: return@launch
            val updated = existing.copy(
                copies = existing.copies + state.duplicateNewCopies,
                updatedAt = System.currentTimeMillis(),
                itemCode = existing.itemCode ?: state.pendingItemCode,
                productName = existing.productName ?: state.pendingProductName,
                productNameArabic = existing.productNameArabic ?: state.pendingProductNameArabic
            )
            repository.updateItem(updated.toEntity())
            resetPendingState()
            startInactivityTimer()
        }
    }

    /** Replaces the saved copy count with the newly selected value. */
    fun replaceDuplicateItem() {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = repository.getItemById(state.duplicateItemId) ?: return@launch
            val updated = existing.copy(
                copies = state.duplicateNewCopies,
                updatedAt = System.currentTimeMillis(),
                itemCode = existing.itemCode ?: state.pendingItemCode,
                productName = existing.productName ?: state.pendingProductName,
                productNameArabic = existing.productNameArabic ?: state.pendingProductNameArabic
            )
            repository.updateItem(updated.toEntity())
            resetPendingState()
            startInactivityTimer()
        }
    }

    fun dismissDuplicateDialog() {
        resetPendingState()
        startInactivityTimer()
    }

    /** Cancels the in-flight scan (Tag/Unit/Copies dialog dismissed). */
    fun cancelPendingScan() {
        resetPendingState()
        startInactivityTimer()
    }

    private fun resetPendingState() {
        _uiState.update {
            it.copy(
                showTagTypeDialog = false,
                showCopiesDialog = false,
                showDuplicateDialog = false,
                showManualMode = false,
                pendingBarcode = "",
                pendingItemCode = null,
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingTagType = "A4",
                pendingUnitType = "PCS",
                pendingCopies = 1,
                detectedBarcode = "",
                scannerInactive = false
            )
        }
    }

    // -- Edit / Delete for recent scan items -----------------------------------

    fun requestEdit(item: ScannedItem) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editItemId = item.id,
                editBarcode = item.barcode,
                editItemCode = item.itemCode,
                editProductName = item.productName,
                editProductNameArabic = item.productNameArabic,
                editTagType = item.tagType,
                editUnitType = item.unitType,
                editCopies = item.copies
            )
        }
    }

    fun openEditTagPicker() = _uiState.update { it.copy(showEditTagPicker = true) }
    fun openEditUnitPicker() = _uiState.update { it.copy(showEditUnitPicker = true) }
    fun openEditCopiesPicker() = _uiState.update { it.copy(showEditCopiesPicker = true) }

    fun onEditTagTypeSelected(tagType: String) {
        _uiState.update { it.copy(editTagType = tagType, showEditTagPicker = false) }
    }

    fun onEditUnitTypeSelected(unitType: String) {
        _uiState.update { it.copy(editUnitType = unitType, showEditUnitPicker = false) }
    }

    fun onEditCopiesSelected(copies: Int) {
        _uiState.update { it.copy(editCopies = copies, showEditCopiesPicker = false) }
    }

    fun dismissEditPickers() {
        _uiState.update {
            it.copy(showEditTagPicker = false, showEditUnitPicker = false, showEditCopiesPicker = false)
        }
    }

    fun confirmEdit() {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = repository.getItemById(state.editItemId) ?: return@launch
            val updated = existing.copy(
                tagType = state.editTagType,
                unitType = state.editUnitType,
                copies = state.editCopies,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateItem(updated.toEntity())
            _uiState.update { it.copy(showEditDialog = false) }
        }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }

    fun requestDelete(item: ScannedItem) {
        _uiState.update {
            it.copy(showDeleteConfirmDialog = true, deleteItemId = item.id)
        }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val state = _uiState.value
            val item = repository.getItemById(state.deleteItemId) ?: return@launch
            repository.deleteItem(item)
            _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteItemId = 0) }
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteItemId = 0) }
    }

    // -- Internal helpers --------------------------------------------------------

    /** Single short vibration (new barcode / pickers). */
    private fun vibrateSingle() {
        getVibrator()?.vibrate(
            VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }
    }
}
