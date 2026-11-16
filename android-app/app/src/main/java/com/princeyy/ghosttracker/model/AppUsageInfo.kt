package com.princeyy.ghosttracker.model

import android.graphics.drawable.Drawable

/**
 * Represents comprehensive usage forensics for an application,
 * whether it is currently installed or was uninstalled.
 *
 * Developed by Princeyy
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val isInstalled: Boolean,
    val totalForegroundTimeMs: Long,
    val firstTimeStamp: Long,
    val lastTimeUsed: Long,
    val sessionCount: Int,
    val avgSessionDurationMs: Long = if (sessionCount > 0) totalForegroundTimeMs / sessionCount else 0L,
    val icon: Drawable? = null
)
