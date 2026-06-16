package com.abezb.thirukuraldaily

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews

class KuralWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_RANDOM = "com.abezb.thirukuraldaily.ACTION_RANDOM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RANDOM) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId, isRandom = true)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, isRandom = false)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    isRandom: Boolean
) {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)
    val kural = if (isRandom) KuralData.getRandomKural(context) else KuralData.getTodayKural(context)
    
    if (kural != null) {
        views.setTextViewText(R.id.kural_num, "குறள் ${kural.id}")
        val todayStr = java.time.LocalDate.now().toString()
        views.setTextViewText(R.id.paal, "—  ${kural.paal} ($todayStr)")
        views.setTextViewText(R.id.kural_line1, kural.kuralLine1)
        views.setTextViewText(R.id.kural_line2, kural.kuralLine2)
        views.setTextViewText(R.id.urai, kural.urai)
    }

    // Refresh today's logic (tap background)
    val intentUpdate = Intent(context, KuralWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    }
    val pendingUpdate = PendingIntent.getBroadcast(
        context, appWidgetId, intentUpdate,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingUpdate)

    // Random logic (tap dice)
    val intentRandom = Intent(context, KuralWidgetProvider::class.java).apply {
        action = KuralWidgetProvider.ACTION_RANDOM
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    // Use a different request code (like appWidgetId + 1000) so it doesn't overwrite the other pending intent
    val pendingRandom = PendingIntent.getBroadcast(
        context, appWidgetId + 1000, intentRandom,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.btn_random, pendingRandom)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
