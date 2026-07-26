package com.dosetap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.dosetap.ui.navigation.AppNav
import com.dosetap.ui.theme.AppTheme

/**
 * Single-activity entry point for DoseTap.
 *
 * Hosts the Compose Navigation graph ([AppNav]).
 * All navigation, back-stack management, and deep links are handled by Nav Compose.
 */
class MainActivity : ComponentActivity() {

    /** Route to deep-link into, derived from the launching intent (e.g. a notification action). */
    private val deepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkRoute.value = routeFromIntent(intent)
        setContent {
            val route by deepLinkRoute
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(
                        deepLinkRoute = route,
                        onDeepLinkHandled = { deepLinkRoute.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRoute.value = routeFromIntent(intent)
    }

    /** Translates a launching intent's extras into a nav route, or null for a normal launch. */
    private fun routeFromIntent(intent: Intent?): String? {
        if (intent?.getStringExtra(EXTRA_NAV_DEST) == NAV_DEST_FOCUS) {
            val mode = intent.getStringExtra(EXTRA_FOCUS_MODE) ?: "TRANSITION"
            val autoStart = intent.getBooleanExtra(EXTRA_FOCUS_AUTOSTART, false)
            return "focus?mode=$mode&autostart=$autoStart"
        }
        return null
    }

    companion object {
        const val EXTRA_NAV_DEST = "nav_dest"
        const val NAV_DEST_FOCUS = "focus"
        const val EXTRA_FOCUS_MODE = "focus_mode"
        const val EXTRA_FOCUS_AUTOSTART = "focus_autostart"
    }
}
