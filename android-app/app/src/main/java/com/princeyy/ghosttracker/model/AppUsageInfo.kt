package com.princeyy.ghosttracker.model

import android.graphics.drawable.Drawable

/**
 * Categorization of apps for forensic risk assessment.
 */
enum class AppCategory(val title: String, val badge: String, val colorHex: String) {
    SECRET_VAULT("Secret Vault / Hider", "🔒 VAULT", "#EF4444"),
    DATING("Dating & Hookup", "💘 DATING", "#EC4899"),
    SECRET_MESSAGING("Encrypted Chat", "💬 CHAT", "#8B5CF6"),
    TAMPER_TOOL("Tamper / Cleaner", "⚠️ TAMPER", "#F59E0B"),
    GENERAL("General", "", "#6B7280")
}

/**
 * Individual recorded foreground session timeline slice.
 */
data class UsageSession(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long
)

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
    val icon: Drawable? = null,
    val category: AppCategory = AppCategory.GENERAL,
    val sessions: List<UsageSession> = emptyList()
)
