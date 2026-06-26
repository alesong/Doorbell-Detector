package com.notifsender.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LogEntry(
    val seq: Int,
    val id: String,
    val timestamp: String,
    val timeMs: Long
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var seq by remember { mutableIntStateOf(0) }
    val log = remember { mutableStateListOf<LogEntry>() }
    var loopRunning by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun sendNotif() {
        seq++
        val notifId = seq
        val id = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val ts = timeFormat.format(Date(now))

        val channelId = "notifsender_test"
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Test Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val body = "{\"id\":\"ntf_$notifId\",\"seq\":$notifId,\"ts\":\"$ts\",\"app\":\"NotifSender\",\"uuid\":\"$id\"}"

        val notification = Notification.Builder(context, channelId)
            .setContentTitle("Test-$notifId")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)

        log.add(0, LogEntry(notifId, id, ts, now))
        if (log.size > 100) log.removeAt(log.lastIndex)
    }

    val loopRunnable = remember {
        object : Runnable {
            override fun run() {
                if (loopRunning) {
                    sendNotif()
                    handler.postDelayed(this, 3000)
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "NotifSender",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "App de prueba para enviar notificaciones trazables",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Controles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { sendNotif() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enviar notificación #${seq + 1}") }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                for (i in 1..5) {
                                    sendNotif()
                                    Thread.sleep(300)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Ráfaga 5") }

                        Button(
                            onClick = {
                                loopRunning = !loopRunning
                                if (loopRunning) {
                                    handler.post(loopRunnable)
                                } else {
                                    handler.removeCallbacks(loopRunnable)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (loopRunning) "Detener loop" else "Loop 3s")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { log.clear() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Limpiar log") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Log de envíos (${log.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (log.isEmpty()) {
                        Text(
                            text = "Presiona un botón para enviar una notificación",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(log) { entry ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "#${entry.seq}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "id=ntf_${entry.seq} uuid=${entry.id}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cómo usar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Abre Doorbell Detector → selecciona com.notifsender\n" +
                                "2. Abre la web del servidor (localhost:3000)\n" +
                                "3. Vuelve aquí y presiona 'Enviar'\n" +
                                "4. Revisa los logs en Doorbell Detector → Trazabilidad",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
