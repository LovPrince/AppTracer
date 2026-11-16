# GhostTrack - Android Uninstalled App Forensics & Usage Tracker
### Created by Princeyy

An advanced Android application and forensic audit tool that detects and analyzes **uninstalled (ghost) apps** alongside currently installed apps.

---

## 🔍 How It Detects Uninstalled Apps (Even Before Installation)

A common belief is that an app cannot know what happened before it was installed. **On Android, that is not true!**

### The Core Technology: Android `UsageStatsManager`
1. The Android OS runs a privileged system service called `usagestats` (located in `/data/system/usagestats/`).
2. This service continuously logs package events (`ACTIVITY_RESUMED`, `ACTIVITY_PAUSED`, timestamps, foreground screen time, and session intervals) for **every application used on the device**.
3. **When an application is uninstalled**, Android deletes the app's APK and private data directory (`/data/data/<package>`), **but the historical usage statistics in the system database are preserved** across query windows (daily, weekly, monthly, and yearly intervals).
4. When **GhostTrack by Princeyy** is installed and granted the standard **Usage Access** permission (`android.permission.PACKAGE_USAGE_STATS`), it queries the system's historical logs (`UsageStatsManager.queryUsageStats` and `UsageStatsManager.queryEvents`).
5. The app retrieves all packages that recorded activity within the chosen timeframe and cross-checks each package name against `PackageManager.getInstalledPackages(0)`.
6. Any package with recorded foreground activity that is **absent from `PackageManager`** is flagged as an **UNINSTALLED GHOST APP**!

---

## ⚡ Features
- 👻 **Detects Recently Uninstalled Apps**: Finds apps used and uninstalled before or after GhostTrack was installed.
- ⏱️ **Foreground Time (`fg time`)**: Exact screen time in hours, minutes, and seconds.
- 📅 **Last Time Used**: Exact date and time the app was last opened before uninstallation.
- 🚀 **First Time Used**: Earliest timestamp recorded in the Android system database.
- 🔄 **Session Counter**: Total launches and active sessions calculated from raw `ACTIVITY_RESUMED` events.
- ⏳ **Average Session Duration**: Calculates typical usage session length.
- 🔍 **Search & Filters**: Filter by `Uninstalled Only (Ghosts)`, `Installed`, or `All Apps`.
- 📊 **Flexible Time Ranges**: Past 24 hours, 7 days, 30 days, or 1 year.
- 👑 **Interactive UI with "Made by Princeyy"** branding, dark neon forensic dashboard, and detailed app cards.
- 📤 **Export Forensic Audit Report**: Export results to JSON or formatted audit text.
