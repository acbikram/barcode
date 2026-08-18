package com.industrial.barcodescanner.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.industrial.barcodescanner.presentation.components.FirstLaunchLanguageDialog
import com.industrial.barcodescanner.presentation.navigation.BarcodeToCsvNavHost
import com.industrial.barcodescanner.presentation.theme.BarcodeToCsvTheme
import com.industrial.barcodescanner.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Extends AppCompatActivity (not plain ComponentActivity) so that
 * AppCompatDelegate.setApplicationLocales(...) (used for the in-app
 * English/Arabic language switch in Settings) can correctly wrap this
 * activity's resources with the chosen locale and trigger a recreate on
 * API levels below 33. On API 33+ the system LocaleManager handles this
 * directly, but AppCompatActivity is still required for consistent
 * behavior across all supported OS versions (minSdk 29).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    private val firstLaunchViewModel: FirstLaunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val context = LocalContext.current
            val themeMode by preferencesManager.themeModeFlow.collectAsState(initial = "dark")
            val notificationPromptShown by preferencesManager.notificationPermissionPromptShownFlow
                .collectAsState(initial = false)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            val useDarkTheme = when (themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            BarcodeToCsvTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BarcodeToCsvNavHost()

                    // ── First-launch language picker ─────────────────────────
                    val showLanguagePrompt by firstLaunchViewModel.showLanguagePrompt.collectAsState()
                    LaunchedEffect(showLanguagePrompt, notificationPromptShown) {
                        val shouldRequestNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !showLanguagePrompt &&
                            !notificationPromptShown &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        if (shouldRequestNotifications) {
                            preferencesManager.setNotificationPermissionPromptShown()
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (showLanguagePrompt) {
                        FirstLaunchLanguageDialog(
                            onDismiss = { firstLaunchViewModel.onLanguagePromptDismissed() }
                        )
                    }
                }
            }
        }
    }
}
