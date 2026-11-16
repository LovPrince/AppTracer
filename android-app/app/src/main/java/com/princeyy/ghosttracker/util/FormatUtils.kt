package com.princeyy.ghosttracker.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility functions for formatting durations and timestamps.
 *
 * Developed by Princeyy
 */
object FormatUtils {

    private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    private val DATE_TIME_EXACT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Formats duration in milliseconds to human-readable format:
     * e.g. "3h 42m 15s", "12m 4s", or "< 1s"
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0s"
        if (durationMs < 1000) return "< 1s"

        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Formats a timestamp into relative time ("3 hours ago", "Yesterday", etc.)
     */
    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "Never recorded"
        val now = System.currentTimeMillis()
        if (timestamp > now) return "Just now"

        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    /**
     * Formats a timestamp into full date & time (e.g. "Nov 14, 2026 at 08:30 PM")
     */
    fun formatExactDateTime(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        return DATE_FORMAT.format(Date(timestamp))
    }

    /**
     * Formats into ISO-like timestamp for export reports
     */
    fun formatIsoDateTime(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        return DATE_TIME_EXACT.format(Date(timestamp))
    }
}
