package com.princeyy.ghosttracker.util

import com.princeyy.ghosttracker.model.AppCategory
import java.util.Locale

/**
 * Heuristic catalogue to resolve friendly names and categories for uninstalled apps
 * and deep detection of secret vaults (HideX, AmorK, Calculator Vaults), dating apps,
 * and encrypted messengers.
 *
 * Developed by Princeyy
 */
object KnownAppsCatalog {

    private val KNOWN_PACKAGES = mapOf(
        // === SECRET VAULTS, HIDERS & CALCULATORS ===
        "com.flatfish.cal.privacy" to "HideX - Calculator Vault",
        "calculator.vault.privacy.calc" to "HideX Calculator Lock",
        "com.calculator.hideu" to "HideU - Calculator Lock",
        "com.hide.calculator.vault" to "Calculator Vault App Hider",
        "com.km.calculator.vault" to "Calculator Vault - Gallery Lock",
        "com.colpit.diamond.calculator" to "Secret Calculator Photo Vault",
        "com.smartanuj.hideitpro" to "Hide It Pro (Audio Manager)",
        "com.hidesms.guva.movielocker" to "Hide Pictures in Vaulty",
        "com.amork.apphider" to "AmorK App Hider",
        "com.armor.apphider" to "Armor Hider",
        "com.hide.apphider" to "App Hider - Hide Apps",
        "com.app.hider.master.pro" to "App Hider Master",
        "vault.gallery.lock" to "Gallery Vault - Hide Pictures",
        "com.thinkyeah.galleryvault" to "GalleryVault Hide Video & Photo",
        "com.kii.safe" to "Keepsafe Photo Vault",
        "com.ludashi.dualspace" to "Dual Space - Multiple Accounts",
        "com.lbe.parallel.intl" to "Parallel Space",
        "com.excelliance.multiaccount" to "Multi Parallel - Multiple Accounts",
        "com.cloneapp.parallelspace.dualspace" to "Clone App - Dual App Cloner",
        "com.oasisfeng.island" to "Island - Sandbox Space",
        "com.polestar.super.clone" to "Super Clone - App Cloner",
        "com.kms.free" to "Private Zone - Applock",

        // === DATING APPS ===
        "com.tinder" to "Tinder",
        "com.bumble.app" to "Bumble",
        "com.hinge.app" to "Hinge",
        "com.grindrapp.android" to "Grindr",
        "com.badoo.mobile" to "Badoo",
        "com.okcupid.okcupid" to "OkCupid",
        "com.pure.app" to "Pure: Anonymous Dating",
        "com.ftw_hush.happn" to "Happn - Dating App",
        "com.ruby.ashleymadison" to "Ashley Madison",
        "com.match.android.matchapp" to "Match.com",
        "com.coffeemeetsbagel" to "Coffee Meets Bagel",

        // === SECRET & ENCRYPTED MESSAGING ===
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram X",
        "org.thoughtcrime.securesms" to "Signal",
        "network.loki.messenger" to "Session - Private Messenger",
        "com.myriad.wickrme" to "Wickr Me",
        "com.snapchat.android" to "Snapchat",
        "com.gbwhatsapp" to "GBWhatsApp",
        "com.whatsapp.plus" to "WhatsApp Plus",

        // === GENERAL APPS ===
        "com.whatsapp" to "WhatsApp Messenger",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.facebook.orca" to "Messenger",
        "com.facebook.lite" to "Facebook Lite",
        "com.google.android.youtube" to "YouTube",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.google.android.gm" to "Gmail",
        "com.google.android.apps.photos" to "Google Photos",
        "com.google.android.apps.maps" to "Google Maps",
        "com.google.android.googlequicksearchbox" to "Google",
        "com.android.chrome" to "Google Chrome",
        "com.twitter.android" to "X (formerly Twitter)",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.spotify.music" to "Spotify",
        "com.netflix.mediaclient" to "Netflix",
        "com.amazon.mShop.android.shopping" to "Amazon Shopping",
        "com.amazon.mp3" to "Amazon Music",
        "com.amazon.avod.thirdpartyclient" to "Amazon Prime Video",
        "com.discord" to "Discord",
        "com.reddit.frontpage" to "Reddit",
        "com.pinterest" to "Pinterest",
        "com.linkedin.android" to "LinkedIn",
        "com.supercell.clashofclans" to "Clash of Clans",
        "com.supercell.clashroyale" to "Clash Royale",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.dts.freefireth" to "Free Fire",
        "com.dts.freefiremax" to "Free Fire MAX",
        "com.tencent.ig" to "PUBG Mobile",
        "com.pubg.imobile" to "Battlegrounds Mobile India (BGMI)",
        "com.roblox.client" to "Roblox",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.king.candycrushsaga" to "Candy Crush Saga",
        "com.kiloo.subwaysurf" to "Subway Surfers",
        "com.fingersoft.hillclimb" to "Hill Climb Racing",
        "com.ea.gp.fifamobile" to "EA SPORTS FC Mobile",
        "com.nianticlabs.pokemongo" to "Pokémon GO",
        "com.truecaller" to "Truecaller",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "in.org.npci.upiapp" to "BHIM",
        "com.application.zomato" to "Zomato",
        "in.swiggy.android" to "Swiggy",
        "com.ubercab" to "Uber",
        "com.olacabs.customer" to "Ola",
        "org.videolan.vlc" to "VLC for Android",
        "com.mxtech.videoplayer.ad" to "MX Player",
        "com.duolingo" to "Duolingo",
        "com.shazam.android" to "Shazam",
        "com.canva.editor" to "Canva",
        "com.adobe.reader" to "Adobe Acrobat Reader"
    )

    /**
     * Resolves the app name using catalog lookup or smart package name decomposition.
     */
    fun resolveAppName(packageName: String): String {
        KNOWN_PACKAGES[packageName]?.let { return it }

        // Clean & derive friendly name from package segments:
        val segments = packageName.split(".").filter {
            it !in listOf("com", "org", "net", "io", "android", "app", "apps", "client", "mobile")
        }

        if (segments.isNotEmpty()) {
            val lastSegment = segments.last()
            return formatSegmentToTitle(lastSegment)
        }

        return packageName
    }

    /**
     * Categorizes an application into high-interest forensic categories:
     * Secret Vaults/Hiders (HideX, AmorK), Dating apps, Secret chats, Tampering tools.
     */
    fun resolveCategory(packageName: String): AppCategory {
        val lower = packageName.lowercase()

        // 1. Vaults & Hiders (HideX, AmorK, Calculator Vaults, Dual Space, Private Safe)
        if (lower.contains("hidex") ||
            lower.contains("amork") ||
            lower.contains("apphider") ||
            lower.contains("app.hider") ||
            lower.contains("galleryvault") ||
            lower.contains("keepsafe") ||
            lower.contains("hideitpro") ||
            lower.contains("parallelspace") ||
            lower.contains("dualspace") ||
            (lower.contains("calculator") && (lower.contains("vault") || lower.contains("hide") || lower.contains("lock") || lower.contains("privacy"))) ||
            (lower.contains("vault") && !lower.contains("ansible")) ||
            lower in listOf(
                "com.flatfish.cal.privacy",
                "calculator.vault.privacy.calc",
                "com.calculator.hideu",
                "com.smartanuj.hideitpro",
                "com.hidesms.guva.movielocker",
                "vault.gallery.lock",
                "com.ludashi.dualspace",
                "com.lbe.parallel.intl"
            )
        ) {
            return AppCategory.SECRET_VAULT
        }

        // 2. Dating Apps
        if (lower in listOf(
                "com.tinder",
                "com.bumble.app",
                "com.hinge.app",
                "com.grindrapp.android",
                "com.badoo.mobile",
                "com.okcupid.okcupid",
                "com.pure.app",
                "com.ftw_hush.happn",
                "com.ruby.ashleymadison",
                "com.match.android.matchapp",
                "com.coffeemeetsbagel"
            ) || lower.contains("tinder") || lower.contains("bumble") || lower.contains("dating")
        ) {
            return AppCategory.DATING
        }

        // 3. Secret / Ephemeral Messengers
        if (lower in listOf(
                "org.telegram.messenger",
                "org.telegram.messenger.web",
                "org.thoughtcrime.securesms",
                "network.loki.messenger",
                "com.myriad.wickrme",
                "com.snapchat.android",
                "com.gbwhatsapp",
                "com.whatsapp.plus"
            ) || lower.contains("gbwhatsapp") || lower.contains("wickr")
        ) {
            return AppCategory.SECRET_MESSAGING
        }

        // 4. Tamper / System Cleaners
        if (lower.contains("cleanmaster") ||
            lower.contains("ccleaner") ||
            lower.contains("sdmaid") ||
            lower.contains("appcleaner")
        ) {
            return AppCategory.TAMPER_TOOL
        }

        return AppCategory.GENERAL
    }

    private fun formatSegmentToTitle(segment: String): String {
        return segment
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }
}
