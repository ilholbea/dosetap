package com.dosetap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dosetap.DoseTapApp
import com.dosetap.ui.focus.FocusTimerScreen
import com.dosetap.ui.history.HistoryScreen
import com.dosetap.ui.home.HomeScreen
import com.dosetap.ui.onboarding.OnboardingScreen
import com.dosetap.ui.report.DoctorReportScreen
import com.dosetap.ui.settings.SettingsScreen
import com.dosetap.ui.stats.StatsScreen

/** All navigation destinations in the app. */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object History : Screen("history")
    data object Stats : Screen("stats")
    data object DoctorReport : Screen("doctor_report")
    data object Focus : Screen("focus")
}

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController(),
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container
    val onboardingComplete by container.settingsRepository.onboardingComplete
        .collectAsStateWithLifecycle(initialValue = false)

    // Deep link from a notification action (e.g. wear-off → Focus in Transition mode).
    // Keyed on onboardingComplete too: on a cold start it emits false first, so the effect must
    // re-run once it flips true or the deep link would be dropped.
    LaunchedEffect(deepLinkRoute, onboardingComplete) {
        if (deepLinkRoute != null && onboardingComplete) {
            navController.navigate(deepLinkRoute)
            onDeepLinkHandled()
        }
    }

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
                onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                onNavigateToFocus = { navController.navigate(Screen.Focus.route) },
                onStartFocusTimer = {
                    navController.navigate("focus?mode=TRANSITION&autostart=true")
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReport = { navController.navigate(Screen.DoctorReport.route) },
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.DoctorReport.route) {
            DoctorReportScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "focus?mode={mode}&autostart={autostart}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("autostart") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { backStackEntry ->
            FocusTimerScreen(
                onNavigateBack = { navController.popBackStack() },
                startMode = backStackEntry.arguments?.getString("mode"),
                autoStart = backStackEntry.arguments?.getBoolean("autostart") ?: false,
            )
        }
    }
}
