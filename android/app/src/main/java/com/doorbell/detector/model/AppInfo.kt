package com.doorbell.detector.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSelected: Boolean = false
)
