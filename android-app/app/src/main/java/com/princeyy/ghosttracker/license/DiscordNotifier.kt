package com.princeyy.ghosttracker.license

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles Discord Request notifications via Webhook or direct Discord invite.
 *
 * Developed by Princeyy
 */
object DiscordNotifier {

    // You can paste your Discord Webhook URL here if you want automated embeds in your Discord channel!
    // Leave blank or as is if you just want to open Discord directly.
    var WEBHOOK_URL: String = "https://discord.com/api/webhooks/1571741418928603136/mgB2UjXZdI4otxgX5g6BCMVeCoPdugN0L0mH4RSeEL2Kuqqx-P__SK-QMQKEEXnamnNf"

    // Your Discord Server Invite link or Profile link
    const val DISCORD_SERVER_LINK = "https://discord.gg/"

    /**
     * Sends an access approval request embed to your Discord Webhook.
     */
    suspend fun sendDiscordWebhookRequest(
        hwid: String,
        discordTag: String,
        requestedPlan: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (WEBHOOK_URL.isBlank()) return@withContext false

        try {
            val url = URL(WEBHOOK_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "GhostTrack-App")
            conn.doOutput = true

            val json = JSONObject()
            json.put("username", "GhostTrack Access Gate")
            json.put("avatar_url", "https://i.imgur.com/8Q8q3Yl.png")

            val embed = JSONObject()
            embed.put("title", "👑 New GhostTrack License Request")
            embed.put("description", "A user is requesting an approval key from Princeyy.")
            embed.put("color", 0x8B5CF6) // Purple

            val fields = JSONArray()
            fields.put(JSONObject().put("name", "👤 Discord User").put("value", discordTag.ifBlank { "Not specified" }).put("inline", true))
            fields.put(JSONObject().put("name", "⏳ Requested Limit").put("value", requestedPlan).put("inline", true))
            fields.put(JSONObject().put("name", "📱 Device Model").put("value", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})").put("inline", false))
            fields.put(JSONObject().put("name", "🔑 Hardware HWID").put("value", "```$hwid```").put("inline", false))

            embed.put("fields", fields)
            embed.put("footer", JSONObject().put("text", "GhostTrack Forensics • Made by Princeyy"))

            val embedsArray = JSONArray()
            embedsArray.put(embed)
            json.put("embeds", embedsArray)

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens Discord app or web browser to contact Princeyy.
     */
    fun openDiscord(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_SERVER_LINK))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback
        }
    }
}
