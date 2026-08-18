package com.industrial.barcodescanner.presentation.screens.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: ScannedItemRepository
) : ViewModel() {

    data class RecycleBinUiState(
        val items: List<ScannedItem> = emptyList(),
        val selectedIds: Set<Long> = emptySet(),
        val error: String? = null,
        val showEmptyConfirmation: Boolean = false
    ) {
        val selectionCount: Int get() = selectedIds.size
        val allSelected: Boolean get() = items.isNotEmpty() && selectedIds.size == items.size
    }

    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState: StateFlow<RecycleBinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getDeletedItems()
                .catch { error -> _uiState.update { it.copy(error = error.message) } }
                .collect { items ->
                    _uiState.update { current ->
                        current.copy(
                            items = items,
                            selectedIds = current.selectedIds.intersect(items.map { it.id }.toSet()),
                            error = null
                        )
                    }
                }
        }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            state.copy(selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = if (state.allSelected) emptySet() else state.items.map { it.id }.toSet())
        }
    }

    fun restoreSelected() = executeSelection { ids -> repository.restoreItems(ids) }

    fun permanentlyDeleteSelected() = executeSelection { ids -> repository.permanentlyDeleteItems(ids) }

    fun restoreItem(id: Long) = viewModelScope.launch { repository.restoreItems(listOf(id)) }

    fun permanentlyDeleteItem(id: Long) = viewModelScope.launch { repository.permanentlyDeleteItems(listOf(id)) }

    fun requestEmpty() = _uiState.update { it.copy(showEmptyConfirmation = true) }

    fun dismissEmptyConfirmation() = _uiState.update { it.copy(showEmptyConfirmation = false) }

    fun confirmEmpty() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
            _uiState.update { it.copy(showEmptyConfirmation = false, selectedIds = emptySet()) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun executeSelection(action: suspend (List<Long>) -> Unit) {
        val selected = _uiState.value.selectedIds.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            action(selected)
            _uiState.update { it.copy(selectedIds = emptySet()) }
        }
    }
}
