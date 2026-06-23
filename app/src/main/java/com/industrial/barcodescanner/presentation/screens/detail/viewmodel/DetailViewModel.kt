package com.industrial.barcodescanner.presentation.screens.detail.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.data.local.entity.toEntity
import com.industrial.barcodescanner.domain.model.ScannedItem
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ScannedItemRepository
) : ViewModel() {

    data class DetailUiState(
        val item: ScannedItem? = null,
        val tagType: String = "A4",
        val unitType: String = "PCS",
        val copies: Int = 1,
        val showTagPicker: Boolean = false,
        val showUnitPicker: Boolean = false,
        val showCopiesPicker: Boolean = false,
        val error: String? = null,
        val navigateBack: Boolean = false,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false
    )

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val item = repository.getItemById(itemId)
                _uiState.update {
                    it.copy(
                        item = item,
                        tagType = item?.tagType ?: "A4",
                        unitType = item?.unitType ?: "PCS",
                        copies = item?.copies ?: 1,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun openTagPicker() = _uiState.update { it.copy(showTagPicker = true) }
    fun openUnitPicker() = _uiState.update { it.copy(showUnitPicker = true) }
    fun openCopiesPicker() = _uiState.update { it.copy(showCopiesPicker = true) }
    fun dismissPickers() = _uiState.update {
        it.copy(showTagPicker = false, showUnitPicker = false, showCopiesPicker = false)
    }

    fun onTagTypeSelected(tagType: String) {
        _uiState.update { it.copy(tagType = tagType, showTagPicker = false) }
    }

    fun onUnitTypeSelected(unitType: String) {
        _uiState.update { it.copy(unitType = unitType, showUnitPicker = false) }
    }

    fun onCopiesSelected(copies: Int) {
        _uiState.update { it.copy(copies = copies, showCopiesPicker = false) }
    }

    fun saveChanges() {
        val current = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val updatedItem = current.item?.copy(
                    tagType = current.tagType,
                    unitType = current.unitType,
                    copies = current.copies,
                    updatedAt = System.currentTimeMillis()
                ) ?: return@launch
                repository.updateItem(updatedItem.toEntity())
                _uiState.update { it.copy(navigateBack = true, isSaving = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun deleteItem() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                _uiState.value.item?.let {
                    repository.deleteItem(it)
                    _uiState.update { it.copy(navigateBack = true, isSaving = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
