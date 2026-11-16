package com.princeyy.ghosttracker.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.princeyy.ghosttracker.R
import com.princeyy.ghosttracker.model.AppCategory
import com.princeyy.ghosttracker.model.AppUsageInfo
import com.princeyy.ghosttracker.util.FormatUtils

/**
 * RecyclerView Adapter displaying detected Uninstalled & Installed apps.
 *
 * Designed and engineered by Princeyy.
 */
class AppAdapter(
    private val context: Context,
    private val onItemClick: (AppUsageInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val allItems = mutableListOf<AppUsageInfo>()
    private val displayedItems = mutableListOf<AppUsageInfo>()

    fun setData(items: List<AppUsageInfo>) {
        allItems.clear()
        allItems.addAll(items)
        displayedItems.clear()
        displayedItems.addAll(items)
        notifyDataSetChanged()
    }

    fun filter(query: String, filterMode: FilterMode) {
        displayedItems.clear()
        val lowerQuery = query.trim().lowercase()

        val filtered = allItems.filter { item ->
            val matchesQuery = if (lowerQuery.isEmpty()) {
                true
            } else {
                item.appName.lowercase().contains(lowerQuery) ||
                        item.packageName.lowercase().contains(lowerQuery)
            }

            val matchesFilter = when (filterMode) {
                FilterMode.UNINSTALLED_ONLY -> !item.isInstalled
                FilterMode.INSTALLED_ONLY -> item.isInstalled
                FilterMode.VAULTS_AND_DATING -> item.category == AppCategory.SECRET_VAULT || item.category == AppCategory.DATING || item.category == AppCategory.SECRET_MESSAGING
                FilterMode.ALL -> true
            }

            matchesQuery && matchesFilter
        }

        displayedItems.addAll(filtered)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(displayedItems[position])
    }

    override fun getItemCount(): Int = displayedItems.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardApp: CardView = itemView.findViewById(R.id.cardApp)
        private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvAppInitial: TextView = itemView.findViewById(R.id.tvAppInitial)
        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        private val tvCategoryBadge: TextView = itemView.findViewById(R.id.tvCategoryBadge)
        private val tvFgTime: TextView = itemView.findViewById(R.id.tvFgTime)
        private val tvSessions: TextView = itemView.findViewById(R.id.tvSessions)
        private val tvLastUsed: TextView = itemView.findViewById(R.id.tvLastUsed)
        private val tvFirstUsed: TextView = itemView.findViewById(R.id.tvFirstUsed)

        fun bind(item: AppUsageInfo) {
            tvAppName.text = item.appName
            tvPackageName.text = item.packageName
            tvFgTime.text = FormatUtils.formatDuration(item.totalForegroundTimeMs)
            tvSessions.text = "${item.sessionCount} launches"
            tvLastUsed.text = FormatUtils.formatRelativeTime(item.lastTimeUsed)
            tvFirstUsed.text = FormatUtils.formatExactDateTime(item.firstTimeStamp)

            // Category badge (HideX, AmorK, Dating, Chat)
            if (item.category != AppCategory.GENERAL) {
                tvCategoryBadge.text = item.category.badge
                tvCategoryBadge.setTextColor(Color.parseColor(item.category.colorHex))
                tvCategoryBadge.visibility = View.VISIBLE
            } else {
                tvCategoryBadge.visibility = View.GONE
            }

            if (item.isInstalled) {
                // Currently Installed App
                tvStatusBadge.text = "🟢 INSTALLED"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_installed)
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.active_green))
                cardApp.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_card))

                if (item.icon != null) {
                    ivAppIcon.setImageDrawable(item.icon)
                    ivAppIcon.visibility = View.VISIBLE
                    tvAppInitial.visibility = View.GONE
                } else {
                    setupInitialAvatar(item.appName)
                }
            } else {
                // UNINSTALLED GHOST APP!
                tvStatusBadge.text = "👻 UNINSTALLED"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_uninstalled)
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.ghost_red_light))
                cardApp.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_card_uninstalled))

                setupInitialAvatar(item.appName, isGhost = true)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun setupInitialAvatar(name: String, isGhost: Boolean = false) {
            ivAppIcon.visibility = View.GONE
            tvAppInitial.visibility = View.VISIBLE
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            tvAppInitial.text = initial
            if (isGhost) {
                tvAppInitial.setTextColor(Color.parseColor("#EF4444"))
            } else {
                tvAppInitial.setTextColor(Color.parseColor("#6366F1"))
            }
        }
    }

    enum class FilterMode {
        UNINSTALLED_ONLY,
        INSTALLED_ONLY,
        VAULTS_AND_DATING,
        ALL
    }
}
