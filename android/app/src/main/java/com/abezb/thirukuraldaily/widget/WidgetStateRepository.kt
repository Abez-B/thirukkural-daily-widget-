package com.abezb.thirukuraldaily.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.abezb.thirukuraldaily.preferences.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the Kural currently *displayed* on the widget.
 *
 * Fixes Bug #8: tapping the widget after pressing Random Kural must open
 * the Kural that is actually visible, not recalculate from today's date.
 *
 * Data stored:
 *  - [KEY_DISPLAYED_KURAL_ID]  — the kural.id of whatever is on-screen
 *  - [KEY_DISPLAYED_SOURCE]    — "daily" | "random" (for display in MainActivity)
 */
class WidgetStateRepository(private val context: Context) {

    companion object {
        val KEY_DISPLAYED_KURAL_ID = intPreferencesKey("widget_displayed_kural_id")
        val KEY_DISPLAYED_SOURCE   = stringPreferencesKey("widget_displayed_source")
        val KEY_HISTORY            = stringPreferencesKey("widget_history")
    }

    /** Persists the kural currently shown in the widget. */
    suspend fun saveDisplayedKural(kuralId: Int, source: String = "daily") {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAYED_KURAL_ID] = kuralId
            prefs[KEY_DISPLAYED_SOURCE]   = source
        }
    }

    /** Appends the current ID to history before switching to a new one. */
    suspend fun pushToHistory(currentId: Int) {
        val currentHistory = getHistory()
        val newHistory = if (currentHistory.isEmpty()) currentId.toString() else "$currentHistory,$currentId"
        context.dataStore.edit { prefs ->
            prefs[KEY_HISTORY] = newHistory
        }
    }

    /** Pops the last ID from history, returns null if history is empty. */
    suspend fun popFromHistory(): Int? {
        val currentHistory = getHistory()
        if (currentHistory.isEmpty()) return null
        
        val parts = currentHistory.split(",")
        val lastId = parts.last().toIntOrNull()
        val newHistory = parts.dropLast(1).joinToString(",")
        
        context.dataStore.edit { prefs ->
            prefs[KEY_HISTORY] = newHistory
        }
        return lastId
    }

    private suspend fun getHistory(): String =
        context.dataStore.data.map { it[KEY_HISTORY] ?: "" }.first()

    /** Returns the ID of the kural currently shown on the widget, or -1 if not set. */
    suspend fun getDisplayedKuralId(): Int =
        context.dataStore.data.map { it[KEY_DISPLAYED_KURAL_ID] ?: -1 }.first()

    /** Returns "daily" or "random". */
    suspend fun getDisplayedSource(): String =
        context.dataStore.data.map { it[KEY_DISPLAYED_SOURCE] ?: "daily" }.first()
}
