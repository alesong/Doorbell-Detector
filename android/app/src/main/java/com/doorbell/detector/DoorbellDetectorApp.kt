package com.doorbell.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DoorbellDetectorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Doorbell Detector",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val FOREGROUND_CHANNEL_ID = "doorbell_foreground"
    }
}
