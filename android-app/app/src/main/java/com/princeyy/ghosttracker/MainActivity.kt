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
import android.view.View
import android.widget.Button
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
import com.princeyy.ghosttracker.model.AppUsageInfo
import com.princeyy.ghosttracker.ui.AppAdapter
import com.princeyy.ghosttracker.util.FormatUtils
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Main Forensic Dashboard Activity.
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
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndScan()
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
        binding.tabLayoutFilter.addTab(binding.tabLayoutFilter.newTab().setText("All (0)"))

        binding.tabLayoutFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilterMode = when (tab?.position) {
                    0 -> AppAdapter.FilterMode.UNINSTALLED_ONLY
                    1 -> AppAdapter.FilterMode.INSTALLED_ONLY
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

        // Tap on "Made by Princeyy" badge shows developer info
        binding.layoutPrinceyyBadge.setOnClickListener {
            showAboutPrinceyyDialog()
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
        if (!analyzer.hasUsagePermission()) {
            binding.cardPermissionWarning.isVisible = true
            return
        }

        binding.progressBar.isVisible = true
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(currentTimeWindowDays.toLong())

        lifecycleScope.launch {
            try {
                val results = analyzer.analyzeAppUsage(startTime, endTime)
                currentDataset = results
                adapter.setData(results)

                // Update summary dashboard counts
                updateDashboard(results)
                applyFilter()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateDashboard(results: List<AppUsageInfo>) {
        val ghosts = results.filter { !it.isInstalled }
        val active = results.filter { it.isInstalled }

        val ghostFgTotal = ghosts.sumOf { it.totalForegroundTimeMs }
        val activeFgTotal = active.sumOf { it.totalForegroundTimeMs }

        binding.tvGhostCount.text = ghosts.size.toString()
        binding.tvGhostTime.text = "${FormatUtils.formatDuration(ghostFgTotal)} screen time"

        binding.tvInstalledCount.text = active.size.toString()
        binding.tvActiveTime.text = "${FormatUtils.formatDuration(activeFgTotal)} screen time"

        // Update tab titles with counts
        binding.tabLayoutFilter.getTabAt(0)?.text = "👻 Uninstalled (${ghosts.size})"
        binding.tabLayoutFilter.getTabAt(1)?.text = "📱 Installed (${active.size})"
        binding.tabLayoutFilter.getTabAt(2)?.text = "All (${results.size})"
    }

    private fun applyFilter() {
        adapter.filter(currentQuery, currentFilterMode)
        val isEmpty = adapter.itemCount == 0
        binding.layoutEmpty.isVisible = isEmpty
        if (isEmpty) {
            binding.tvEmptyMessage.text = when (currentFilterMode) {
                AppAdapter.FilterMode.UNINSTALLED_ONLY -> "No uninstalled apps detected in the selected timeframe."
                AppAdapter.FilterMode.INSTALLED_ONLY -> "No active installed apps found."
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
        val tvStatus = view.findViewById<TextView>(R.id.tvDetailStatus)
        val tvFgTime = view.findViewById<TextView>(R.id.tvDetailFgTime)
        val tvSessions = view.findViewById<TextView>(R.id.tvDetailSessions)
        val tvAvgSession = view.findViewById<TextView>(R.id.tvDetailAvgSession)
        val tvLastUsed = view.findViewById<TextView>(R.id.tvDetailLastUsed)
        val tvFirstUsed = view.findViewById<TextView>(R.id.tvDetailFirstUsed)
        val btnPlayStore = view.findViewById<Button>(R.id.btnOpenPlayStore)
        val btnCopy = view.findViewById<Button>(R.id.btnCopySummary)

        tvAppName.text = item.appName
        tvPackage.text = item.packageName

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
                appendLine("Status: ${if (item.isInstalled) "INSTALLED" else "UNINSTALLED (GHOST)"}")
                appendLine("Total Foreground Time: ${FormatUtils.formatDuration(item.totalForegroundTimeMs)}")
                appendLine("Total Sessions: ${item.sessionCount}")
                appendLine("Avg Session: ${FormatUtils.formatDuration(item.avgSessionDurationMs)}")
                appendLine("Last Used: ${FormatUtils.formatExactDateTime(item.lastTimeUsed)}")
                appendLine("First Seen: ${FormatUtils.formatExactDateTime(item.firstTimeStamp)}")
                appendLine("Generated by GhostTrack • Made by Princeyy")
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("App Forensics", summary))
            Toast.makeText(this, "Copied forensics report to clipboard!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
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
            .setMessage("Developed and Designed by Princeyy.\n\nGhostTrack queries the Android system-level UsageStatsManager service to reveal apps that were used and uninstalled even before this app was installed on the phone.\n\nAll forensics are computed completely locally on-device without any internet or data leakage.")
            .setPositiveButton("Awesome", null)
            .show()
    }
}
