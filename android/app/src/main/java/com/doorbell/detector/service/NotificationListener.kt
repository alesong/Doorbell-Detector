package com.doorbell.detector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    val sent: Boolean = false,
    val error: String? = null
)

data class TraceEntry(
    val timestamp: String,
    val event: String,
    val detail: String
)

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val MAX_ENTRIES = 20
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val POLL_INTERVAL_MS = 3_000L

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

        private val _traceFlow = MutableStateFlow<List<TraceEntry>>(emptyList())
        val traceFlow: StateFlow<List<TraceEntry>> = _traceFlow

        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())



        fun setTargets(packages: Set<String>, names: Map<String, String>, serverUrl: String) {
            targetPackages = packages
            targetAppNames = names
            targetServer = serverUrl
            Log.d(TAG, "setTargets: pkgs=$packages server=$serverUrl")
            addTrace("CONFIG", "targets=$packages server=$serverUrl")
        }

        fun clearNotifications() {
            _notificationsFlow.value = emptyList()
        }

        private fun addTrace(event: String, detail: String) {
            val ts = timeFormat.format(Date())
            _traceFlow.value = listOf(TraceEntry(ts, event, detail)) + _traceFlow.value.take(99)
        }

        fun clearTrace() {
            _traceFlow.value = emptyList()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiClient = ApiClient()



    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        addTrace("LIFECYCLE", "onCreate")

        loadFromDataStore()
        observePreferences()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        Log.d(TAG, "onBind")
        addTrace("LIFECYCLE", "onBind")
        return super.onBind(intent)
    }

    override fun onListenerConnected() {
        Log.d(TAG, "onListenerConnected")
        isConnected = true

        loadFromDataStore()
        addTrace("LIFECYCLE", "onListenerConnected — LISTENER ACTIVO")


    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "onListenerDisconnected")
        isConnected = false
        addTrace("LIFECYCLE", "onListenerDisconnected — listener perdido")
        try {
            requestRebind(ComponentName(this, NotificationListener::class.java))
            addTrace("LIFECYCLE", "requestRebind() en onListenerDisconnected")
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        isConnected = false


        addTrace("LIFECYCLE", "onDestroy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        addTrace("EVENT", "onNotificationPosted ${sbn.packageName} id=${sbn.id}")
        processNotificationEntry(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        addTrace("EVENT", "onNotificationRemoved ${sbn.packageName} id=${sbn.id}")
    }

    private fun processNotificationEntry(sbn: StatusBarNotification) {
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

        if (targetPackages.isEmpty() || packageName !in targetPackages) {
            Log.d(TAG, "filtered out: pkg=$packageName targets=$targetPackages")
            addTrace("FILTER", "BLOQUEADO pkg=$packageName (targets=$targetPackages)")
            return
        }
        if (targetServer.isBlank()) {
            addTrace("FILTER", "BLOQUEADO — servidor no configurado")
            return
        }
        if (title.isBlank() && text.isBlank()) return

        addTrace("HTTP", "POST ${packageName} title='$title' INICIO")

        scope.launch {
            val startMs = System.currentTimeMillis()
            try {
                val result = apiClient.sendNotification(
                    baseUrl = targetServer,
                    appName = targetAppNames[packageName] ?: packageName,
                    packageName = packageName,
                    title = title,
                    body = text
                )
                val duration = System.currentTimeMillis() - startMs
                val ok = result.isSuccess
                val current = _notificationsFlow.value.toMutableList()
                val idx = current.indexOfFirst { it === entry }
                if (idx >= 0) {
                    current[idx] = entry.copy(
                        sent = ok,
                        error = if (ok) null else result.exceptionOrNull()?.message
                    )
                    _notificationsFlow.value = current
                }
                addTrace("HTTP", "POST ${if (ok) "OK" else "FAIL"} ${duration}ms${if (!ok) " ${result.exceptionOrNull()?.message}" else ""}")
                Log.d(TAG, "sendNotification: ${if (ok) "OK" else result.exceptionOrNull()?.message} (${duration}ms)")
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startMs
                Log.e(TAG, "sendNotification exception", e)
                addTrace("HTTP", "POST EXCEPTION ${duration}ms: ${e.message}")
                val current = _notificationsFlow.value.toMutableList()
                val idx = current.indexOfFirst { it === entry }
                if (idx >= 0) {
                    current[idx] = entry.copy(error = e.message)
                    _notificationsFlow.value = current
                }
            }
        }
    }

    private fun loadFromDataStore() {
        scope.launch {
            try {
                val pm = PreferencesManager(this@NotificationListener)
                if (targetPackages.isEmpty()) {
                    targetPackages = pm.selectedPackages.first()
                    addTrace("CONFIG", "loaded selectedPackages=$targetPackages")
                }
                if (targetAppNames.isEmpty()) {
                    targetAppNames = pm.selectedAppNames.first()
                }
                if (targetServer.isBlank()) {
                    targetServer = pm.apiUrl.first()
                    addTrace("CONFIG", "loaded targetServer=$targetServer")
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
            
                    addTrace("CONFIG", "selectedPackages changed: $value")
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
                    addTrace("CONFIG", "targetServer changed: $value")
                    Log.d(TAG, "apiUrl changed: $value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "observe apiUrl failed", e)
            }
        }
    }
}
