package com.aura.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** خدمة فاضية الغرض منها بس تنفيذ "قفل الشاشة" بدبل تاب. مابتقرأ أي محتوى. */
class AuraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: AuraAccessibilityService? = null

        fun recents(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false

        fun split(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false

        fun lock(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
