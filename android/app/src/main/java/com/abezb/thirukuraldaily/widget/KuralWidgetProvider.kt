package com.abezb.thirukuraldaily.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews
import com.abezb.thirukuraldaily.R
import com.abezb.thirukuraldaily.data.KuralRepository
import com.abezb.thirukuraldaily.data.model.KuralModel
import com.abezb.thirukuraldaily.preferences.UserPreferences
import com.abezb.thirukuraldaily.preferences.UserPreferencesRepository
import com.abezb.thirukuraldaily.preferences.commentaryById
import com.abezb.thirukuraldaily.preferences.getCommentaryText
import com.abezb.thirukuraldaily.rendering.WidgetBitmapRenderer
import com.abezb.thirukuraldaily.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Widget provider — Phase 3 Revision.
 *
 * Key fixes in this revision:
 *  - BUG #8 FIXED: After Random Kural, tapping the widget opens the DISPLAYED
 *    kural, not today's. Achieved by persisting kural ID in [WidgetStateRepository]
 *    and passing it to MainActivity via Intent extra.
 *  - Share button now opens MainActivity with share intent flag instead of a
 *    PendingIntent to ACTION_SEND (avoids background Activity launch restrictions).
 *  - All rendering stays on background threads.
 */
class KuralWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV          = "com.abezb.thirukuraldaily.ACTION_PREV"
        const val ACTION_RANDOM        = "com.abezb.thirukuraldaily.ACTION_RANDOM"
        const val ACTION_PREFS_CHANGED = "com.abezb.thirukuraldaily.ACTION_PREFS_CHANGED"

        // Intent extras used to communicate to MainActivity
        const val EXTRA_KURAL_ID  = "displayed_kural_id"
        const val EXTRA_OPEN_SHARE = "open_share"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV, ACTION_RANDOM -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    scope.launch {
                        updateWidget(
                            context,
                            AppWidgetManager.getInstance(context),
                            appWidgetId,
                            action = intent.action
                        )
                    }
                }
            }
            ACTION_PREFS_CHANGED -> {
                WidgetBitmapRenderer.invalidateCache(context)
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, KuralWidgetProvider::class.java)
                )
                onUpdate(context, manager, ids)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            scope.launch {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetBitmapRenderer.invalidateCache(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.cancel()
    }

    // ── Core update logic ─────────────────────────────────────────────────────

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        action: String? = null
    ) {
        val prefsRepo  = UserPreferencesRepository(context)
        val kuralRepo  = KuralRepository(context)
        val stateRepo  = WidgetStateRepository(context)

        val prefs: UserPreferences
        val kural: KuralModel?

        withContext(Dispatchers.IO) {
            prefs = prefsRepo.userPreferencesFlow.first()
            
            if (action == ACTION_PREV) {
                val previousId = stateRepo.popFromHistory()
                kural = if (previousId != null) {
                    kuralRepo.getKuralById(previousId) ?: kuralRepo.getTodayKural()
                } else {
                    kuralRepo.getTodayKural()
                }
            } else if (action == ACTION_RANDOM || prefs.refreshMode == "random") {
                val currentId = stateRepo.getDisplayedKuralId()
                if (currentId != -1) stateRepo.pushToHistory(currentId)
                kural = kuralRepo.getRandomKural()
            } else {
                kural = kuralRepo.getTodayKural()
            }
        }

        if (kural == null) return

        // FIX #8: Persist the kural currently shown so MainActivity opens the right one
        stateRepo.saveDisplayedKural(kural.id, "manual")

        // Render bitmap on background thread
        val bitmap = WidgetBitmapRenderer.getOrRender(context, kural, prefs)

        val views = RemoteViews(context.packageName, R.layout.widget_layout_v2)
        views.setImageViewBitmap(R.id.widget_image, bitmap)

        val theme = com.abezb.thirukuraldaily.rendering.ThemeManager.getTheme(prefs.themeId)
        views.setTextColor(R.id.btn_share, theme.secondaryTextColor)
        views.setTextColor(R.id.btn_prev, theme.secondaryTextColor)
        views.setTextColor(R.id.btn_random, theme.secondaryTextColor)

        // Tap widget card → open MainActivity showing the DISPLAYED kural (not today's)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_KURAL_ID, kural.id)  // FIX #8: pass displayed ID
        }
        views.setOnClickPendingIntent(
            R.id.widget_image,
            PendingIntent.getActivity(
                context, appWidgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Tap ← → previous kural
        val prevIntent = Intent(context, KuralWidgetProvider::class.java).apply {
            this.action = ACTION_PREV
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickPendingIntent(
            R.id.btn_prev,
            PendingIntent.getBroadcast(
                context, appWidgetId + 1000, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Tap 🎲 → random kural
        val randomIntent = Intent(context, KuralWidgetProvider::class.java).apply {
            this.action = ACTION_RANDOM
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickPendingIntent(
            R.id.btn_random,
            PendingIntent.getBroadcast(
                context, appWidgetId + 1001, randomIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Tap 📤 → open MainActivity with share flag (avoids background Activity restrictions)
        val shareOpenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_KURAL_ID, kural.id)
            putExtra(EXTRA_OPEN_SHARE, true)
        }
        views.setOnClickPendingIntent(
            R.id.btn_share,
            PendingIntent.getActivity(
                context, appWidgetId + 2000, shareOpenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
