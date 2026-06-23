package com.industrial.barcodescanner.presentation.screens.printhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.domain.model.PrintJob
import com.industrial.barcodescanner.utils.PrintHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintHistoryViewModel @Inject constructor(
    private val historyManager: PrintHistoryManager
) : ViewModel() {

    data class PrintHistoryUiState(
        val jobs: List<PrintJob> = emptyList(),
        val isLoading: Boolean = true,
        val searchQuery: String = ""
    ) {
        val filteredJobs: List<PrintJob>
            get() = if (searchQuery.isBlank()) jobs
            else jobs.filter { job ->
                job.sheets.any { sheet ->
                    sheet.items.any { item ->
                        item.pos.contains(searchQuery, ignoreCase = true) ||
                            item.eng.contains(searchQuery, ignoreCase = true)
                    } || sheet.tag.contains(searchQuery, ignoreCase = true)
                }
            }
    }

    private val _uiState = MutableStateFlow(PrintHistoryUiState())
    val uiState: StateFlow<PrintHistoryUiState> = _uiState.asStateFlow()

    init { loadHistory() }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val jobs = historyManager.loadJobs()
            _uiState.update { it.copy(jobs = jobs, isLoading = false) }
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun deleteJob(id: Long) {
        viewModelScope.launch {
            historyManager.deleteJob(id)
            loadHistory()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyManager.clearAll()
            _uiState.update { it.copy(jobs = emptyList()) }
        }
    }
}
