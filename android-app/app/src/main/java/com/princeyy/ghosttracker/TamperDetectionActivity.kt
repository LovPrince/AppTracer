package com.princeyy.ghosttracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.princeyy.ghosttracker.databinding.ActivityTamperDetectionBinding
import com.princeyy.ghosttracker.forensics.TamperDetector
import com.princeyy.ghosttracker.util.FormatUtils
import kotlinx.coroutines.launch

/**
 * Tamper Detection Screen
 * Shows ADB, Shizuku, LADB, Root, Log Gap, and Suspicious App analysis.
 *
 * Developed by Princeyy
 */
class TamperDetectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTamperDetectionBinding
    private var lastReport: TamperDetector.TamperReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTamperDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportTamperReport.setOnClickListener { exportReport() }

        runAnalysis()
    }

    private fun runAnalysis() {
        binding.progressBarTamper.isVisible = true
        binding.tvVerdictTitle.text = "Scanning Device…"
        binding.tvVerdictSubtitle.text = "Analyzing forensic integrity, ADB, Shizuku, LADB, and root status…"

        lifecycleScope.launch {
            val report = TamperDetector.runFullAnalysis(this@TamperDetectionActivity)
            lastReport = report
            binding.progressBarTamper.isVisible = false
            renderReport(report)
        }
    }

    private fun renderReport(report: TamperDetector.TamperReport) {

        // ── Verdict Card ────────────────────────────────────────────────────
        when (report.verdict) {
            TamperDetector.Verdict.CLEAN -> {
                binding.tvVerdictEmoji.text = "✅"
                binding.tvVerdictTitle.text = "Device Looks CLEAN"
                binding.tvVerdictTitle.setTextColor(ContextCompat.getColor(this, R.color.active_green))
                binding.tvVerdictSubtitle.text = "No significant tampering indicators found on this device."
                binding.progressRisk.setIndicatorColor(Color.parseColor("#10B981"))
            }
            TamperDetector.Verdict.MEDIUM_RISK -> {
                binding.tvVerdictEmoji.text = "⚠️"
                binding.tvVerdictTitle.text = "MEDIUM RISK Detected"
                binding.tvVerdictTitle.setTextColor(Color.parseColor("#F59E0B"))
                binding.tvVerdictSubtitle.text = "Some suspicious indicators found. Device may have had ADB or developer tools used."
                binding.progressRisk.setIndicatorColor(Color.parseColor("#F59E0B"))
            }
            TamperDetector.Verdict.HIGH_RISK -> {
                binding.tvVerdictEmoji.text = "🚨"
                binding.tvVerdictTitle.text = "HIGH RISK — Tampered!"
                binding.tvVerdictTitle.setTextColor(ContextCompat.getColor(this, R.color.ghost_red))
                binding.tvVerdictSubtitle.text = "Strong evidence of ADB, Shizuku/LADB usage, or root access detected! Usage history may have been cleared."
                binding.progressRisk.setIndicatorColor(Color.parseColor("#EF4444"))
            }
        }

        binding.progressRisk.progress = report.riskScore
        binding.tvRiskScore.text = "${report.riskScore} / 100"

        // ── System Checks ───────────────────────────────────────────────────
        setCheckCard(binding.tvAdbStatus, report.adbEnabled, "🔴 ACTIVE", "🟢 Disabled")
        setCheckCard(binding.tvDevStatus, report.developerOptionsOn, "🔴 ENABLED", "🟢 OFF")
        setCheckCard(binding.tvRootStatus, report.isRooted, "🔴 ROOTED", "🟢 Not Rooted")
        setCheckCard(binding.tvLogGapStatus, report.logGapResult.suspicious,
            "⚠️ ${report.logGapResult.gapDays}d Gap", "🟢 Normal")

        if (report.logGapResult.suspicious) {
            binding.tvLogGapExplain.text = "⚠️ ${report.logGapResult.description}"
            binding.tvLogGapExplain.isVisible = true
        }

        // ── Factory Reset Card ──────────────────────────────────────────────
        val fr = report.factoryResetResult
        setCheckCard(
            binding.tvFactoryResetStatus,
            fr.detected,
            "🔴 DETECTED (${fr.confidence}%)",
            "🟢 Not Detected"
        )
        if (fr.detected || fr.signals.isNotEmpty()) {
            binding.tvFactoryResetSummary.text = fr.summary
            binding.tvFactoryResetSummary.isVisible = true

            binding.layoutFactoryResetSignals.removeAllViews()
            fr.signals.forEach { signal ->
                val tv = TextView(this).apply {
                    text = signal
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@TamperDetectionActivity, R.color.text_secondary))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 6
                    layoutParams = lp
                }
                binding.layoutFactoryResetSignals.addView(tv)
            }
            binding.layoutFactoryResetSignals.isVisible = true
        }

        // ── Suspicious Apps ─────────────────────────────────────────────────
        val container = binding.layoutSuspiciousApps
        container.removeAllViews()

        if (report.suspiciousApps.isEmpty()) {
            binding.tvNoSuspiciousApps.isVisible = true
        } else {
            binding.tvNoSuspiciousApps.isVisible = false
            for (app in report.suspiciousApps) {
                val card = LayoutInflater.from(this)
                    .inflate(android.R.layout.simple_list_item_2, container, false)

                val tv1 = card.findViewById<TextView>(android.R.id.text1)
                val tv2 = card.findViewById<TextView>(android.R.id.text2)

                tv1.text = "${app.appLabel}  •  ${app.installedSince}"
                tv1.setTextColor(ContextCompat.getColor(this, R.color.ghost_red_light))
                tv1.textSize = 13f

                tv2.text = "${app.packageName}\n${app.reason}"
                tv2.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tv2.textSize = 11f

                val wrapper = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_card_ghost)
                    setPadding(32, 24, 32, 24)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 10
                    layoutParams = lp
                    addView(tv1)
                    addView(tv2)
                }
                container.addView(wrapper)
            }
        }
    }

    private fun setCheckCard(tv: TextView, isBad: Boolean, badText: String, goodText: String) {
        tv.text = if (isBad) badText else goodText
        tv.setTextColor(
            if (isBad) ContextCompat.getColor(this, R.color.ghost_red)
            else ContextCompat.getColor(this, R.color.active_green)
        )
    }

    private fun exportReport() {
        val r = lastReport
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════")
        sb.appendLine("    GHOSTTRACK TAMPER DETECTION REPORT     ")
        sb.appendLine("         👑 Generated by Princeyy          ")
        sb.appendLine("═══════════════════════════════════════════")
        sb.appendLine("Export Time : ${FormatUtils.formatIsoDateTime(System.currentTimeMillis())}")
        sb.appendLine("Device      : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
        sb.appendLine()

        if (r != null) {
            sb.appendLine("── VERDICT ──────────────────────────────")
            sb.appendLine("Risk Score  : ${r.riskScore} / 100")
            sb.appendLine("Verdict     : ${r.verdict.name}")
            sb.appendLine()

            sb.appendLine("── SYSTEM CHECKS ────────────────────────")
            sb.appendLine("ADB Enabled       : ${if (r.adbEnabled) "YES ⚠️" else "No ✅"}")
            sb.appendLine("Developer Options : ${if (r.developerOptionsOn) "ENABLED ⚠️" else "Off ✅"}")
            sb.appendLine("Root Access       : ${if (r.isRooted) "ROOTED 🚨" else "Not Rooted ✅"}")
            sb.appendLine("Log Gap           : ${if (r.logGapResult.suspicious) "SUSPICIOUS (${r.logGapResult.gapDays}d) ⚠️" else "Normal ✅"}")
            if (r.logGapResult.suspicious) sb.appendLine("  → ${r.logGapResult.description}")
            sb.appendLine()

            sb.appendLine("── FACTORY RESET DETECTION ──────────────")
            sb.appendLine("Detected   : ${if (r.factoryResetResult.detected) "YES 🔴" else "No 🟢"}")
            sb.appendLine("Confidence : ${r.factoryResetResult.confidence}%")
            sb.appendLine("Summary    : ${r.factoryResetResult.summary}")
            if (r.factoryResetResult.signals.isNotEmpty()) {
                sb.appendLine("Signals:")
                r.factoryResetResult.signals.forEach { sb.appendLine("  • $it") }
            }
            sb.appendLine()

            sb.appendLine("── SUSPICIOUS APPS (${r.suspiciousApps.size} found) ─────")
            if (r.suspiciousApps.isEmpty()) {
                sb.appendLine("None detected ✅")
            } else {
                r.suspiciousApps.forEach { app ->
                    sb.appendLine("• ${app.appLabel} [${app.packageName}]")
                    sb.appendLine("  Installed: ${app.installedSince}")
                    sb.appendLine("  Reason   : ${app.reason}")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("═══════════════════════════════════════════")
        sb.appendLine("       👑 Made by Princeyy — GhostTrack   ")
        sb.appendLine("═══════════════════════════════════════════")

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Export Tamper Report"))
    }
}
