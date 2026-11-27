package com.aura.launcher

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** أيقونات خاصة بالثيم (12 خانة) بتتطبق على التطبيقات المعروفة (اتصال، رسائل، كاميرا…). */
object ThemeIcons {

    /** بنطابق على أجزاء اسم الحزمة (com.google.android.dialer → dialer) علشان تطبيقات غريبة ما تاخدش أيقونة غلط. */
    fun slotFor(pkg: String): String? {
        val p = pkg.lowercase()
        val seg = p.split('.')
        fun any(f: (String) -> Boolean) = seg.any(f)
        return when {
            any { it == "dialer" } || p == "com.android.phone" || any { it == "contacts" } -> "phone"
            any { it == "mms" || it == "messaging" || it == "messages" || it == "message" } -> "message"
            any { it == "chrome" || it == "firefox" || it == "opera" || it.endsWith("browser") } || p == "com.microsoft.emmx" -> "browser"
            any { it == "camera" || it.endsWith("camera") } -> "camera"
            any { it == "photos" || it.startsWith("gallery") } -> "gallery"
            any { it == "music" || it == "spotify" || it == "anghami" || it == "soundcloud" } -> "music"
            any { it.endsWith("clock") || it == "clockpackage" } -> "clock"
            any { it == "calendar" } -> "calendar"
            p == "com.android.settings" || p.endsWith(".settings") -> "settings"
            p == "com.android.vending" -> "play"
            any { it.contains("weather") } -> "weather"
            any { it == "filemanager" || it == "files" || it == "documentsui" || it.endsWith("myfiles") } -> "folder"
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
