package com.dosetap

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.provider.Settings
import android.util.Log
import com.dosetap.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "DoseTapApp"

/**
 * Application class. Creates the [AppContainer] and initializes the notification channel.
 *
 * Notification channel creation is idempotent — safe to call on every app start.
 */
class DoseTapApp : Application() {

    /** Process-lifetime scope — never cancelled, safe for widget updates from dying Activities. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        Log.i(TAG, "DoseTap application started")
    }

    /**
     * Creates the notification channel for medication alarms.
     *
     * Channel properties per §5.3:
     * - Importance: HIGH (shows heads-up notification)
     * - Sound: alarm audio stream (bypasses DND when user has "Alarms" in DND exceptions)
     * - Vibration: [0, 500, 200, 500]
     * - Lock screen visibility: PUBLIC
     * - Badge: disabled
     *
     * This call is idempotent — creating an existing channel with the same ID is a no-op
     * for most properties (sound/vibration cannot be changed after creation without
     * deleting and recreating the channel, which would reset user customizations).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Medication reminder alarms"

            // Use USAGE_ALARM so alarms bypass DND when the user has enabled "Alarms" in DND
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)

            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 500L, 200L, 500L)

            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC

            setShowBadge(false)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created/verified: ${Constants.NOTIFICATION_CHANNEL_ID}")
    }
}
