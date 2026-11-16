package com.princeyy.ghosttracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.princeyy.ghosttracker.databinding.ActivityMainBinding
import com.princeyy.ghosttracker.engine.UsageAnalyzer
import com.princeyy.ghosttracker.forensics.VaultFileScanner
import com.princeyy.ghosttracker.forensics.VaultFolderTrace
import com.princeyy.ghosttracker.license.DiscordNotifier
import com.princeyy.ghosttracker.license.LicenseManager
import com.princeyy.ghosttracker.model.AppCategory
import com.princeyy.ghosttracker.model.AppUsageInfo
import com.princeyy.ghosttracker.ui.AppAdapter
import com.princeyy.ghosttracker.util.FormatUtils
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Main Forensic Dashboard Activity with HWID & Discord License Protection.
 * Auto-locks once approved time or scan limits expire.
 *
 * Developed and Designed by Princeyy
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var analyzer: UsageAnalyzer
    private lateinit var adapter: AppAdapter

    private var currentFilterMode = AppAdapter.FilterMode.UNINSTALLED_ONLY
    private var currentQuery = ""
    private var currentTimeWindowDays = 7
    private var currentDataset = listOf<AppUsageInfo>()
    private var currentVaultTraces = listOf<VaultFolderTrace>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyzer = UsageAnalyzer(this)

        setupRecyclerView()
        setupTabs()
        setupTimeChips()
        setupSearch()
        setupActions()
        setupLicenseGate()
    }

    override fun onResume() {
        super.onResume()
        checkLicenseAndProceed()
    }

    private fun checkLicenseAndProceed() {
        val isLicensed = LicenseManager.isAccessAllowed(this)
        if (!isLicensed) {
            showLicenseLockScreen()
        } else {
            hideLicenseLockScreen()
            updateLicenseStatusBanner()
            checkPermissionAndScan()
        }
    }

    private fun setupLicenseGate() {
        val gateView = binding.viewLicenseGate.root
        val tvHwid = gateView.findViewById<TextView>(R.id.tvDeviceHwid)
        val btnCopy = gateView.findViewById<Button>(R.id.btnCopyHwid)
        val etDiscord = gateView.findViewById<EditText>(R.id.etDiscordTag)
        val btnDiscord = gateView.findViewById<Button>(R.id.btnRequestDiscord)
        val etKey = gateView.findViewById<EditText>(R.id.etLicenseKey)
        val btnActivate = gateView.findViewById<Button>(R.id.btnActivateKey)

        val hwid = LicenseManager.getDeviceHwid(this)
        tvHwid.text = hwid

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Device HWID", hwid))
            Toast.makeText(this, "Device HWID copied! Send it to Princeyy.", Toast.LENGTH_SHORT).show()
        }

        btnDiscord.setOnClickListener {
            val tag = etDiscord.text.toString().trim()
            lifecycleScope.launch {
                val sentWebhook = DiscordNotifier.sendDiscordWebhookRequest(hwid, tag, "Standard Access")
                if (!sentWebhook) {
                    // If no webhook configured, open Discord directly
                    DiscordNotifier.openDiscord(this@MainActivity)
                } else {
                    Toast.makeText(this@MainActivity, "Access request dispatched to Princeyy on Discord!", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnActivate.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter your license key.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (val result = LicenseManager.activateKey(this, key)) {
                is LicenseManager.ActivationResult.Success -> {
                    Toast.makeText(this, "🎉 Activated! Welcome to GhostTrack VIP.", Toast.LENGTH_LONG).show()
                    hideLicenseLockScreen()
                    updateLicenseStatusBanner()
                    checkPermissionAndScan()
                }
                is LicenseManager.ActivationResult.Error -> {
                    Toast.makeText(this, "❌ ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLicenseLockScreen() {
        binding.viewLicenseGate.root.isVisible = true
        binding.layoutLicenseStatus.isVisible = false
        binding.fabScan.isVisible = false
    }

    private fun hideLicenseLockScreen() {
        binding.viewLicenseGate.root.isVisible = false
        binding.layoutLicenseStatus.isVisible = true
        binding.fabScan.isVisible = true
    }

    private fun updateLicenseStatusBanner() {
        val remainingMs = LicenseManager.getRemainingTimeMs(this)
        val remainingUses = LicenseManager.getRemainingUses(this)

        val timeStr = when {
            remainingMs == -1L -> "👑 VIP Lifetime"
            remainingMs <= 0 -> "⚠️ Expired"
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(remainingMs)
                val hours = TimeUnit.MILLISECONDS.toHours(remainingMs) % 24
                if (days > 0) "⏳ ${days}d ${hours}h left" else "⏳ ${hours}h left"
            }
        }

        val usesStr = when {
            remainingUses == -1 -> "🔄 Unlimited Scans"
            remainingUses <= 0 -> "⚠️ 0 Scans Left"
            else -> "🔄 $remainingUses scans left"
        }

        binding.tvLicenseStatusText.text = timeStr
        binding.tvLicenseUsesText.text = usesStr
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter(this) { item ->
            showAppDetailDialog(item)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayoutFilter.addTab(binding.tabLayoutFilter.newTab().setText("👻 Uninstalled (0)"))
        binding.tabLayoutFilter.addTab(binding.tabLayoutFilter.newTab().setText("📱 Installed (0)"))
        binding.tabLayoutFilter.addTab(binding.tabLayoutFilter.newTab().setText("🔒 Vaults & Dating (0)"))
        binding.tabLayoutFilter.addTab(binding.tabLayoutFilter.newTab().setText("All (0)"))

        binding.tabLayoutFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilterMode = when (tab?.position) {
                    0 -> AppAdapter.FilterMode.UNINSTALLED_ONLY
                    1 -> AppAdapter.FilterMode.INSTALLED_ONLY
                    2 -> AppAdapter.FilterMode.VAULTS_AND_DATING
                    else -> AppAdapter.FilterMode.ALL
                }
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupTimeChips() {
        binding.chip24h.setOnClickListener { setTimeWindow(1) }
        binding.chip7d.setOnClickListener { setTimeWindow(7) }
        binding.chip30d.setOnClickListener { setTimeWindow(30) }
        binding.chip1y.setOnClickListener { setTimeWindow(365) }
    }

    private fun setTimeWindow(days: Int) {
        currentTimeWindowDays = days
        performForensicScan()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupActions() {
        binding.swipeRefresh.setOnRefreshListener {
            performForensicScan()
        }

        binding.fabScan.setOnClickListener {
            performForensicScan()
        }

        binding.btnGrantPermission.setOnClickListener {
            openUsageAccessSettings()
        }

        binding.btnExportReport.setOnClickListener {
            exportForensicReport()
        }

        binding.btnSyncDiscord.setOnClickListener {
            syncForensicsToDiscord(isManual = true)
        }

        binding.cardVaultDetection.setOnClickListener {
            showVaultTracesDialog()
        }

        binding.layoutPrinceyyBadge.setOnClickListener {
            showAboutPrinceyyDialog()
        }

        binding.btnOpenTamperDetection.setOnClickListener {
            startActivity(Intent(this, TamperDetectionActivity::class.java))
        }
    }

    private fun checkPermissionAndScan() {
        val hasPermission = analyzer.hasUsagePermission()
        binding.cardPermissionWarning.isVisible = !hasPermission
        if (hasPermission) {
            performForensicScan()
        } else {
            binding.progressBar.isVisible = false
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun performForensicScan() {
        // Enforce license check before performing scan
        if (!LicenseManager.isAccessAllowed(this)) {
            showLicenseLockScreen()
            Toast.makeText(this, "🔒 Your license time or scan limit has expired! Contact Princeyy.", Toast.LENGTH_LONG).show()
            return
        }

        if (!analyzer.hasUsagePermission()) {
            binding.cardPermissionWarning.isVisible = true
            return
        }

        // Consume 1 use/scan from license quota
        LicenseManager.recordUsage(this)
        updateLicenseStatusBanner()

        binding.progressBar.isVisible = true
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(currentTimeWindowDays.toLong())

        lifecycleScope.launch {
            try {
                val results = analyzer.analyzeAppUsage(startTime, endTime)
                val vaultTraces = VaultFileScanner.scanVaultTraces(this@MainActivity)
                currentDataset = results
                currentVaultTraces = vaultTraces
                adapter.setData(results)

                updateDashboard(results, vaultTraces)
                applyFilter()

                // Feature 5: Auto Discord Webhook Audit Sync
                syncForensicsToDiscord(isManual = false)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateDashboard(results: List<AppUsageInfo>, vaultTraces: List<VaultFolderTrace>) {
        val ghosts = results.filter { !it.isInstalled }
        val active = results.filter { it.isInstalled }
        val vaultsAndDating = results.filter {
            it.category == AppCategory.SECRET_VAULT ||
            it.category == AppCategory.DATING ||
            it.category == AppCategory.SECRET_MESSAGING
        }

        val ghostFgTotal = ghosts.sumOf { it.totalForegroundTimeMs }
        val activeFgTotal = active.sumOf { it.totalForegroundTimeMs }

        binding.tvGhostCount.text = ghosts.size.toString()
        binding.tvGhostTime.text = "${FormatUtils.formatDuration(ghostFgTotal)} screen time"

        binding.tvInstalledCount.text = active.size.toString()
        binding.tvActiveTime.text = "${FormatUtils.formatDuration(activeFgTotal)} screen time"

        binding.tabLayoutFilter.getTabAt(0)?.text = "👻 Uninstalled (${ghosts.size})"
        binding.tabLayoutFilter.getTabAt(1)?.text = "📱 Installed (${active.size})"
        binding.tabLayoutFilter.getTabAt(2)?.text = "🔒 Vaults (${vaultsAndDating.size})"
        binding.tabLayoutFilter.getTabAt(3)?.text = "All (${results.size})"

        // Check if any vaults/hiders or leftover storage traces were discovered
        val vaultApps = results.filter { it.category == AppCategory.SECRET_VAULT }
        if (vaultTraces.isNotEmpty() || vaultApps.isNotEmpty()) {
            binding.cardVaultDetection.isVisible = true
            binding.tvVaultWarningTitle.text = "🚨 ${vaultApps.size} Vault App(s) & ${vaultTraces.size} Storage Folder(s) Found!"
            binding.tvVaultWarningSubtitle.text = if (vaultTraces.isNotEmpty()) {
                "Storage traces: ${vaultTraces.take(3).joinToString { it.folderName }}"
            } else {
                "Apps: ${vaultApps.take(2).joinToString { it.appName }}"
            }
        } else {
            binding.cardVaultDetection.isVisible = false
        }
    }

    private fun applyFilter() {
        adapter.filter(currentQuery, currentFilterMode)
        val isEmpty = adapter.itemCount == 0
        binding.layoutEmpty.isVisible = isEmpty
        if (isEmpty) {
            binding.tvEmptyMessage.text = when (currentFilterMode) {
                AppAdapter.FilterMode.UNINSTALLED_ONLY -> "No uninstalled apps detected in the selected timeframe."
                AppAdapter.FilterMode.INSTALLED_ONLY -> "No active installed apps found."
                AppAdapter.FilterMode.VAULTS_AND_DATING -> "No hidden vaults, hiders, or dating apps found."
                AppAdapter.FilterMode.ALL -> "No apps match your search."
            }
        }
    }

    private fun showAppDetailDialog(item: AppUsageInfo) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_app_detail, null)
        dialog.setContentView(view)

        val tvAppName = view.findViewById<TextView>(R.id.tvDetailAppName)
        val tvPackage = view.findViewById<TextView>(R.id.tvDetailPackage)
        val tvCategory = view.findViewById<TextView>(R.id.tvDetailCategory)
        val tvStatus = view.findViewById<TextView>(R.id.tvDetailStatus)
        val tvFgTime = view.findViewById<TextView>(R.id.tvDetailFgTime)
        val tvSessions = view.findViewById<TextView>(R.id.tvDetailSessions)
        val tvAvgSession = view.findViewById<TextView>(R.id.tvDetailAvgSession)
        val tvLastUsed = view.findViewById<TextView>(R.id.tvDetailLastUsed)
        val tvFirstUsed = view.findViewById<TextView>(R.id.tvDetailFirstUsed)
        val btnPlayStore = view.findViewById<Button>(R.id.btnOpenPlayStore)
        val btnCopy = view.findViewById<Button>(R.id.btnCopySummary)
        val layoutTimeline = view.findViewById<android.widget.LinearLayout>(R.id.layoutTimelineContainer)
        val tvTimelineFallback = view.findViewById<TextView>(R.id.tvTimelineFallback)

        tvAppName.text = item.appName
        tvPackage.text = item.packageName

        // Category badge in dialog
        if (item.category != AppCategory.GENERAL) {
            tvCategory.text = "${item.category.badge} ${item.category.title}"
            tvCategory.setTextColor(android.graphics.Color.parseColor(item.category.colorHex))
            tvCategory.isVisible = true
        } else {
            tvCategory.isVisible = false
        }

        if (item.isInstalled) {
            tvStatus.text = "INSTALLED APP"
            tvStatus.setBackgroundResource(R.drawable.bg_badge_installed)
            tvStatus.setTextColor(getColor(R.color.active_green))
        } else {
            tvStatus.text = "👻 UNINSTALLED (GHOST)"
            tvStatus.setBackgroundResource(R.drawable.bg_badge_uninstalled)
            tvStatus.setTextColor(getColor(R.color.ghost_red_light))
        }

        tvFgTime.text = FormatUtils.formatDuration(item.totalForegroundTimeMs)
        tvSessions.text = "${item.sessionCount} launches"
        tvAvgSession.text = FormatUtils.formatDuration(item.avgSessionDurationMs)
        tvLastUsed.text = FormatUtils.formatExactDateTime(item.lastTimeUsed)
        tvFirstUsed.text = FormatUtils.formatExactDateTime(item.firstTimeStamp)

        // Feature 1: Forensic Launch Timeline
        if (item.sessions.isNotEmpty()) {
            tvTimelineFallback.isVisible = false
            item.sessions.take(10).forEach { session ->
                val sessionView = TextView(this)
                sessionView.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 4, 0, 4) }
                sessionView.textSize = 12f
                sessionView.setTextColor(getColor(R.color.text_secondary))
                val start = FormatUtils.formatExactDateTime(session.startTimeMs)
                val dur = FormatUtils.formatDuration(session.durationMs)
                sessionView.text = "🕒 $start  ⏱ $dur"
                layoutTimeline.addView(sessionView)
            }
            if (item.sessions.size > 10) {
                val moreView = TextView(this)
                moreView.textSize = 11f
                moreView.setTextColor(getColor(R.color.text_muted))
                moreView.text = "+ ${item.sessions.size - 10} more sessions…"
                layoutTimeline.addView(moreView)
            }
        } else {
            tvTimelineFallback.isVisible = true
        }

        btnPlayStore.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${item.packageName}"))
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${item.packageName}")))
            }
        }

        btnCopy.setOnClickListener {
            val summary = buildString {
                appendLine("═══ GhostTrack Forensics Report ═══")
                appendLine("App Name: ${item.appName}")
                appendLine("Package: ${item.packageName}")
                appendLine("Category: ${if (item.category != AppCategory.GENERAL) item.category.title else "General"}")
                appendLine("Status: ${if (item.isInstalled) "INSTALLED" else "UNINSTALLED (GHOST)"}")
                appendLine("Total Foreground Time: ${FormatUtils.formatDuration(item.totalForegroundTimeMs)}")
                appendLine("Total Sessions: ${item.sessionCount}")
                appendLine("Avg Session: ${FormatUtils.formatDuration(item.avgSessionDurationMs)}")
                appendLine("Last Used: ${FormatUtils.formatExactDateTime(item.lastTimeUsed)}")
                appendLine("First Seen: ${FormatUtils.formatExactDateTime(item.firstTimeStamp)}")
                if (item.sessions.isNotEmpty()) {
                    appendLine("--- Launch Timeline (Last ${minOf(item.sessions.size, 10)}) ---")
                    item.sessions.take(10).forEach { s ->
                        appendLine("  ${FormatUtils.formatExactDateTime(s.startTimeMs)} (${FormatUtils.formatDuration(s.durationMs)})")
                    }
                }
                appendLine("Generated by GhostTrack • Made by Princeyy")
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("App Forensics", summary))
            Toast.makeText(this, "Copied forensics report to clipboard!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showVaultTracesDialog() {
        if (currentVaultTraces.isEmpty()) {
            Toast.makeText(this, "No vault folder traces found in storage.", Toast.LENGTH_SHORT).show()
            return
        }
        val report = buildString {
            appendLine("🔒 Vault Storage Remnants Found:\n")
            currentVaultTraces.forEachIndexed { i, trace ->
                appendLine("${i + 1}. ${trace.folderName}")
                appendLine("   App: ${trace.detectedVaultType}")
                appendLine("   Path: ${trace.absolutePath}")
                appendLine("   Files: ${trace.fileCount}  |  Size: ${trace.totalSizeBytes / 1024}KB")
                appendLine("   Last Modified: ${FormatUtils.formatExactDateTime(trace.lastModifiedMs)}")
                appendLine()
            }
            appendLine("👑 GhostTrack • Made by Princeyy")
        }
        AlertDialog.Builder(this)
            .setTitle("🔒 Vault & Hider Folder Traces")
            .setMessage(report)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("VaultTraces", report))
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun syncForensicsToDiscord(isManual: Boolean) {
        lifecycleScope.launch {
            val hwid = LicenseManager.getDeviceHwid(this@MainActivity)
            val ghosts = currentDataset.filter { !it.isInstalled }
            val vaultApps = currentDataset.filter { it.category == AppCategory.SECRET_VAULT }
            val ghostFgTotal = ghosts.sumOf { it.totalForegroundTimeMs }

            val sent = DiscordNotifier.sendScanAuditReport(
                hwid = hwid,
                ghostCount = ghosts.size,
                ghostTimeFormatted = FormatUtils.formatDuration(ghostFgTotal),
                installedCount = currentDataset.filter { it.isInstalled }.size,
                timeWindowDays = currentTimeWindowDays,
                vaultApps = vaultApps,
                vaultTraces = currentVaultTraces,
                topGhosts = ghosts.take(6)
            )

            if (isManual) {
                if (sent) {
                    Toast.makeText(this@MainActivity, "✅ Forensics audit sent to Princeyy's Discord!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "⚠️ Could not reach Discord webhook. Check your connection.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportForensicReport() {
        val ghosts = currentDataset.filter { !it.isInstalled }
        val report = buildString {
            appendLine("═══════════════════════════════════════════")
            appendLine("      GHOSTTRACK UNINSTALLED APP AUDIT     ")
            appendLine("           Crafted by Princeyy             ")
            appendLine("═══════════════════════════════════════════")
            appendLine("Scan Timestamp: ${FormatUtils.formatIsoDateTime(System.currentTimeMillis())}")
            appendLine("Time Window: Past $currentTimeWindowDays days")
            appendLine("Total Uninstalled (Ghost) Apps: ${ghosts.size}")
            appendLine("Total Installed Apps Monitored: ${currentDataset.size - ghosts.size}")
            appendLine("───────────────────────────────────────────")
            appendLine("UNINSTALLED (GHOST) APPLICATIONS DETECTED:")
            appendLine("───────────────────────────────────────────")
            if (ghosts.isEmpty()) {
                appendLine("None detected in this timeframe.")
            } else {
                ghosts.forEachIndexed { index, app ->
                    appendLine("${index + 1}. ${app.appName} (${app.packageName})")
                    appendLine("   • Foreground Time: ${FormatUtils.formatDuration(app.totalForegroundTimeMs)}")
                    appendLine("   • Total Sessions: ${app.sessionCount}")
                    appendLine("   • Last Used: ${FormatUtils.formatExactDateTime(app.lastTimeUsed)}")
                    appendLine("   • First Seen: ${FormatUtils.formatExactDateTime(app.firstTimeStamp)}")
                    appendLine("")
                }
            }
            appendLine("═══════════════════════════════════════════")
            appendLine("End of Forensic Audit • Made by Princeyy")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Forensics Audit Report")
        startActivity(shareIntent)
    }

    private fun showAboutPrinceyyDialog() {
        AlertDialog.Builder(this)
            .setTitle("👑 GhostTrack Forensics")
            .setMessage("Developed and Designed by Princeyy.\n\nProtected by Princeyy CryptoVault & HWID licensing.\nAll forensics are computed completely locally on-device without any internet or data leakage.")
            .setPositiveButton("Awesome", null)
            .show()
    }
}
