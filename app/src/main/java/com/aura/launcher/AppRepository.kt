package com.aura.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    val category: Int,
) {
    val key: String = component.flattenToString()
    val normLabel: String = normalizeForSearch(label)
}

object AppRepository {

    private const val ICON_SIZE_PX = 144

    /** يقرأ كل التطبيقات القابلة للتشغيل ويرتبها أبجدياً (خارج الـ Main thread). */
    @Suppress("DEPRECATION")
    suspend fun loadApps(context: Context, iconPack: String?, style: IconStyle): List<AppEntry> =
        withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val pack = iconPack?.let { IconPacks.load(context, it) }
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            pm.queryIntentActivities(query, 0)
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .map { info ->
                    val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                    val drawable = pack?.iconFor(component) ?: info.loadIcon(pm)
                    AppEntry(
                        label = info.loadLabel(pm).toString(),
                        packageName = info.activityInfo.packageName,
                        component = component,
                        icon = IconRenderer.render(drawable, style, ICON_SIZE_PX).asImageBitmap(),
                        category = info.activityInfo.applicationInfo.category,
                    )
                }
                .sortedBy { it.normLabel }
                .toList()
        }

    /** أيقونات تطبيقاتك الحقيقية بستايل ثيم معيّن (لمعاينة المتجر). */
    @Suppress("DEPRECATION")
    suspend fun previewIcons(context: Context, style: IconStyle, count: Int): List<Pair<String, ImageBitmap>> =
        withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it.loadLabel(pm).toString() to it }
                .sortedBy { it.first.lowercase() }
                .take(count)
                .map { (label, info) -> label to IconRenderer.render(info.loadIcon(pm), style, 128).asImageBitmap() }
        }

    /** أول تشغيل: اتصال، رسائل، متصفح، كاميرا (لو لقيناهم). */
    @Suppress("DEPRECATION")
    fun defaultDock(context: Context, apps: List<AppEntry>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents
            .mapNotNull { runCatching { pm.resolveActivity(it, 0)?.activityInfo?.packageName }.getOrNull() }
            .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg }?.key }
            .distinct()
    }
}
