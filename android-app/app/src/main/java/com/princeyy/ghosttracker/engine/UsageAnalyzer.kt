package com.princeyy.ghosttracker.engine

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import com.princeyy.ghosttracker.model.AppUsageInfo
import com.princeyy.ghosttracker.util.KnownAppsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Core Forensic Engine for detecting Uninstalled (Ghost) and Installed apps
 * from Android's system-wide UsageStatsManager service.
 *
 * Designed and engineered by Princeyy.
 */
class UsageAnalyzer(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Checks whether the user has granted Usage Access permission
     * (PACKAGE_USAGE_STATS) via Settings -> Special App Access.
     */
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Scans Android system usage database between startTime and endTime.
     * Identifies apps that ran on the phone but are no longer installed.
     *
     * @param startTime Beginning timestamp in milliseconds
     * @param endTime Ending timestamp in milliseconds (typically System.currentTimeMillis())
     * @return List of AppUsageInfo sorted by lastTimeUsed descending
     */
    suspend fun analyzeAppUsage(
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): List<AppUsageInfo> = withContext(Dispatchers.IO) {
        if (!hasUsagePermission() || usageStatsManager == null) {
            return@withContext emptyList()
        }

        // 1. Gather all currently installed packages on the device
        val installedPackageMap = mutableMapOf<String, Pair<String, Drawable?>>()
        try {
            val installedList = packageManager.getInstalledPackages(0)
            for (pkg in installedList) {
                try {
                    val appInfo = pkg.applicationInfo ?: continue
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    installedPackageMap[pkg.packageName] = Pair(label, icon)
                } catch (e: Exception) {
                    installedPackageMap[pkg.packageName] = Pair(pkg.packageName, null)
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        // 2. Query aggregated UsageStats
        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        ) ?: emptyList()

        // Helper tracker per package
        class PackageAccumulator(
            val packageName: String,
            var totalTimeMs: Long = 0L,
            var firstTimeStamp: Long = Long.MAX_VALUE,
            var lastTimeUsed: Long = 0L,
            var sessionCount: Int = 0
        )

        val accumulators = mutableMapOf<String, PackageAccumulator>()

        for (stat in statsList) {
            val pkg = stat.packageName ?: continue
            val item = accumulators.getOrPut(pkg) { PackageAccumulator(pkg) }
            item.totalTimeMs += stat.totalTimeInForeground
            if (stat.firstTimeStamp in 1..<item.firstTimeStamp) {
                item.firstTimeStamp = stat.firstTimeStamp
            }
            if (stat.lastTimeUsed > item.lastTimeUsed) {
                item.lastTimeUsed = stat.lastTimeUsed
            }
        }

        // 3. Query fine-grained UsageEvents to compute exact sessions & recent uninstalled app activity
        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            val resumeTimestamps = mutableMapOf<String, Long>()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val item = accumulators.getOrPut(pkg) { PackageAccumulator(pkg) }

                if (event.timeStamp in 1..<item.firstTimeStamp) {
                    item.firstTimeStamp = event.timeStamp
                }
                if (event.timeStamp > item.lastTimeUsed) {
                    item.lastTimeUsed = event.timeStamp
                }

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        item.sessionCount++
                        resumeTimestamps[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = resumeTimestamps.remove(pkg)
                        if (start != null && event.timeStamp > start) {
                            val duration = event.timeStamp - start
                            // If statsList didn't capture foreground time, use event duration
                            if (item.totalTimeMs == 0L) {
                                item.totalTimeMs += duration
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Some devices might throw SecurityException if events are restricted
        }

        // 4. Construct final list: cross-reference with installed apps
        val result = mutableListOf<AppUsageInfo>()

        for ((packageName, acc) in accumulators) {
            // Filter out internal system framework packages without actual app usage
            if (acc.totalTimeMs == 0L && acc.sessionCount == 0 && acc.lastTimeUsed == 0L) {
                continue
            }

            // Exclude GhostTrack itself from the forensics list
            if (packageName == context.packageName) {
                continue
            }

            val installedInfo = installedPackageMap[packageName]
            val isInstalled = installedInfo != null

            val appName: String
            val icon: Drawable?

            if (isInstalled) {
                appName = installedInfo!!.first
                icon = installedInfo.second
            } else {
                // UNINSTALLED APP: Package metadata is gone from OS, resolve via KnownAppsCatalog
                appName = KnownAppsCatalog.resolveAppName(packageName)
                icon = null
            }

            val cleanFirstTime = if (acc.firstTimeStamp == Long.MAX_VALUE) acc.lastTimeUsed else acc.firstTimeStamp

            result.add(
                AppUsageInfo(
                    packageName = packageName,
                    appName = appName,
                    isInstalled = isInstalled,
                    totalForegroundTimeMs = acc.totalTimeMs,
                    firstTimeStamp = cleanFirstTime,
                    lastTimeUsed = acc.lastTimeUsed,
                    sessionCount = maxOf(acc.sessionCount, if (acc.totalTimeMs > 0) 1 else 0),
                    icon = icon
                )
            )
        }

        // Default sort: Uninstalled apps with high activity first, then by last time used
        result.sortedWith(
            compareByDescending<AppUsageInfo> { !it.isInstalled }
                .thenByDescending { it.lastTimeUsed }
        )
    }
}
