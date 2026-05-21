package com.dosetap.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

private const val TAG = "BatteryOptimizationHelper"

/**
 * Helper for navigating users to battery optimization settings.
 *
 * Battery optimization cannot be disabled programmatically without user consent.
 * This helper provides deep links to the relevant system settings screens.
 */
object BatteryOptimizationHelper {

    /**
     * Returns true if the app is currently exempted from battery optimization.
     * When false, alarms may be delayed or killed by the system on some devices.
     */
    fun isIgnoringBatteryOptimization(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system screen to request battery optimization exemption for this app.
     * This shows the standard Android dialog; the user must accept.
     *
     * Note: We do NOT request this automatically — it's shown in Settings → Reliability Check
     * as an informed user action. See §3 of spec.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open battery optimization settings via direct intent", e)
            // Fall back to general battery settings
            openGeneralBatterySettings(context)
        }
    }

    /**
     * Attempts to open the OEM-specific autostart/battery settings.
     * Falls back to the standard battery optimization settings if the intent fails.
     */
    fun openOemSettings(context: Context) {
        val oemIntent = OemDetector.getOemSettingsIntent()
        if (oemIntent != null) {
            try {
                oemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(oemIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "OEM settings intent failed for ${OemDetector.detectOem()}, falling back", e)
            }
        }
        // Fall back to battery optimization settings
        openBatteryOptimizationSettings(context)
    }

    /**
     * Opens the app's battery settings page in system settings.
     */
    fun openAppBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open app battery settings", e)
            openGeneralBatterySettings(context)
        }
    }

    private fun openGeneralBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open general battery settings", e)
        }
    }

    /**
     * Opens Android system notification settings for this app.
     * Used when notification permission is denied to direct user to re-enable.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open notification settings", e)
        }
    }
}
