package com.aura.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ThemeDef(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val tagsAr: List<String>,
    val tagsEn: List<String>,
    val author: String,
    val categoryAr: String = "",
    val categoryEn: String = "",
    val sizeKb: Int,
    val wallpaperUrl: String,
    val previewUrl: String,
    val mode: ThemeMode,
    val accent: Long,
    val homeText: Long?,
    val clock: ClockStyle,
    val icon: IconStyle,
    val rawJson: String,
) {
    private val ar get() = Locale.getDefault().language == "ar"
    val name: String get() = if (ar) nameAr else nameEn
    val tags: List<String> get() = if (ar) tagsAr else tagsEn
    val category: String get() = (if (ar) categoryAr else categoryEn).ifBlank { tr("أخرى", "Other") }
}

private fun colorOf(s: String): Long = android.graphics.Color.parseColor(s).toLong() and 0xFFFFFFFFL

/** الروابط لازم تكون https ونفس مصدر الكتالوج، علشان محتوى الكتالوج ما يوجّهش التطبيق لأي مكان تاني. */
private fun resolveUrl(base: String, rel: String): String {
    val b = URL(base)
    val u = URL(b, rel)
    require(u.protocol == "https" && u.host == b.host) { "bad url" }
    return u.toString()
}

fun themeFromJson(o: JSONObject, base: String): ThemeDef {
    val id = o.getString("id")
    require(Regex("[a-z0-9-]{1,40}").matches(id)) { "bad id" }
    val name = o.getJSONObject("name")
    val tags = o.optJSONObject("tags")
    val st = o.getJSONObject("style")
    val ic = st.optJSONObject("icon")
    val icon = if (ic == null) IconStyle() else IconStyle(
        shape = runCatching { IconShape.valueOf(ic.optString("shape", "ORIGINAL")) }
            .getOrDefault(IconShape.ORIGINAL),
        plate = if (ic.has("plate")) colorOf(ic.getString("plate")) else 0L,
        mono = ic.optBoolean("mono", false),
        tint = if (ic.has("tint")) colorOf(ic.getString("tint")) else 0xFFFFFFFF,
    )
    return ThemeDef(
        id = id,
        nameAr = name.optString("ar", id),
        nameEn = name.optString("en", id),
        tagsAr = tags?.optJSONArray("ar").toStringList(),
        tagsEn = tags?.optJSONArray("en").toStringList(),
        author = o.optString("author", "Aura"),
        categoryAr = o.optJSONObject("category")?.optString("ar", "") ?: "",
        categoryEn = o.optJSONObject("category")?.optString("en", "") ?: "",
        sizeKb = o.optInt("sizeKb", 0),
        wallpaperUrl = resolveUrl(base, o.getString("wallpaper")),
        previewUrl = resolveUrl(base, o.getString("preview")),
        mode = runCatching { ThemeMode.valueOf(st.optString("mode", "DARK")) }.getOrDefault(ThemeMode.DARK),
        accent = colorOf(st.optString("accent", "#00E5FF")),
        homeText = if (st.has("homeText")) colorOf(st.getString("homeText")) else null,
        clock = runCatching { ClockStyle.valueOf(st.optString("clock", "NONE")) }.getOrDefault(ClockStyle.NONE),
        icon = icon,
        rawJson = o.toString(),
    )
}

object ThemeStore {
    /** الكتالوج على GitHub: لإضافة ثيم جديد عدّل store.json وارفع الصور من غير تحديث التطبيق. */
    const val BASE = "https://raw.githubusercontent.com/sherifebaid26-crypto/laun/main/store/"
    private const val MAX_BYTES = 8_000_000

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private suspend fun httpGet(url: String): ByteArray = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "AuraLauncher")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            if (c.contentLengthLong > MAX_BYTES) error("too large")
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun parseCatalog(text: String): List<ThemeDef> {
        val arr = JSONObject(text).getJSONArray("themes")
        return List(arr.length()) { themeFromJson(arr.getJSONObject(it), BASE) }
    }

    /** يرجّع (الثيمات، هل اتقرأت من الكاش لأن مفيش نت). */
    suspend fun fetchCatalog(ctx: Context): Pair<List<ThemeDef>, Boolean> {
        val file = File(ctx.filesDir, "catalog.json")
        return try {
            val text = String(httpGet(BASE + "store.json"), Charsets.UTF_8)
            val list = parseCatalog(text)
            withContext(Dispatchers.IO) { file.writeText(text) }
            list to false
        } catch (e: Exception) {
            val cached = withContext(Dispatchers.IO) { if (file.exists()) file.readText() else null }
            if (cached != null) parseCatalog(cached) to true else throw e
        }
    }

    private fun decodeSampled(bytes: ByteArray, reqWidth: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= reqWidth) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private suspend fun fetchBitmap(url: String, reqWidth: Int): Bitmap? {
        val key = "$url@$reqWidth"
        cache.get(key)?.let { return it }
        return try {
            val bytes = httpGet(url)
            val bmp = withContext(Dispatchers.Default) { decodeSampled(bytes, reqWidth) }
            if (bmp != null) cache.put(key, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    suspend fun previewBitmap(ctx: Context, def: ThemeDef, installed: Boolean, reqWidth: Int): Bitmap? {
        if (installed) {
            val f = wallpaperFile(ctx, def.id)
            if (f.exists()) {
                val local = withContext(Dispatchers.Default) {
                    runCatching { decodeSampled(f.readBytes(), reqWidth) }.getOrNull()
                }
                if (local != null) return local
            }
        }
        return fetchBitmap(def.previewUrl, reqWidth)
    }

    // ───────────── الثيمات المحمّلة ─────────────

    private fun dir(ctx: Context, id: String) = File(ctx.filesDir, "themes/$id")
    fun wallpaperFile(ctx: Context, id: String) = File(dir(ctx, id), "wallpaper.jpg")

    suspend fun download(ctx: Context, def: ThemeDef) {
        val bytes = httpGet(def.wallpaperUrl)
        withContext(Dispatchers.IO) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            require(o.outWidth > 0) { "not an image" }
            val d = dir(ctx, def.id)
            d.mkdirs()
            File(d, "wallpaper.jpg").writeBytes(bytes)
            File(d, "theme.json").writeText(def.rawJson)
        }
    }

    suspend fun loadInstalled(ctx: Context): List<ThemeDef> = withContext(Dispatchers.IO) {
        val root = File(ctx.filesDir, "themes")
        (root.listFiles() ?: emptyArray()).mapNotNull { d ->
            val json = File(d, "theme.json")
            if (!json.exists() || !File(d, "wallpaper.jpg").exists()) null
            else runCatching { themeFromJson(JSONObject(json.readText()), BASE) }.getOrNull()
        }.sortedBy { it.id }
    }

    suspend fun delete(ctx: Context, id: String) {
        withContext(Dispatchers.IO) { dir(ctx, id).deleteRecursively() }
    }

    /** يحط الخلفية على الشاشة الرئيسية وعلى القفل بنداءين منفصلين (أوثق مع الأجهزة المختلفة). */
    suspend fun applyWallpaper(ctx: Context, id: String): WallpaperResult = withContext(Dispatchers.IO) {
        val f = wallpaperFile(ctx, id)
        if (!f.exists()) {
            WallpaperResult(system = false, lock = false)
        } else {
            val wm = WallpaperManager.getInstance(ctx)
            val system = runCatching {
                f.inputStream().use { wm.setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
            }.isSuccess
            val lock = runCatching {
                f.inputStream().use { wm.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
            }.isSuccess
            WallpaperResult(system, lock)
        }
    }
}

data class WallpaperResult(val system: Boolean, val lock: Boolean)
