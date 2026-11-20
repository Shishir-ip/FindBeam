package com.findbeam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.findbeam.app.core.store.Locator
import com.findbeam.app.core.store.SettingsStore
import com.findbeam.app.ui.finder.FinderViewModel
import com.findbeam.app.ui.nav.FindBeamNavHost
import com.findbeam.app.ui.theme.FindBeamTheme
import com.findbeam.app.ui.wifi.WifiViewModel

class MainActivity : ComponentActivity() {

    private val finderViewModel: FinderViewModel by viewModels()
    private val wifiViewModel: WifiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        Locator.init(applicationContext)

        enableEdgeToEdge()

        setContent {
            val settings by Locator.settingsStore.settings.collectAsStateWithLifecycle()

            // Status bar icon color follows the app theme, not the system theme.
            val dark = when (settings.themeMode) {
                SettingsStore.ThemeMode.DARK -> true
                SettingsStore.ThemeMode.LIGHT -> false
                SettingsStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !dark
            }

            FindBeamTheme(themeMode = settings.themeMode) {
                FindBeamNavHost(
                    finderViewModel = finderViewModel,
                    wifiViewModel = wifiViewModel
                )
            }
        }
    }
}
