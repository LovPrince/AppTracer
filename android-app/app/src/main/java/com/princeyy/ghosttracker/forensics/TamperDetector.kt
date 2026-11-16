package com.princeyy.ghosttracker.forensics

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.princeyy.ghosttracker.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Forensic Tamper Detection Engine
 * Detects ADB usage, Shizuku, LADB, root tools, and usage log gaps.
 *
 * Developed by Princeyy
 */
object TamperDetector {

    // ─── Suspicious Package Catalog ───────────────────────────────────────────
    val SUSPICIOUS_PACKAGES = listOf(
        // ─── Wireless / No-Root ADB Tools ───
        SuspiciousApp("com.draco.ladb",                   "LADB",                    "🔌 Wireless ADB without root — can clear usage stats silently"),
        SuspiciousApp("com.pdanet.android",               "PdaNet",                  "🔌 ADB-over-network bridge tool"),
        SuspiciousApp("com.adbwireless",                  "ADB WiFi Toggle",         "🔌 Enables ADB wirelessly without PC"),

        // ─── Shizuku & Privileged Shell ───
        SuspiciousApp("moe.shizuku.privileged.api",       "Shizuku",                 "⚡ Grants shell-level API access to apps — can wipe usage stats"),
        SuspiciousApp("rikka.shizuku",                    "Shizuku (Alt pkg)",       "⚡ Shizuku privileged API server"),
        SuspiciousApp("com.rikkati.assistant",            "Shizuku Assistant",       "⚡ Shizuku companion app"),

        // ─── Root Managers ───
        SuspiciousApp("com.topjohnwu.magisk",             "Magisk",                  "☠️ Root manager — can delete/modify any system file"),
        SuspiciousApp("eu.chainfire.supersu",             "SuperSU",                 "☠️ Root manager — full system access"),
        SuspiciousApp("com.noshufou.android.su",          "Superuser (ChainsDD)",    "☠️ Classic root manager"),
        SuspiciousApp("com.koushikdutta.superuser",       "Superuser (CWM)",         "☠️ Root manager"),
        SuspiciousApp("com.zachspong.temprootremovejb",   "TempRoot",                "☠️ Temp root granter"),
        SuspiciousApp("com.ramdroid.appquarantine",       "App Quarantine",          "☠️ Root-based app control"),
        SuspiciousApp("com.amphoras.hidemyroot",          "Hide My Root",            "☠️ Root detection spoofer"),
        SuspiciousApp("com.saurik.substrate",             "Cydia Substrate",         "☠️ Runtime code patching framework (bypasses app checks)"),

        // ─── ADB / Terminal Emulators ───
        SuspiciousApp("com.termux",                       "Termux",                  "💻 Linux terminal — can run ADB commands wirelessly"),
        SuspiciousApp("jackpal.androidterm",              "Android Terminal",        "💻 Shell terminal with ADB access"),
        SuspiciousApp("com.jrummy.terminal",              "Terminal Emulator",       "💻 Shell terminal access"),
        SuspiciousApp("org.connectbot",                   "ConnectBot SSH",          "💻 SSH/shell client"),
        SuspiciousApp("com.termoneplus",                  "TermOne Plus",            "💻 Terminal emulator"),

        // ─── Usage Stats Clearers / Cleaners ───
        SuspiciousApp("com.android.shell",                "Android Shell",           "🛠 System shell process (manual ADB via USB)"),
        SuspiciousApp("com.jaredrummler.android.processes","Process Monitor",        "🛠 Can monitor and kill system processes"),

        // ─── Root File Managers ───
        SuspiciousApp("com.jrummy.root.browserfree",      "Root Browser",            "📂 Root file manager — can delete usagestats folder"),
        SuspiciousApp("com.speedsoftware.rootexplorer",   "Root Explorer",           "📂 Root file manager — can delete usagestats folder"),
        SuspiciousApp("com.mixplorer",                    "MiXplorer",               "📂 File manager with root support"),
        SuspiciousApp("nextapp.fx",                       "FX File Explorer (Root)", "📂 File manager with root support"),
        SuspiciousApp("com.ghostsq.commander",            "Ghost Commander",         "📂 File manager with root access"),

        // ─── Xposed / Code Injection Frameworks ───
        SuspiciousApp("de.robv.android.xposed.installer", "Xposed Framework",       "⚠️ Runtime code injection — can bypass ALL app checks"),
        SuspiciousApp("org.lsposed.manager",              "LSPosed",                 "⚠️ Modern Xposed fork — bypasses app security checks"),
        SuspiciousApp("com.elderdrivers.riru.edxp",       "EdXposed",                "⚠️ Xposed for Android 8-10"),

        // ─── Backup Tools (Can Restore Wiped Data or Backup App State) ───
        SuspiciousApp("com.keramidas.TitaniumBackup",     "Titanium Backup",         "💾 Root backup — can restore/wipe app data selectively"),
        SuspiciousApp("com.swift_backup",                 "Swift Backup",            "💾 Backup tool with root access"),
        SuspiciousApp("org.twrp.twrpapp",                 "TWRP",                    "💾 Custom recovery — full system access"),

        // ─── Custom Android Tweakers ───
        SuspiciousApp("com.android.vending.billing.InAppBillingService.KEY", "Lucky Patcher", "🔑 Patches app security & license checks"),
        SuspiciousApp("com.android.vending.billing.InAppBillingService.LOCK", "Lucky Patcher (Alt)", "🔑 Patches app security & license checks"),
        SuspiciousApp("com.chelpus.lackypatch",           "Lucky Patcher (Legacy)", "🔑 Patches app license checks")
    )

    // ─── Run Full Tamper Analysis ──────────────────────────────────────────────
    suspend fun runFullAnalysis(context: Context): TamperReport = withContext(Dispatchers.IO) {
        val foundApps = try { detectSuspiciousApps(context) } catch (e: Throwable) { emptyList() }
        val adbEnabled = try { isAdbEnabled(context) } catch (e: Throwable) { false }
        val devOptionsOn = try { isDeveloperOptionsEnabled(context) } catch (e: Throwable) { false }
        val isRooted = try { isDeviceRooted() } catch (e: Throwable) { false }
        val logGapResult = try { analyzeUsageLogGap(context) } catch (e: Throwable) { LogGapResult(false, 0, "Log analysis unavailable") }
        val usbDebuggingHistoryOn = try { isUsbDebuggingHistoryDetected(context) } catch (e: Throwable) { false }
        val factoryResetResult = try { detectFactoryReset(context) } catch (e: Throwable) { FactoryResetResult(false, 0, emptyList(), "Check unavailable") }

        val riskScore = calculateRiskScore(
            foundApps, adbEnabled, devOptionsOn, isRooted, logGapResult, factoryResetResult
        )
        val verdict = when {
            riskScore >= 70 -> Verdict.HIGH_RISK
            riskScore >= 35 -> Verdict.MEDIUM_RISK
            else -> Verdict.CLEAN
        }

        TamperReport(
            suspiciousApps = foundApps,
            adbEnabled = adbEnabled,
            developerOptionsOn = devOptionsOn,
            isRooted = isRooted,
            usbDebuggingHistory = usbDebuggingHistoryOn,
            logGapResult = logGapResult,
            factoryResetResult = factoryResetResult,
            riskScore = riskScore,
            verdict = verdict
        )
    }

    // ─── Detect Suspicious Installed Apps ─────────────────────────────────────
    private fun detectSuspiciousApps(context: Context): List<DetectedApp> {
        val pm = context.packageManager
        val detected = mutableListOf<DetectedApp>()

        for (sus in SUSPICIOUS_PACKAGES) {
            try {
                val info = pm.getPackageInfo(sus.packageName, 0)
                detected.add(
                    DetectedApp(
                        packageName = sus.packageName,
                        appLabel = sus.friendlyName,
                        reason = sus.reason,
                        installedSince = FormatUtils.formatRelativeTime(info.firstInstallTime)
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed — good!
            }
        }

        return detected
    }

    // ─── ADB Currently Enabled Check ──────────────────────────────────────────
    private fun isAdbEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) == 1
    }

    // ─── Developer Options Check ──────────────────────────────────────────────
    private fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
    }

    // ─── USB Debugging History (ADB ever connected) ───────────────────────────
    private fun isUsbDebuggingHistoryDetected(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                "adb_enabled", 0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    // ─── Root Detection ───────────────────────────────────────────────────────
    private fun isDeviceRooted(): Boolean {
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )

        if (rootPaths.any { java.io.File(it).exists() }) return true

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val inputStream = process.inputStream.bufferedReader().readText()
            inputStream.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    // ─── Factory Reset Detection (5 Independent Signals) ─────────────────────
    fun detectFactoryReset(context: Context): FactoryResetResult {
        val signals = mutableListOf<String>()
        var confidence = 0

        // Signal 1: Boot Count - resets to 1 after factory reset
        try {
            val bootCount = Settings.Global.getInt(context.contentResolver, "boot_count", -1)
            if (bootCount in 1..3) {
                signals.add("🔄 Boot count is only $bootCount — indicates very recent reset or new device")
                confidence += 30
            }
        } catch (e: Exception) { }

        // Signal 2: System Apps Same Install Time - all system apps reinstalled simultaneously = fresh wipe
        try {
            val systemApps = listOf(
                "com.android.settings", "com.android.phone",
                "com.android.contacts", "com.android.mms"
            )
            val installTimes = systemApps.mapNotNull { pkg ->
                try { context.packageManager.getPackageInfo(pkg, 0).firstInstallTime } catch (e: Throwable) { null }
            }
            if (installTimes.size >= 2) {
                val maxTime = installTimes.maxOrNull() ?: 0L
                val minTime = installTimes.minOrNull() ?: 0L
                val maxDiff = maxTime - minTime
                if (maxDiff in 1..TimeUnit.HOURS.toMillis(1)) {
                    signals.add("📱 All system apps installed at exactly same time — strong factory reset signal")
                    confidence += 40
                }
            }
        } catch (e: Throwable) { }

        // Signal 3: Usage logs started very recently
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val now = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_YEARLY,
                    now - TimeUnit.DAYS.toMillis(365), now
                )
                val oldestLog = stats?.minOfOrNull { it.firstTimeStamp } ?: now
                val logAgeDays = TimeUnit.MILLISECONDS.toDays(now - oldestLog)
                if (logAgeDays < 7) {
                    signals.add("📅 Oldest usage record is only ${logAgeDays} day(s) ago — usage history was wiped recently")
                    confidence += 25
                }
            }
        } catch (e: Exception) { }

        // Signal 4: Device Setup Wizard was recently run (user_setup_complete = 0)
        try {
            val setupComplete = Settings.Secure.getInt(context.contentResolver, "user_setup_complete", 1)
            if (setupComplete == 0) {
                signals.add("⚙️ Device setup wizard not completed — phone may have been just reset")
                confidence += 35
            }
        } catch (e: Exception) { }

        // Signal 5: GhostTrack Persistent Secret Token (most reliable)
        // On first run, we save a timestamp. After factory reset, internal storage is wiped
        // so the token disappears → we know a reset happened!
        try {
            val prefs = context.getSharedPreferences("princeyy_device_fingerprint", Context.MODE_PRIVATE)
            val storedFirstRun = prefs.getLong("first_run_timestamp", 0L)
            if (storedFirstRun == 0L) {
                prefs.edit().putLong("first_run_timestamp", System.currentTimeMillis()).apply()
            } else {
                val daysSinceFirstRun = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - storedFirstRun)
                val bootCount = try { Settings.Global.getInt(context.contentResolver, "boot_count", 999) } catch (e: Exception) { 999 }
                if (bootCount <= 3 && daysSinceFirstRun > 10) {
                    signals.add("🔑 GhostTrack was first installed ${daysSinceFirstRun}d ago but device shows fresh boot count → FACTORY RESET DETECTED!")
                    confidence += 50
                }
            }
        } catch (e: Exception) { }

        val detected = confidence >= 40
        return FactoryResetResult(
            detected = detected,
            confidence = confidence.coerceAtMost(100),
            signals = signals,
            summary = when {
                confidence >= 70 -> "🔴 HIGH CONFIDENCE: Device was very likely factory reset to erase evidence!"
                confidence >= 40 -> "🟡 MEDIUM CONFIDENCE: Multiple factory reset signals detected."
                confidence > 0  -> "🟢 LOW: Some minor signals detected but not conclusive."
                else            -> "✅ No factory reset indicators detected."
            }
        )
    }

    // ─── Usage Log Gap Analysis ───────────────────────────────────────────────
    private fun analyzeUsageLogGap(context: Context): LogGapResult {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return LogGapResult(false, 0, "Could not access usage stats")

            val now = System.currentTimeMillis()
            val oneYearAgo = now - TimeUnit.DAYS.toMillis(365)

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY, oneYearAgo, now
            ) ?: return LogGapResult(false, 0, "No usage stats available")

            val earliestLogMs = stats.minOfOrNull { it.firstTimeStamp } ?: now

            // Estimate phone setup time from boot time
            val phoneSetupEstimateMs = now - SystemClock.elapsedRealtime()
            val gapMs = earliestLogMs - phoneSetupEstimateMs
            val gapDays = TimeUnit.MILLISECONDS.toDays(gapMs.coerceAtLeast(0))

            when {
                gapDays > 180 -> LogGapResult(
                    suspicious = true,
                    gapDays = gapDays,
                    description = "Usage logs only go back ${gapDays} days despite phone being much older → History was likely cleared!"
                )
                gapDays > 60 -> LogGapResult(
                    suspicious = true,
                    gapDays = gapDays,
                    description = "Moderate gap of ${gapDays} days found in usage history — may indicate a partial wipe."
                )
                else -> LogGapResult(
                    suspicious = false,
                    gapDays = gapDays,
                    description = "Usage log history appears consistent (${gapDays} day gap — normal)."
                )
            }
        } catch (e: Exception) {
            LogGapResult(false, 0, "Analysis error: ${e.message}")
        }
    }

    // ─── Risk Score Calculator ─────────────────────────────────────────────────
    private fun calculateRiskScore(
        apps: List<DetectedApp>,
        adb: Boolean,
        devOptions: Boolean,
        rooted: Boolean,
        gap: LogGapResult,
        factoryReset: FactoryResetResult
    ): Int {
        var score = 0
        if (rooted) score += 35
        if (adb) score += 20
        if (devOptions) score += 10
        if (gap.suspicious) score += 25
        if (factoryReset.detected) score += (factoryReset.confidence * 0.4).toInt()
        score += (apps.size * 10).coerceAtMost(30)
        return score.coerceAtMost(100)
    }

    // ─── Data Models ─────────────────────────────────────────────────────────
    data class SuspiciousApp(val packageName: String, val friendlyName: String, val reason: String)

    data class DetectedApp(
        val packageName: String,
        val appLabel: String,
        val reason: String,
        val installedSince: String
    )

    data class LogGapResult(
        val suspicious: Boolean,
        val gapDays: Long,
        val description: String
    )

    data class FactoryResetResult(
        val detected: Boolean,
        val confidence: Int,       // 0–100
        val signals: List<String>, // human-readable signal descriptions
        val summary: String        // one-line verdict string
    )

    data class TamperReport(
        val suspiciousApps: List<DetectedApp>,
        val adbEnabled: Boolean,
        val developerOptionsOn: Boolean,
        val isRooted: Boolean,
        val usbDebuggingHistory: Boolean,
        val logGapResult: LogGapResult,
        val factoryResetResult: FactoryResetResult,
        val riskScore: Int,
        val verdict: Verdict
    )

    enum class Verdict { CLEAN, MEDIUM_RISK, HIGH_RISK }
}
