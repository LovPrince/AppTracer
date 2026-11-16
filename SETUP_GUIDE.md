# 👑 GhostTrack - Setup & Usage Guide
### Engineered by Princeyy

---

## 📱 How to Run on an Android Phone

### Option A: Open in Android Studio (Recommended)
1. Launch **Android Studio**.
2. Click **Open** and select the folder:
   `c:\Users\HP\OneDrive\Desktop\Princeyy-App-Ghost-Tracker\android-app`
3. Wait for Gradle sync to complete.
4. Connect your Android phone via USB (or start an Android Emulator).
5. Click **Run (Shift + F10)** to install and launch GhostTrack on your device.

---

## 🔑 Crucial Step on the Phone: Grant "Usage Access"
Android safeguards system usage statistics behind a special permission called **Usage Access** (`PACKAGE_USAGE_STATS`).

1. When you first launch GhostTrack, an alert card will appear:
   `⚠️ Usage Access Permission Required`
2. Tap **"Enable Usage Access"** (or open phone Settings ➔ Apps ➔ Special app access ➔ Usage Access).
3. Find **GhostTrack** in the list and toggle the switch to **Allow**.
4. Switch back to GhostTrack and tap **"Scan History"**.

---

## 🔬 How GhostTrack Uncovers Uninstalled Apps

| Stage | What Happens in Android |
|---|---|
| **1. Historical Logging** | Even before GhostTrack was installed, Android's internal `usagestats` service was logging activity timestamps (`ACTIVITY_RESUMED`, `ACTIVITY_PAUSED`) for every app launched. |
| **2. Uninstallation** | When an app is uninstalled, the app package and its `/data/` folder are removed, but the historical logs in `/data/system/usagestats/` remain intact across the query window. |
| **3. Detection** | GhostTrack queries `UsageStatsManager` for the chosen time window (Past 24H, 7D, 30D, 1Y) and compares the resulting package list with `PackageManager.getInstalledPackages(0)`. |
| **4. Classification** | Packages with recorded foreground time that are **missing from `PackageManager`** are flagged as **👻 UNINSTALLED (GHOST APPS)**. |

---

## 💻 Option B: Immediate Browser Preview
To see the interactive UI right now on your PC:
1. Double click `web-dashboard/index.html` or run:
   ```powershell
   python start_preview.py
   ```
2. You can test the interactive UI, filter ghost apps, inspect session counts, and test ADB log parsing!
