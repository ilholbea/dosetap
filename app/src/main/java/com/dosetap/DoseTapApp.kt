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
import kotlinx.coroutines.launch

private const val TAG = "DoseTapApp"

class DoseTapApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        postInitialAmbientNotification()
        // Arm the weekly summary. Idempotent (fixed request code + FLAG_UPDATE_CURRENT), so it's
        // safe to call every launch — this is what keeps the summary alive across reboots and
        // updates, which the receiver's self-reschedule alone never bootstrapped (E-C5).
        container.alarmScheduler.scheduleWeeklySummary()
        Log.i(TAG, "DoseTap application started")
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Channel 1: Dose Alarms — high importance, alarm audio, bypasses DND
        val alarmChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Medication reminder alarms"
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

        // Channel 2: Ambient dose state — low importance, silent, always visible
        val ambientChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_AMBIENT,
            Constants.NOTIFICATION_CHANNEL_AMBIENT_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent dose status shown on lock screen"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        // Channel 3: Dose Warnings — default importance, for comedown/late-cutoff/refill
        val warningsChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_WARNINGS,
            Constants.NOTIFICATION_CHANNEL_WARNINGS_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Wear-off warnings, refill reminders, and late cutoff alerts"
            enableVibration(false)
            setShowBadge(false)
        }

        // Channel 4: Focus Timer — default importance, subtle chime when session ends
        val focusChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_FOCUS,
            Constants.NOTIFICATION_CHANNEL_FOCUS_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts when a focus or break timer ends"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 200L, 100L, 200L)
            setShowBadge(false)
        }

        nm.createNotificationChannel(alarmChannel)
        nm.createNotificationChannel(ambientChannel)
        nm.createNotificationChannel(warningsChannel)
        nm.createNotificationChannel(focusChannel)
        Log.d(TAG, "Notification channels created/verified")
    }

    private fun postInitialAmbientNotification() {
        appScope.launch(Dispatchers.IO) {
            try {
                container.refreshAmbient()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post initial ambient notification", e)
            }
        }
    }
}
