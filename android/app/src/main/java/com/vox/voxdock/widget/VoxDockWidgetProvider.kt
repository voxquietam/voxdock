package com.vox.voxdock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.vox.voxdock.MainActivity
import com.vox.voxdock.R
import com.vox.voxdock.data.DashboardResponse
import com.vox.voxdock.data.SettingsStore
import com.vox.voxdock.data.VoxDockApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoxDockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context).state.value
                val dashboard = if (settings.workerUrl.isNotBlank()) {
                    VoxDockApi.fetchDashboard(settings).getOrNull()
                } else {
                    null
                }
                if (dashboard != null) {
                    pushState(context, dashboard)
                } else {
                    val views = buildViews(context, DashboardResponse())
                    appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun pushState(context: Context, dashboard: DashboardResponse) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, VoxDockWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context, dashboard)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, d: DashboardResponse): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_voxdock)

            val sel = d.alerts.selected
            val (alertColor, alertLabel) = when (sel.level) {
                "active" -> Color.parseColor("#EF4444") to "▲ ${sel.name.uppercase()}: ALERT!"
                "partial" -> Color.parseColor("#F59E0B") to "▲ ${sel.name.uppercase()}: PARTIAL"
                else -> Color.parseColor("#22C55E") to "● ${sel.name.uppercase()}: CLEAR"
            }
            views.setTextViewText(R.id.widget_alert, alertLabel)
            views.setTextColor(R.id.widget_alert, alertColor)

            val sea = d.weather.current.seaTemp?.let { " · sea ${it}°" }.orEmpty()
            views.setTextViewText(R.id.widget_weather, "${d.weather.current.temp}°C$sea")

            val nextTxt = d.power.nextChange?.let { " → $it" }.orEmpty()
            views.setTextViewText(R.id.widget_power, "⚡ ${d.power.summary}$nextTxt")

            val topNote = d.notes.firstOrNull()
            val noteText = if (topNote != null) {
                val pfx = if (topNote.kind == "reminder") "⏰ ${topNote.remindAt ?: ""} " else "✦ "
                "$pfx${topNote.text}"
            } else {
                "✦ ${d.agentStatus.source.uppercase()}: ${d.agentStatus.message}"
            }
            views.setTextViewText(R.id.widget_note, noteText)

            val launchIntent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            return views
        }
    }
}
