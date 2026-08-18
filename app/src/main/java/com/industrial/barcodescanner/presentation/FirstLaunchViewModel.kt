package com.industrial.barcodescanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrial.barcodescanner.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FirstLaunchViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _showLanguagePrompt = MutableStateFlow(false)
    val showLanguagePrompt: StateFlow<Boolean> = _showLanguagePrompt.asStateFlow()

    private val _showThemePrompt = MutableStateFlow(false)
    val showThemePrompt: StateFlow<Boolean> = _showThemePrompt.asStateFlow()

    init {
        viewModelScope.launch {
            val languageShown = runCatching {
                preferencesManager.languagePromptShownFlow.first()
            }.getOrDefault(false)
            val themeShown = runCatching {
                preferencesManager.themePromptShownFlow.first()
            }.getOrDefault(false)
            _showLanguagePrompt.value = !languageShown
            _showThemePrompt.value = languageShown && !themeShown
        }
    }

    fun onLanguagePromptDismissed() {
        viewModelScope.launch {
            preferencesManager.setLanguagePromptShown()
            _showLanguagePrompt.value = false
            _showThemePrompt.value = true
        }
    }

    fun onThemePromptConfirmed(mode: String) {
        viewModelScope.launch {
            runCatching { preferencesManager.setThemeMode(mode) }
            preferencesManager.setThemePromptShown()
            _showThemePrompt.value = false
        }
    }
}
