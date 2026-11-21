package com.aura.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class WidgetHost(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, 1024)

    fun start() { runCatching { host.startListening() } }
    fun stop() { runCatching { host.stopListening() } }

    fun providers(): List<AppWidgetProviderInfo> {
        val pm = context.packageManager
        return runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { runCatching { it.loadLabel(pm)?.lowercase() }.getOrNull() ?: "" }
    }

    fun allocate(): Int = host.allocateAppWidgetId()
    fun delete(id: Int) { runCatching { host.deleteAppWidgetId(id) } }
}

@Composable
fun WidgetView(widgetHost: WidgetHost, widgetId: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                val info = widgetHost.manager.getAppWidgetInfo(widgetId)
                if (info == null) View(ctx) else widgetHost.host.createView(ctx, widgetId, info)
            },
            update = { v ->
                // لازم نعرّف الودجت بحجمه الحقيقي، وإلا كتير منهم ما بيرسمش نفسه
                (v as? AppWidgetHostView)?.updateAppWidgetSize(Bundle(), w, h, w, h)
            },
        )
    }
}
