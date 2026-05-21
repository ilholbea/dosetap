package com.dosetap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dosetap.DoseTapApp
import com.dosetap.ui.history.HistoryScreen
import com.dosetap.ui.home.HomeScreen
import com.dosetap.ui.onboarding.OnboardingScreen
import com.dosetap.ui.settings.SettingsScreen

/** All navigation destinations in the app. */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object History : Screen("history")
}

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container
    val onboardingComplete by container.settingsRepository.onboardingComplete
        .collectAsStateWithLifecycle(initialValue = false)

    NavHost(
        navController = navController,
        startDestination = if (onboardingComplete) Screen.Home.route else Screen.Onboarding.route,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
