package com.industrial.barcodescanner.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { NEWEST, OLDEST, COPIES }

/** "ALL" for everything, or a "TAG_<x>" / "UNIT_<x>" filter key. */
const val FILTER_ALL_KEY = "ALL"

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ScannedItemRepository
) : ViewModel() {

    data class HistoryUiState(
        val allItems: List<ScannedItem> = emptyList(),
        val filteredItems: List<ScannedItem> = emptyList(),
        val searchQuery: String = "",
        /** "ALL", "TAG_<TagType>", or "UNIT_<UnitType>". */
        val filter: String = FILTER_ALL_KEY,
        val sortOrder: SortOrder = SortOrder.NEWEST,
        val selectionMode: Boolean = false,
        val selectedIds: Set<Long> = emptySet(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /**
     * Called from the composable after the ViewModel is created to apply the
     * initial filter/sort passed from a dashboard stat-card tap.
     */
    fun applyInitialFilterAndSort(filterStr: String, sortStr: String) {
        val sort = when (sortStr) {
            "COPIES" -> SortOrder.COPIES
            "OLDEST" -> SortOrder.OLDEST
            else -> SortOrder.NEWEST
        }
        _uiState.update { it.copy(filter = filterStr.ifBlank { FILTER_ALL_KEY }, sortOrder = sort) }
        applyFiltersAndSort()
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { items ->
                    _uiState.update { it.copy(allItems = items) }
                    applyFiltersAndSort()
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(filter = filter) }
        applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        val next = when (_uiState.value.sortOrder) {
            SortOrder.NEWEST -> SortOrder.OLDEST
            SortOrder.OLDEST -> SortOrder.COPIES
            SortOrder.COPIES -> SortOrder.NEWEST
        }
        _uiState.update { it.copy(sortOrder = next) }
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        var filtered = state.allItems

        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.barcode.contains(state.searchQuery, ignoreCase = true) ||
                    it.productName?.contains(state.searchQuery, ignoreCase = true) == true ||
                    it.productNameArabic?.contains(state.searchQuery, ignoreCase = true) == true ||
                    it.itemCode?.contains(state.searchQuery, ignoreCase = true) == true
            }
        }

        filtered = when {
            state.filter == FILTER_ALL_KEY -> filtered
            state.filter.startsWith("TAG_") -> filtered.filter { it.tagType == state.filter.removePrefix("TAG_") }
            state.filter.startsWith("UNIT_") -> filtered.filter { it.unitType == state.filter.removePrefix("UNIT_") }
            else -> filtered
        }

        filtered = when (state.sortOrder) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOrder.COPIES -> filtered.sortedByDescending { it.copies }
        }

        _uiState.update { it.copy(filteredItems = filtered) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Selection mode ──────────────────────────────────────────────────────

    fun enterSelectionMode() {
        _uiState.update { it.copy(selectionMode = true, selectedIds = emptySet()) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleItemSelected(id: Long) {
        _uiState.update { state ->
            val newSelection = if (state.selectedIds.contains(id)) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = newSelection)
        }
    }

    /** Selects all items currently visible under the active filter/search. */
    fun selectAllVisible() {
        _uiState.update { state ->
            state.copy(selectedIds = state.filteredItems.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ── Delete actions ──────────────────────────────────────────────────────

    /** Deletes the items currently selected in selection mode. */
    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            if (ids.isNotEmpty()) {
                repository.deleteItemsByIds(ids)
            }
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
        }
    }

    /**
     * Deletes every record matching the currently active filter — all
     * records if the filter is [FILTER_ALL_KEY], or every record with the
     * filtered Tag Type / Unit Type otherwise. Ignores the search query, so
     * this always clears the entire tag/unit group.
     */
    fun deleteByCurrentFilter() {
        viewModelScope.launch {
            when {
                _uiState.value.filter == FILTER_ALL_KEY -> repository.deleteAllItems()
                _uiState.value.filter.startsWith("TAG_") ->
                    repository.deleteItemsByTagType(_uiState.value.filter.removePrefix("TAG_"))
                _uiState.value.filter.startsWith("UNIT_") ->
                    repository.deleteItemsByUnitType(_uiState.value.filter.removePrefix("UNIT_"))
            }
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAllItems()
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
        }
    }

    /** Count of records that [deleteByCurrentFilter] would remove. */
    fun countForCurrentFilter(): Int {
        val state = _uiState.value
        return when {
            state.filter == FILTER_ALL_KEY -> state.allItems.size
            state.filter.startsWith("TAG_") -> state.allItems.count { it.tagType == state.filter.removePrefix("TAG_") }
            state.filter.startsWith("UNIT_") -> state.allItems.count { it.unitType == state.filter.removePrefix("UNIT_") }
            else -> 0
        }
    }
}
