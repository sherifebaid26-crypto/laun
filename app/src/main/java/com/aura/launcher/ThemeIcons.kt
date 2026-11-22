package com.aura.launcher

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** أيقونات خاصة بالثيم (12 خانة) بتتطبق على التطبيقات المعروفة (اتصال، رسائل، كاميرا…). */
object ThemeIcons {

    fun slotFor(pkg: String): String? {
        val p = pkg.lowercase()
        return when {
            "dialer" in p || p == "com.android.phone" || p.endsWith(".contacts") -> "phone"
            "mms" in p || "messaging" in p || p.endsWith(".messages") || "message" in p -> "message"
            "chrome" in p || "browser" in p || "firefox" in p || p == "com.microsoft.emmx" || "opera" in p -> "browser"
            "camera" in p -> "camera"
            "gallery" in p || "photos" in p -> "gallery"
            "music" in p || "spotify" in p || "anghami" in p || "soundcloud" in p -> "music"
            "clock" in p -> "clock"
            "calendar" in p -> "calendar"
            p == "com.android.settings" || p.endsWith(".settings") -> "settings"
            p == "com.android.vending" -> "play"
            "weather" in p -> "weather"
            "filemanager" in p || "documentsui" in p || p.endsWith(".files") -> "folder"
            else -> null
        }
    }

    fun load(dir: File): Map<String, ImageBitmap> {
        val files = dir.listFiles() ?: return emptyMap()
        val out = HashMap<String, ImageBitmap>()
        for (f in files) {
            val bmp = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() ?: continue
            out[f.nameWithoutExtension] = bmp.asImageBitmap()
        }
        return out
    }
}
