package com.example.voicetodo.data

import android.content.Context
import android.content.Intent

/** Resolves a spoken app name (e.g. "youtube") to a launch intent for that installed app. */
object AppResolver {

    // Fast path for common apps whose label may differ from what people say.
    private val aliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "yt" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "yt music" to "com.google.android.apps.youtube.music",
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "google" to "com.google.android.googlequicksearchbox",
        "photos" to "com.google.android.apps.photos",
        "drive" to "com.google.android.apps.docs",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "spotify" to "com.spotify.music",
        "telegram" to "org.telegram.messenger",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "snapchat" to "com.snapchat.android",
        "netflix" to "com.netflix.mediaclient",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "hotstar" to "in.startv.hotstar",
        "disney hotstar" to "in.startv.hotstar",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "linkedin" to "com.linkedin.android",
        "reddit" to "com.reddit.frontpage",
        "discord" to "com.discord",
        "zoom" to "us.zoom.videomeetings",
        "settings" to "com.android.settings",
        "play store" to "com.android.vending",
        "camera" to "com.android.camera"
    )

    fun launchIntent(context: Context, name: String): Intent? {
        val pm = context.packageManager
        val key = name.lowercase().trim()

        aliases[key]?.let { pkg -> pm.getLaunchIntentForPackage(pkg)?.let { return it } }

        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        var partialPkg: String? = null
        for (ri in apps) {
            val label = ri.loadLabel(pm).toString().lowercase()
            val pkg = ri.activityInfo.packageName
            if (label == key) return pm.getLaunchIntentForPackage(pkg)
            if (partialPkg == null && label.contains(key)) partialPkg = pkg
        }
        return partialPkg?.let { pm.getLaunchIntentForPackage(it) }
    }
}
