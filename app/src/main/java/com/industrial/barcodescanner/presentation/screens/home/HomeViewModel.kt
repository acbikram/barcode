package com.industrial.barcodescanner.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScannedItemRepository
) : ViewModel() {

    data class HomeUiState(
        val totalRecords: Int = 0,
        val uniqueBarcodes: Int = 0,
        val totalCopies: Int = 0,
        /** Counts for A4 / 4PCS / 4PCS_DATE / 4PCS_SAME / VEG, in [TAG_TYPES] order. */
        val tagTypeCounts: Map<String, Int> = TAG_TYPES.associateWith { 0 },
        /** Counts for PCS / PKT / CTN / KGS, in [UNIT_TYPES] order. */
        val unitTypeCounts: Map<String, Int> = UNIT_TYPES.associateWith { 0 },
        val recentItems: List<ScannedItem> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /**
     * Continuously collects the items Flow from Room so the dashboard
     * recomputes automatically whenever a record is inserted, updated,
     * or deleted — no manual refresh or app restart required.
     */
    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { allItems ->
                    val totalRecords = allItems.size
                    val uniqueBarcodes = allItems.map { it.barcode }.distinct().size
                    val totalCopies = allItems.sumOf { it.copies }

                    val tagTypeCounts = TAG_TYPES.associateWith { tagType ->
                        allItems.count { it.tagType == tagType }
                    }
                    val unitTypeCounts = UNIT_TYPES.associateWith { unitType ->
                        allItems.count { it.unitType == unitType }
                    }

                    val recentItems = allItems.sortedByDescending { it.createdAt }.take(5)

                    _uiState.update {
                        it.copy(
                            totalRecords = totalRecords,
                            uniqueBarcodes = uniqueBarcodes,
                            totalCopies = totalCopies,
                            tagTypeCounts = tagTypeCounts,
                            unitTypeCounts = unitTypeCounts,
                            recentItems = recentItems,
                            error = null
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
