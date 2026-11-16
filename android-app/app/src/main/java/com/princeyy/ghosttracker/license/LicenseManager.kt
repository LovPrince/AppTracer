package com.princeyy.ghosttracker.license

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Advanced Cryptographic License & HWID Key System.
 * Supports Time Expiry limits (hours/days) and Usage/Scan limits.
 * Auto-locks the app once any limit expires.
 *
 * Developed by Princeyy
 */
object LicenseManager {

    // Secret master key used to sign and verify licenses (Keep this secret to Princeyy!)
    private const val MASTER_SECRET = "PRINCEYY_GHOSTTRACK_SECRET_KEY_9928174625"
    private const val HMAC_ALGORITHM = "HmacSHA256"

    private const val PREFS_NAME = "princeyy_license_vault"
    private const val KEY_ACTIVATED_TOKEN = "activated_token"
    private const val KEY_USED_COUNT = "used_count"
    private const val KEY_EXPIRY_TIMESTAMP = "expiry_timestamp"
    private const val KEY_MAX_USES = "max_uses"
    private const val KEY_BURNED_SIGNATURES = "burned_signatures"

    /**
     * Generates a stable Hardware ID (HWID) unique to this physical Android device.
     * Format: PRINCEYY-XXXX-XXXX-XXXX
     */
    fun getDeviceHwid(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        val raw = "${Build.MANUFACTURER}_${Build.MODEL}_${androidId}_PRINCEYY"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString("") { "%02X".format(it) }
        return "PRINCEYY-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
    }

    /**
     * Verifies if the app is currently licensed and within time & usage limits.
     */
    fun isAccessAllowed(context: Context): Boolean {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_ACTIVATED_TOKEN, null) ?: return false
        val expiryMs = prefs.getLong(KEY_EXPIRY_TIMESTAMP, 0L)
        val maxUses = prefs.getInt(KEY_MAX_USES, 0)
        val usedCount = prefs.getInt(KEY_USED_COUNT, 0)

        val now = System.currentTimeMillis()

        // 1. Time Limit Check
        if (expiryMs != -1L && now >= expiryMs) {
            burnCurrentToken(context)
            return false
        }

        // 2. Usage / Scan Limit Check
        if (maxUses != -1 && usedCount >= maxUses) {
            burnCurrentToken(context)
            return false
        }

        // 3. Hardware ID & Signature Verification
        val hwid = getDeviceHwid(context)
        return verifyTokenSignature(token, hwid)
    }

    /**
     * Activates a license key provided by Princeyy.
     * Prevents re-entry, replay attacks, and resets of used scan counts.
     */
    fun activateKey(context: Context, rawKey: String): ActivationResult {
        val cleanKey = rawKey.trim()
        if (!cleanKey.startsWith("GHOST-")) {
            return ActivationResult.Error("Invalid key format. Keys start with 'GHOST-'")
        }

        val base64Payload = cleanKey.removePrefix("GHOST-")
        val decoded = try {
            String(Base64.decode(base64Payload, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return ActivationResult.Error("Corrupted or malformed key.")
        }

        // Payload format: HWID|EXPIRY_TIMESTAMP_MS|MAX_USES|SIGNATURE
        val parts = decoded.split("|")
        if (parts.size != 4) {
            return ActivationResult.Error("Invalid license structure.")
        }

        val keyHwid = parts[0]
        val expiryMs = parts[1].toLongOrNull() ?: return ActivationResult.Error("Invalid expiry.")
        val maxUses = parts[2].toIntOrNull() ?: return ActivationResult.Error("Invalid usage limit.")
        val signature = parts[3]

        val deviceHwid = getDeviceHwid(context)
        if (keyHwid != deviceHwid && keyHwid != "ALL_DEVICES") {
            return ActivationResult.Error("This key was generated for a different device ($keyHwid).")
        }

        // Verify cryptographic signature
        val expectedSignature = calculateHmac("$keyHwid|$expiryMs|$maxUses")
        if (signature != expectedSignature) {
            return ActivationResult.Error("Cryptographic signature check failed. Unauthorized key!")
        }

        val prefs = getPrefs(context)
        val burned = prefs.getStringSet(KEY_BURNED_SIGNATURES, emptySet()) ?: emptySet()

        // 1. Check if this key was already burned / exhausted on this device
        if (burned.contains(signature)) {
            return ActivationResult.Error("❌ This key has already been fully used and is expired!")
        }

        // 2. Check if this key has already consumed its scans previously
        val perKeyUsage = prefs.getInt("key_usage_$signature", 0)
        if (maxUses != -1 && perKeyUsage >= maxUses) {
            burnSignature(context, signature)
            return ActivationResult.Error("❌ This key's scan limit ($maxUses scans) was already fully consumed!")
        }

        // 3. Check if key is already active on this device
        val activeToken = prefs.getString(KEY_ACTIVATED_TOKEN, null)
        if (activeToken == cleanKey) {
            val used = prefs.getInt(KEY_USED_COUNT, 0)
            if (maxUses != -1 && used >= maxUses) {
                burnSignature(context, signature)
                return ActivationResult.Error("❌ This key has reached its maximum scan limit ($maxUses scans)!")
            }
            return ActivationResult.Error("⚠️ This key is already active on your phone! ($used / $maxUses scans used)")
        }

        // 4. Check if already expired by time
        if (expiryMs != -1L && System.currentTimeMillis() >= expiryMs) {
            burnSignature(context, signature)
            return ActivationResult.Error("❌ This key has already expired.")
        }

        // Save activation state without resetting already-consumed scans for this key
        prefs.edit()
            .putString(KEY_ACTIVATED_TOKEN, cleanKey)
            .putLong(KEY_EXPIRY_TIMESTAMP, expiryMs)
            .putInt(KEY_MAX_USES, maxUses)
            .putInt(KEY_USED_COUNT, perKeyUsage) // Keeps previous usage if re-entered, NEVER resets to 0!
            .apply()

        return ActivationResult.Success(expiryMs, maxUses)
    }

    /**
     * Increments the scan / usage counter when an action is performed.
     * Permanently burns the key once the max limit is reached.
     */
    fun recordUsage(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_USED_COUNT, 0)
        val newCount = current + 1
        val maxUses = prefs.getInt(KEY_MAX_USES, -1)
        val token = prefs.getString(KEY_ACTIVATED_TOKEN, null)

        val editor = prefs.edit().putInt(KEY_USED_COUNT, newCount)

        // Track per-key usage so re-activating the same key keeps the count
        val signature = extractSignature(token)
        if (signature != null) {
            editor.putInt("key_usage_$signature", newCount)
        }

        // If this scan reaches or exceeds max limit, permanently burn the key!
        if (maxUses != -1 && newCount >= maxUses && signature != null) {
            val burned = (prefs.getStringSet(KEY_BURNED_SIGNATURES, emptySet()) ?: emptySet()).toMutableSet()
            burned.add(signature)
            editor.putStringSet(KEY_BURNED_SIGNATURES, burned)
        }

        editor.apply()
    }

    private fun burnCurrentToken(context: Context) {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_ACTIVATED_TOKEN, null) ?: return
        val signature = extractSignature(token) ?: return
        burnSignature(context, signature)
    }

    private fun burnSignature(context: Context, signature: String) {
        val prefs = getPrefs(context)
        val burned = (prefs.getStringSet(KEY_BURNED_SIGNATURES, emptySet()) ?: emptySet()).toMutableSet()
        burned.add(signature)
        prefs.edit().putStringSet(KEY_BURNED_SIGNATURES, burned).apply()
    }

    private fun extractSignature(token: String?): String? {
        if (token == null || !token.startsWith("GHOST-")) return null
        return try {
            val base64Payload = token.removePrefix("GHOST-")
            val decoded = String(Base64.decode(base64Payload, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
            val parts = decoded.split("|")
            if (parts.size == 4) parts[3] else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets remaining access time in milliseconds, or -1 if lifetime.
     */
    fun getRemainingTimeMs(context: Context): Long {
        val expiryMs = getPrefs(context).getLong(KEY_EXPIRY_TIMESTAMP, 0L)
        if (expiryMs == -1L) return -1L
        val diff = expiryMs - System.currentTimeMillis()
        return if (diff > 0) diff else 0L
    }

    /**
     * Gets remaining uses, or -1 if unlimited.
     */
    fun getRemainingUses(context: Context): Int {
        val max = getPrefs(context).getInt(KEY_MAX_USES, -1)
        if (max == -1) return -1
        val used = getPrefs(context).getInt(KEY_USED_COUNT, 0)
        val left = max - used
        return if (left > 0) left else 0
    }

    fun getTotalUsed(context: Context): Int {
        return getPrefs(context).getInt(KEY_USED_COUNT, 0)
    }

    private fun verifyTokenSignature(token: String, deviceHwid: String): Boolean {
        return try {
            val base64Payload = token.removePrefix("GHOST-")
            val decoded = String(Base64.decode(base64Payload, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
            val parts = decoded.split("|")
            if (parts.size != 4) return false
            val keyHwid = parts[0]
            val expiryMs = parts[1]
            val maxUses = parts[2]
            val signature = parts[3]

            if (keyHwid != deviceHwid && keyHwid != "ALL_DEVICES") return false
            signature == calculateHmac("$keyHwid|$expiryMs|$maxUses")
        } catch (e: Exception) {
            false
        }
    }

    fun calculateHmac(data: String): String {
        val keySpec = SecretKeySpec(MASTER_SECRET.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(keySpec)
        val raw = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    sealed class ActivationResult {
        data class Success(val expiryMs: Long, val maxUses: Int) : ActivationResult()
        data class Error(val message: String) : ActivationResult()
    }
}
