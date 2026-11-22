package com.aura.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/** عدد الإشعارات الظاهرة لكل تطبيق (باكدج). */
object Badges {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
}

/** بيقرأ الإشعارات علشان نعمل شارات (نقط/أرقام) على الأيقونات. بيحتاج إذن "الوصول للإشعارات". */
class AuraNotificationService : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onListenerDisconnected() {
        Badges.counts.value = emptyMap()
    }

    private fun refresh() {
        val out = HashMap<String, Int>()
        val list = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        for (sbn in list) {
            if (sbn.isOngoing) continue
            if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            out[sbn.packageName] = (out[sbn.packageName] ?: 0) + sbn.notification.number.coerceAtLeast(1)
        }
        Badges.counts.value = out
    }
}
