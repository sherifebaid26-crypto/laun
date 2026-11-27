package com.aura.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
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
        registerLegacy(ctx)
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

    /** تطبيقات بعتت لنا العدد الحقيقي (بث الشارات القديم): هي المرجع ونتجاهل حسابنا لها. */
    private val exact = HashSet<String>()

    fun setExact(pkg: String, n: Int) { exact += pkg; set(pkg, n) }

    private var receiversOn = false

    /** بث الشارات اللي التطبيقات بتبعتها للّانشرات (سامسونج/فيفو/سوني/نوفا). */
    @Synchronized
    fun registerLegacy(ctx: Context) {
        if (receiversOn) return
        receiversOn = true
        val app = ctx.applicationContext
        val filter = android.content.IntentFilter().apply {
            addAction("android.intent.action.BADGE_COUNT_UPDATE")
            addAction("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM")
            addAction("com.sonyericsson.home.action.UPDATE_BADGE")
            addAction("com.anddoes.launcher.COUNTER_CHANGED")
        }
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) {
                i ?: return
                val (pkg, n) = when (i.action) {
                    "android.intent.action.BADGE_COUNT_UPDATE" ->
                        i.getStringExtra("badge_count_package_name") to i.getIntExtra("badge_count", 0)
                    "launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM" ->
                        i.getStringExtra("packageName") to i.getIntExtra("notificationNum", 0)
                    "com.sonyericsson.home.action.UPDATE_BADGE" ->
                        i.getStringExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME") to
                            (i.getStringExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE")?.toIntOrNull() ?: 0)
                    else -> i.getStringExtra("package")?.substringBefore('/') to i.getIntExtra("count", 0)
                }
                if (pkg.isNullOrBlank() || pkg == app.packageName) return
                // بث الشارات القديم مفتوح لأي تطبيق: نقبله بس لتطبيق مثبّت وقابل للتشغيل
                if (app.packageManager.getLaunchIntentForPackage(pkg) == null) return
                setExact(pkg, n)
            }
        }
        runCatching { ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_EXPORTED) }
    }

    fun add(pkg: String, n: Int) { if (pkg !in exact) set(pkg, (counts.value[pkg] ?: 0) + n) }
    fun sub(pkg: String, n: Int) { if (pkg !in exact) set(pkg, (counts.value[pkg] ?: 0) - n) }
    fun clear(pkg: String) { if (counts.value.containsKey(pkg)) set(pkg, 0) }
    fun setIfEmpty(pkg: String, n: Int) { if ((counts.value[pkg] ?: 0) == 0) set(pkg, n) }
}

/** بيقرأ الإشعارات علشان نعمل شارات على الأيقونات. بيحتاج إذن "الوصول للإشعارات". */
class AuraNotificationService : NotificationListenerService() {
    private val seen = HashMap<String, Long>()
    private val contrib = HashMap<String, Pair<String, Int>>()

    private fun relevant(sbn: StatusBarNotification): Boolean =
        !sbn.isOngoing &&
            (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 &&
            sbn.packageName != packageName

    @Suppress("DEPRECATION")
    private fun extractMsgs(sbn: StatusBarNotification): List<PreviewMsg> {
        val n = sbn.notification
        if (n.category == android.app.Notification.CATEGORY_TRANSPORT || n.category == android.app.Notification.CATEGORY_PROGRESS) return emptyList()
        val ex = n.extras ?: return emptyList()
        val title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val arr = ex.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
        if (arr != null && arr.isNotEmpty()) {
            return arr.mapNotNull { it as? android.os.Bundle }.mapNotNull { b ->
                val t = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
                val who = b.getCharSequence("sender")?.toString()?.takeIf { it.isNotBlank() } ?: title
                PreviewMsg(who, t, b.getLong("time", sbn.postTime))
            }
        }
        val lines = ex.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) return lines.map { PreviewMsg(title, it.toString(), sbn.postTime) }
        val t = (ex.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString()
        return if (t.isNullOrBlank()) emptyList() else listOf(PreviewMsg(title, t, sbn.postTime))
    }

    override fun onListenerConnected() {
        Badges.init(this)
        val active = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        val per = HashMap<String, Int>()
        for (sbn in active) {
            if (!relevant(sbn)) continue
            val n = sbn.notification.number.coerceAtLeast(1)
            seen[sbn.key] = sbn.postTime
            Previews.put(sbn.packageName, sbn.key, extractMsgs(sbn))
            contrib[sbn.key] = sbn.packageName to n
            per[sbn.packageName] = (per[sbn.packageName] ?: 0) + n
        }
        per.forEach { (pkg, n) -> Badges.setIfEmpty(pkg, n) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        Badges.init(this)
        if (!relevant(sbn)) return
        Previews.put(sbn.packageName, sbn.key, extractMsgs(sbn))
        val n = sbn.notification.number.coerceAtLeast(1)
        val prev = seen[sbn.key]
        seen[sbn.key] = sbn.postTime
        if (prev == null || sbn.postTime > prev) {
            Badges.add(sbn.packageName, n)
            val old = contrib[sbn.key]?.second ?: 0
            contrib[sbn.key] = sbn.packageName to (old + n)
        }
    }

    // السحب من الشريط (REASON_CANCEL) مش بيصفّر الرقم لأنك لسه ما شفتش الرسالة.
    // لكن لو التطبيق نفسه شال الإشعار (قريت جواه) أو دست على الإشعار، بنخصم نصيبه.
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        sbn ?: return
        seen.remove(sbn.key)
        Previews.remove(sbn.packageName, sbn.key)
        val c = contrib.remove(sbn.key) ?: return
        if (reason == REASON_CLICK || reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL) {
            Badges.sub(c.first, c.second)
        }
    }
}
