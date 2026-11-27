package com.aura.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/** دعم حزم الأيقونات بصيغة appfilter.xml (ADW/Nova). */
class IconPack(private val res: Resources, private val pkg: String, private val map: Map<String, String>) {
    fun iconFor(component: ComponentName): Drawable? {
        val name = map["ComponentInfo{${component.packageName}/${component.className}}"] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }
}

object IconPacks {
    private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")

    @Suppress("DEPRECATION")
    fun list(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in ACTIONS) {
            runCatching {
                pm.queryIntentActivities(Intent(action), 0).forEach { ri ->
                    val p = ri.activityInfo.packageName
                    found.getOrPut(p) { IconPackInfo(p, ri.loadLabel(pm).toString()) }
                }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private var cached: Triple<String, Long, IconPack?>? = null

    /** بنحلّل appfilter.xml مرة بس لكل إصدار من الحزمة (مش مع كل تحميل للتطبيقات). */
    fun load(context: Context, pkg: String): IconPack? {
        val updated = runCatching { context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
        cached?.let { if (it.first == pkg && it.second == updated) return it.third }
        val p = loadUncached(context, pkg)
        cached = Triple(pkg, updated, p)
        return p
    }

    private fun loadUncached(context: Context, pkg: String): IconPack? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (xmlId != 0) {
            res.getXml(xmlId)
        } else {
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), null)
            }
        }
        val map = HashMap<String, String>()
        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (comp != null && drawable != null) map[comp] = drawable
            }
            type = parser.next()
        }
        IconPack(res, pkg, map)
    }.getOrNull()
}
