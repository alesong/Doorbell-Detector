package com.doorbell.detector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.doorbell.detector.DoorbellDetectorApp
import com.doorbell.detector.data.ApiClient
import com.doorbell.detector.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationEntry(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: String,
    val sent: Boolean = false
)

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val MAX_ENTRIES = 20

        @Volatile
        var isConnected: Boolean = false

        @Volatile
        var targetPackages: Set<String> = emptySet()

        @Volatile
        var targetAppNames: Map<String, String> = emptyMap()

        @Volatile
        var targetServer: String = ""

        private val _notificationsFlow = MutableStateFlow<List<NotificationEntry>>(emptyList())
        val notificationsFlow: StateFlow<List<NotificationEntry>> = _notificationsFlow

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun setTargets(packages: Set<String>, names: Map<String, String>, serverUrl: String) {
            targetPackages = packages
            targetAppNames = names
            targetServer = serverUrl
            Log.d(TAG, "setTargets: pkgs=$packages server=$serverUrl")
        }

        fun clearNotifications() {
            _notificationsFlow.value = emptyList()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiClient = ApiClient()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startForegroundSafe()
        loadFromDataStore()
        observePreferences()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand flags=$flags startId=$startId")
        startForegroundSafe()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        Log.d(TAG, "onBind")
        startForegroundSafe()
        return super.onBind(intent)
    }

    override fun onListenerConnected() {
        Log.d(TAG, "onListenerConnected")
        isConnected = true
        startForegroundSafe()
        loadFromDataStore()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "onListenerDisconnected")
        isConnected = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras?.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras?.getString(Notification.EXTRA_TEXT)
            ?: extras?.getString(Notification.EXTRA_BIG_TEXT)
            ?: ""

        val entry = NotificationEntry(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = timeFormat.format(Date()),
            sent = false
        )

        _notificationsFlow.value = listOf(entry) + _notificationsFlow.value.take(MAX_ENTRIES - 1)

        Log.d(TAG, "onNotificationPosted pkg=$packageName targets=$targetPackages title='$title'")

        if (targetPackages.isNotEmpty() && packageName !in targetPackages) return
        if (targetServer.isBlank()) return
        if (title.isBlank() && text.isBlank()) return

        scope.launch {
            val result = apiClient.sendNotification(
                baseUrl = targetServer,
                appName = targetAppNames[packageName] ?: packageName,
                packageName = packageName,
                title = title,
                body = text
            )
            val ok = result.isSuccess
            if (ok) {
                val current = _notificationsFlow.value.toMutableList()
                val idx = current.indexOfFirst { it === entry }
                if (idx >= 0) {
                    current[idx] = entry.copy(sent = true)
                    _notificationsFlow.value = current
                }
            }
            Log.d(TAG, "sendNotification: ${if (ok) "OK" else result.exceptionOrNull()?.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun startForegroundSafe() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    DoorbellDetectorApp.FOREGROUND_CHANNEL_ID,
                    "Doorbell Detector",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }

            val label = if (targetPackages.isNotEmpty()) {
                targetPackages.size.toString() + " app(s): " + targetPackages.take(3).joinToString(", ") {
                    targetAppNames[it] ?: it
                } + if (targetPackages.size > 3) "..." else ""
            } else {
                "ninguna app"
            }
            val notification = NotificationCompat.Builder(this, DoorbellDetectorApp.FOREGROUND_CHANNEL_ID)
                .setContentTitle("Doorbell Detector")
                .setContentText("Escuchando notificaciones de $label")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()

            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground OK")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun loadFromDataStore() {
        scope.launch {
            try {
                val pm = PreferencesManager(this@NotificationListener)
                if (targetPackages.isEmpty()) {
                    targetPackages = pm.selectedPackages.first()
                }
                if (targetAppNames.isEmpty()) {
                    targetAppNames = pm.selectedAppNames.first()
                }
                if (targetServer.isBlank()) {
                    targetServer = pm.apiUrl.first()
                }
                Log.d(TAG, "loadFromDataStore: pkgs=$targetPackages server=$targetServer")
            } catch (e: Exception) {
                Log.e(TAG, "loadFromDataStore failed", e)
            }
        }
    }

    private fun observePreferences() {
        scope.launch {
            try {
                val pm = PreferencesManager(this@NotificationListener)
                pm.selectedPackages.collect { value ->
                    targetPackages = value
                    startForegroundSafe()
                    Log.d(TAG, "selectedPackages changed: $value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "observe selectedPackages failed", e)
            }
        }
        scope.launch {
            try {
                val pm = PreferencesManager(this@NotificationListener)
                pm.selectedAppNames.collect { value ->
                    targetAppNames = value
                    Log.d(TAG, "selectedAppNames changed: $value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "observe selectedAppNames failed", e)
            }
        }
        scope.launch {
            try {
                val pm = PreferencesManager(this@NotificationListener)
                pm.apiUrl.collect { value ->
                    targetServer = value
                    Log.d(TAG, "apiUrl changed: $value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "observe apiUrl failed", e)
            }
        }
    }
}
