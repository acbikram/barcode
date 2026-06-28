package com.industrial.barcodescanner.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.catalog.ProductCatalogOpenHelper
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.domain.repository.WifiPrintHistoryRepository
import com.industrial.barcodescanner.presentation.screens.scan.TAG_TYPES
import com.industrial.barcodescanner.presentation.screens.scan.UNIT_TYPES
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScannedItemRepository,
    private val wifiHistoryRepository: WifiPrintHistoryRepository,
    private val catalogOpenHelper: ProductCatalogOpenHelper
) : ViewModel() {

    data class HomeUiState(
        val totalRecords: Int = 0,
        val totalCopies: Int = 0,
        val tagTypeCounts: Map<String, Int> = TAG_TYPES.associateWith { 0 },
        val unitTypeCounts: Map<String, Int> = UNIT_TYPES.associateWith { 0 },
        val recentItems: List<ScannedItem> = emptyList(),
        /** True when the product catalog has no entries (user needs to load it). */
        val catalogEmpty: Boolean = false,
        /** Physical pages printed via WiFi today. */
        val wifiPagesToday: Int = 0,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeItems()
        observeWifiStats()
        checkCatalog()
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .flowOn(Dispatchers.Default)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { allItems ->
                    _uiState.update {
                        it.copy(
                            totalRecords  = allItems.size,
                            totalCopies   = allItems.sumOf { i -> i.copies },
                            tagTypeCounts = TAG_TYPES.associateWith  { t -> allItems.count { i -> i.tagType  == t } },
                            unitTypeCounts = UNIT_TYPES.associateWith { u -> allItems.count { i -> i.unitType == u } },
                            recentItems   = allItems.sortedByDescending { i -> i.createdAt }.take(5),
                            error = null
                        )
                    }
                }
        }
    }

    private fun observeWifiStats() {
        val startOfToday = LocalDateTime.now()
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
            .atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        viewModelScope.launch {
            wifiHistoryRepository.getAll()
                .map { rows -> rows.count { it.kind == "sheet" && it.timestamp >= startOfToday } }
                .collect { count -> _uiState.update { it.copy(wifiPagesToday = count) } }
        }
    }

    private fun checkCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val empty = catalogOpenHelper.productCount() == 0
            _uiState.update { it.copy(catalogEmpty = empty) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
