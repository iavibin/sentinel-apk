package com.sentinel.apk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * SentinelWatcherService — Foreground Service
 *
 * Monitors the device's Downloads directory for newly added .apk files
 * using [FileObserver]. When a new APK lands, it fires a high-priority
 * notification with "Scan Now" and "Ignore" action buttons.
 *
 * Lifecycle:
 *   startForegroundService(intent) → onCreate → onStartCommand → watching…
 *   stopSelf() or stopService(intent) → onDestroy → observer stopped
 */
class SentinelWatcherService : Service() {

    companion object {
        private const val TAG = "SentinelWatcher"

        // Notification channel IDs
        private const val CHANNEL_FOREGROUND = "sentinel_foreground"
        private const val CHANNEL_ALERTS     = "sentinel_alerts"

        // Notification IDs
        private const val NOTIFICATION_ID_FOREGROUND = 1001
        private const val NOTIFICATION_ID_ALERT_BASE = 2000

        // Intent actions for the notification buttons
        const val ACTION_SCAN_APK   = "com.sentinel.apk.action.SCAN_APK"
        const val ACTION_IGNORE_APK = "com.sentinel.apk.action.IGNORE_APK"
        const val EXTRA_APK_PATH    = "com.sentinel.apk.extra.APK_PATH"
        const val EXTRA_APK_NAME    = "com.sentinel.apk.extra.APK_NAME"

        // Directory to watch
        private val DOWNLOADS_DIR: String =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
    }

    /** FileObserver watching the Downloads folder. */
    private var fileObserver: FileObserver? = null

    /** Counter so each alert notification gets a unique ID. */
    private var alertCounter = 0

    // ──────────────────────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — creating notification channels")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand — starting foreground + observer")

        // 1. Go foreground immediately to avoid ANR on Android 12+
        startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification())

        // 2. Start watching the Downloads directory
        startWatching()

        // If the system kills us, restart automatically
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — stopping observer")
        stopWatching()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null // Not a bound service

    // ──────────────────────────────────────────────────────────────
    // Notification channels
    // ──────────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Foreground (silent, low importance — just keeps the service alive)
        val foregroundChannel = NotificationChannel(
            CHANNEL_FOREGROUND,
            "Sentinel Watchtower",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while Sentinel monitors downloads"
            setShowBadge(false)
        }

        // Alerts (high importance — pops on screen when an APK is found)
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "APK Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a new APK file is detected in Downloads"
            enableVibration(true)
        }

        manager.createNotificationChannel(foregroundChannel)
        manager.createNotificationChannel(alertChannel)
    }

    // ──────────────────────────────────────────────────────────────
    // Foreground notification
    // ──────────────────────────────────────────────────────────────

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setContentTitle("Sentinel is watching…")
            .setContentText("Monitoring Downloads for new APK files")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your own icon
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ──────────────────────────────────────────────────────────────
    // FileObserver — watches Downloads/
    // ──────────────────────────────────────────────────────────────

    private fun startWatching() {
        stopWatching() // safety: tear down any existing observer

        Log.d(TAG, "Watching directory: $DOWNLOADS_DIR")

        fileObserver = object : FileObserver(DOWNLOADS_DIR, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return

                // Only react to .apk files
                if (!path.lowercase().endsWith(".apk")) return

                Log.i(TAG, "New APK detected: $path")
                val fullPath = "$DOWNLOADS_DIR/$path"
                fireApkDetectedNotification(path, fullPath)
            }
        }

        fileObserver?.startWatching()
        Log.d(TAG, "FileObserver started")
    }

    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    // ──────────────────────────────────────────────────────────────
    // APK-detected alert notification
    // ──────────────────────────────────────────────────────────────

    private fun fireApkDetectedNotification(fileName: String, fullPath: String) {
        val notificationId = NOTIFICATION_ID_ALERT_BASE + (alertCounter++)

        // ── "Scan Now" action ──────────────────────────────────
        val scanIntent = Intent(ACTION_SCAN_APK).apply {
            setPackage(packageName)
            putExtra(EXTRA_APK_PATH, fullPath)
            putExtra(EXTRA_APK_NAME, fileName)
        }
        val scanPending = PendingIntent.getBroadcast(
            this,
            notificationId,
            scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── "Ignore" action ───────────────────────────────────
        val ignoreIntent = Intent(ACTION_IGNORE_APK).apply {
            setPackage(packageName)
            putExtra(EXTRA_APK_PATH, fullPath)
            putExtra(EXTRA_APK_NAME, fileName)
        }
        val ignorePending = PendingIntent.getBroadcast(
            this,
            notificationId + 10_000, // unique request code
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Build the notification ────────────────────────────
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle("⚠️ New APK detected")
            .setContentText(fileName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$fileName\nDo you want Sentinel to scan this file for threats?")
            )
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Replace with your own icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "Scan Now",
                scanPending
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Ignore",
                ignorePending
            )
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
