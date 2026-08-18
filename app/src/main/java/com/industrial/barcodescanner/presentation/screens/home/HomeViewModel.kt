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
        observeDashboard()
        observeWifiStats()
        checkCatalog()
    }

    private fun observeDashboard() {
        viewModelScope.launch {
            combine(
                repository.getDashboardSummary(),
                repository.getTagTypeCounts(),
                repository.getUnitTypeCounts(),
                repository.getRecentItems(limit = 5)
            ) { summary, tagCounts, unitCounts, recentItems ->
                DashboardData(
                    totalRecords = summary.totalRecords,
                    totalCopies = summary.totalCopies,
                    tagTypeCounts = TAG_TYPES.associateWith { tagType ->
                        tagCounts.firstOrNull { it.tagType == tagType }?.count ?: 0
                    },
                    unitTypeCounts = UNIT_TYPES.associateWith { unitType ->
                        unitCounts.firstOrNull { it.unitType == unitType }?.count ?: 0
                    },
                    recentItems = recentItems
                )
            }
                .catch { error -> _uiState.update { it.copy(error = error.message) } }
                .collect { dashboard ->
                    _uiState.update {
                        it.copy(
                            totalRecords = dashboard.totalRecords,
                            totalCopies = dashboard.totalCopies,
                            tagTypeCounts = dashboard.tagTypeCounts,
                            unitTypeCounts = dashboard.unitTypeCounts,
                            recentItems = dashboard.recentItems,
                            error = null
                        )
                    }
                }
        }
    }

    private data class DashboardData(
        val totalRecords: Int,
        val totalCopies: Int,
        val tagTypeCounts: Map<String, Int>,
        val unitTypeCounts: Map<String, Int>,
        val recentItems: List<ScannedItem>
    )

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
