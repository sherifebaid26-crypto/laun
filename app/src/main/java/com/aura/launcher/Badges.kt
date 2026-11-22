package com.aura.launcher

import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * عدد الإشعارات اللي ما شفتهاش لكل تطبيق. بيفضل ظاهر حتى لو مسحت الإشعار من الشريط،
 * ويتصفّر لما تفتح التطبيق من Aura.
 */
object Badges {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("aura_badges", Context.MODE_PRIVATE)
        prefs = p
        counts.value = p.all.mapNotNull { (k, v) -> (v as? Int)?.takeIf { it > 0 }?.let { k to it } }.toMap()
    }

    @Synchronized
    private fun set(pkg: String, n: Int) {
        val v = n.coerceIn(0, 99)
        val m = counts.value.toMutableMap()
        if (v <= 0) m.remove(pkg) else m[pkg] = v
        counts.value = m
        prefs?.edit()?.apply { if (v <= 0) remove(pkg) else putInt(pkg, v) }?.apply()
    }

    fun add(pkg: String, n: Int) = set(pkg, (counts.value[pkg] ?: 0) + n)
    fun clear(pkg: String) { if (counts.value.containsKey(pkg)) set(pkg, 0) }
    fun setIfEmpty(pkg: String, n: Int) { if ((counts.value[pkg] ?: 0) == 0) set(pkg, n) }
}

/** بيقرأ الإشعارات علشان نعمل شارات على الأيقونات. بيحتاج إذن "الوصول للإشعارات". */
class AuraNotificationService : NotificationListenerService() {
    private val seen = HashMap<String, Long>()

    private fun relevant(sbn: StatusBarNotification): Boolean =
        !sbn.isOngoing &&
            (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 &&
            sbn.packageName != packageName

    override fun onListenerConnected() {
        Badges.init(this)
        val active = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        val per = HashMap<String, Int>()
        for (sbn in active) {
            if (!relevant(sbn)) continue
            seen[sbn.key] = sbn.postTime
            per[sbn.packageName] = (per[sbn.packageName] ?: 0) + sbn.notification.number.coerceAtLeast(1)
        }
        per.forEach { (pkg, n) -> Badges.setIfEmpty(pkg, n) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        Badges.init(this)
        if (!relevant(sbn)) return
        val prev = seen[sbn.key]
        seen[sbn.key] = sbn.postTime
        if (prev == null || sbn.postTime > prev) Badges.add(sbn.packageName, sbn.notification.number.coerceAtLeast(1))
    }

    // مقصود: مسح الإشعار مش بيصفّر الرقم، علشان تفتكر إنك ما فتحتش التطبيق.
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { seen.remove(it.key) }
    }
}
