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
            Log.d(TAG, "Boot completed. The system will start NotificationListenerService automatically if permission is granted.")
        }
    }
}
