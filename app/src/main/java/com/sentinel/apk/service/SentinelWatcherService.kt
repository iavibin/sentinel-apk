package com.sentinel.apk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinel.apk.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * SentinelWatcherService — Foreground Service
 *
 * Monitors the device's Downloads directory for newly added .apk files
 * using [FileObserver]. When a new APK lands, it automatically uploads
 * it to the API and fires a high-priority notification with the results.
 */
class SentinelWatcherService : Service() {

    companion object {
        private const val TAG = "SentinelWatcher"

        // Notification channel IDs
        private const val CHANNEL_FOREGROUND = "sentinel_foreground"
        private const val CHANNEL_ALERTS = "sentinel_alerts"

        // Notification IDs
        private const val NOTIFICATION_ID_FOREGROUND = 1001
        private const val NOTIFICATION_ID_ALERT_BASE = 2000

        // Directory to watch
        private val DOWNLOADS_DIR: String =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
    }

    private var fileObserver: FileObserver? = null
    private val alertCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — creating notification channels")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand — starting foreground + observer")

        startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification())
        startWatching()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — stopping observer")
        stopWatching()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val foregroundChannel = NotificationChannel(
            CHANNEL_FOREGROUND,
            "Sentinel Watchtower",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while Sentinel monitors downloads"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "APK Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a new APK file is computed"
            enableVibration(true)
        }

        manager.createNotificationChannel(foregroundChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setContentTitle("Sentinel is watching…")
            .setContentText("Monitoring Downloads for new APK files")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startWatching() {
        stopWatching()
        Log.d(TAG, "Watching directory: $DOWNLOADS_DIR")

        fileObserver = object : FileObserver(DOWNLOADS_DIR, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (!path.lowercase().endsWith(".apk")) return

                Log.i(TAG, "New APK detected: $path")
                val fullPath = "$DOWNLOADS_DIR/$path"
                scanApk(path, fullPath)
            }
        }

        fileObserver?.startWatching()
        Log.d(TAG, "FileObserver started")
    }

    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    private fun scanApk(fileName: String, fullPath: String) {
        serviceScope.launch {
            val file = File(fullPath)
            if (!file.exists()) return@launch

            val requestFile = file.asRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            try {
                Log.d(TAG, "Calling API...")
                val response = RetrofitClient.apiService.auditApk(body)
                if (response.isSuccessful && response.body() != null) {
                    val json = response.body()!!
                    val permissionsObj = runCatching { json.getAsJsonObject("permissions") }.getOrNull()
                    val riskScore = runCatching { permissionsObj?.get("risk_score")?.asInt }.getOrNull() ?: 0
                    val safetyGrade = runCatching { json.get("safety_grade")?.asString }.getOrNull() ?: "Unknown"

                    Log.i(TAG, "Scan complete for $fileName: Score=$riskScore, Grade=$safetyGrade")
                    fireScanCompleteNotification(fileName, riskScore, safetyGrade, json.toString())
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "API Error ${response.code()}: $errorBody")
                    fireFallbackNotification(fileName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network failure: ${Log.getStackTraceString(e)}")
                fireFallbackNotification(fileName)
            }
        }
    }

    private fun fireFallbackNotification(fileName: String) {
        val notificationId = NOTIFICATION_ID_ALERT_BASE + (alertCounter.incrementAndGet())
        
        val intent = Intent(this, com.sentinel.apk.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle("⚠️ APK Detected")
            .setContentText(fileName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("APK Detected — tap to scan manually")
            )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun fireScanCompleteNotification(fileName: String, riskScore: Int, safetyGrade: String, reportJson: String) {
        val notificationId = NOTIFICATION_ID_ALERT_BASE + (alertCounter.incrementAndGet())

        val resultIntent = Intent(this, com.sentinel.apk.ui.ResultActivity::class.java).apply {
            putExtra("report_json", reportJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle("⚠️ APK Scan Complete")
            .setContentText(fileName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Risk Score: $riskScore | Grade: $safetyGrade — Tap to view full report")
            )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
