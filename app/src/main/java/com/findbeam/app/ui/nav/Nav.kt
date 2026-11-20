package com.findbeam.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.findbeam.app.ui.finder.DeviceScanScreen
import com.findbeam.app.ui.finder.FinderViewModel
import com.findbeam.app.ui.finder.TrackingScreen
import com.findbeam.app.ui.home.HomeScreen
import com.findbeam.app.ui.settings.SettingsScreen
import com.findbeam.app.ui.wifi.SpotsScreen
import com.findbeam.app.ui.wifi.WifiScreen
import com.findbeam.app.ui.wifi.WifiViewModel

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val TRACK = "track/{address}/{name}"
    const val WIFI = "wifi"
    const val SPOTS = "spots"
    const val SETTINGS = "settings"

    fun track(address: String, name: String): String {
        val encoded = Uri.encode(name.ifBlank { "device" })
        return "track/${Uri.encode(address)}/$encoded"
    }
}

@Composable
fun FindBeamNavHost(
    navController: NavHostController = rememberNavController(),
    finderViewModel: FinderViewModel,
    wifiViewModel: WifiViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenFinder = { navController.navigate(Routes.SCAN) },
                onOpenWifi = { navController.navigate(Routes.WIFI) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SCAN) {
            DeviceScanScreen(
                viewModel = finderViewModel,
                onBack = {
                    finderViewModel.stopScan()
                    navController.popBackStack()
                },
                onSelect = { address, name ->
                    finderViewModel.selectDevice(address, name)
                    navController.navigate(Routes.track(address, name))
                }
            )
        }

        composable(Routes.TRACK) { entry ->
            val address = entry.arguments?.getString("address") ?: ""
            val name = Uri.decode(entry.arguments?.getString("name") ?: "device")
            TrackingScreen(
                viewModel = finderViewModel,
                onDone = { navController.popBackStack() }
            )
        }

        composable(Routes.WIFI) {
            WifiScreen(
                viewModel = wifiViewModel,
                onBack = { navController.popBackStack() },
                onOpenSpots = { navController.navigate(Routes.SPOTS) }
            )
        }

        composable(Routes.SPOTS) {
            SpotsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
