package com.doorbell.detector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.doorbell.detector.service.NotificationListener

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, starting service")
            val serviceIntent = Intent(context, NotificationListener::class.java).apply {
                putExtra("start_from_ui", false)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Service started after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service after boot", e)
            }
        }
    }
}
