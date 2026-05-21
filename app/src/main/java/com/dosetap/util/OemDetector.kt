package com.dosetap.util

import android.content.ComponentName
import android.content.Intent
import android.os.Build

/**
 * Detects the device OEM and returns the appropriate deep-link intent
 * to the battery/autostart settings screen.
 *
 * Based on research from dontkillmyapp.com.
 * If the OEM-specific intent is unavailable, callers should fall back
 * to the standard battery optimization settings.
 */
object OemDetector {

    /** Known OEM manufacturers with aggressive battery management. */
    enum class Oem {
        XIAOMI,
        SAMSUNG,
        ONEPLUS,
        HUAWEI,
        OPPO,
        VIVO,
        ASUS,
        UNKNOWN,
    }

    /** Returns the detected OEM based on [Build.MANUFACTURER]. */
    fun detectOem(): Oem {
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> Oem.XIAOMI
            "samsung" -> Oem.SAMSUNG
            "oneplus" -> Oem.ONEPLUS
            "huawei", "honor" -> Oem.HUAWEI
            "oppo", "realme", "realme gt" -> Oem.OPPO
            "vivo" -> Oem.VIVO
            "asus" -> Oem.ASUS
            else -> Oem.UNKNOWN
        }
    }

    /** Returns true if the current device is a known problematic OEM. */
    fun isKnownProblematic(): Boolean = detectOem() != Oem.UNKNOWN

    /**
     * Returns the OEM-specific intent to open battery/autostart settings.
     * Returns null if the OEM is unknown.
     *
     * Callers must wrap this in a try/catch when calling [android.content.Context.startActivity],
     * as the intent may not resolve on all devices of that OEM.
     */
    fun getOemSettingsIntent(): Intent? {
        return when (detectOem()) {
            Oem.XIAOMI -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            Oem.SAMSUNG -> Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
            Oem.ONEPLUS -> Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
            Oem.HUAWEI -> Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            Oem.OPPO -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.FakeActivity"
                )
            }
            Oem.VIVO -> Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            Oem.ASUS -> Intent().apply {
                component = ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.powersaver.PowerSaverSettings"
                )
            }
            Oem.UNKNOWN -> null
        }
    }

    /** Returns the display name of the OEM for UI text. */
    fun getOemDisplayName(): String {
        return Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }
    }
}
