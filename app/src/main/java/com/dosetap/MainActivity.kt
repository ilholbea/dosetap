package com.dosetap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav()
                }
            }
        }
    }
}
