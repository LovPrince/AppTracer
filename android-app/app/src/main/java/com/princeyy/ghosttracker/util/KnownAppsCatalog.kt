package com.princeyy.ghosttracker.util

import java.util.Locale

/**
 * Heuristic catalogue to resolve friendly names for uninstalled apps
 * whose package manager metadata is no longer available on the phone.
 *
 * Developed by Princeyy
 */
object KnownAppsCatalog {

    private val KNOWN_PACKAGES = mapOf(
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
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram X",
        "com.snapchat.android" to "Snapchat",
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
        "com.tinder" to "Tinder",
        "com.bumble.app" to "Bumble",
        "com.hinge.app" to "Hinge",
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
        // e.g. "com.supercell.brawlstars" -> "Brawlstars"
        // e.g. "com.rovio.angrybirds2" -> "Angrybirds2"
        val segments = packageName.split(".").filter {
            it !in listOf("com", "org", "net", "io", "android", "app", "apps", "client", "mobile")
        }

        if (segments.isNotEmpty()) {
            val lastSegment = segments.last()
            return formatSegmentToTitle(lastSegment)
        }

        return packageName
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
