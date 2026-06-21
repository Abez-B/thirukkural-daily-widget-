package com.abezb.thirukuraldaily.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// Extension property: one DataStore per application
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Repository that wraps DataStore<Preferences> and exposes:
 *  - A reactive [Flow<UserPreferences>] for the UI layer.
 *  - Suspend update functions for each preference field.
 *
 * Commentary list is stored as a pipe-delimited String ("kalaingar|couplet")
 * to preserve insertion order, since DataStore's StringSet is unordered.
 */
class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_FONT_ID             = stringPreferencesKey("font_id")
        val KEY_THEME_ID            = stringPreferencesKey("theme_id")
        val KEY_FONT_SIZE           = floatPreferencesKey("font_size")
        val KEY_WIDGET_OPACITY      = floatPreferencesKey("widget_opacity")
        val KEY_REFRESH_MODE        = stringPreferencesKey("refresh_mode")
        /** Ordered, pipe-delimited list: e.g. "kalaingar|couplet|vilakam" */
        val KEY_COMMENTARIES        = stringPreferencesKey("selected_commentaries")
        val KEY_FAVORITE_FONTS      = stringSetPreferencesKey("favorite_fonts")

        private val VALID_IDS = ALL_COMMENTARIES.map { it.id }.toSet()
        private const val SEPARATOR = "|"
        private val DEFAULT_COMMENTARIES = listOf("kalaingar")
    }

    // ── Reactive flow ─────────────────────────────────────────────────────────

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs.toUserPreferences() }

    // ── Update functions (suspend) ─────────────────────────────────────────────

    suspend fun setFont(fontId: String) =
        context.dataStore.edit { it[KEY_FONT_ID] = fontId }

    suspend fun setTheme(themeId: String) =
        context.dataStore.edit { it[KEY_THEME_ID] = themeId }

    suspend fun setFontSize(size: Float) =
        context.dataStore.edit { it[KEY_FONT_SIZE] = size.coerceIn(10f, 28f) }

    suspend fun setWidgetOpacity(opacity: Float) =
        context.dataStore.edit { it[KEY_WIDGET_OPACITY] = opacity.coerceIn(0.3f, 1.0f) }

    suspend fun setRefreshMode(mode: String) =
        context.dataStore.edit {
            it[KEY_REFRESH_MODE] = if (mode in listOf("daily", "random")) mode else "daily"
        }

    /**
     * Replaces the full ordered commentary list.
     * Silently ignores unknown IDs. Ensures at least one entry remains.
     */
    suspend fun setSelectedCommentaries(ids: List<String>) =
        context.dataStore.edit { prefs ->
            val filtered = ids.filter { it in VALID_IDS }.distinct()
            val safe = filtered.ifEmpty { DEFAULT_COMMENTARIES }
            prefs[KEY_COMMENTARIES] = safe.joinToString(SEPARATOR)
        }

    /**
     * Toggles a single commentary ID in the ordered list.
     * Adding: appends to the end. Removing: preserves order of the rest.
     * Prevents removing the last entry (widget must show at least one).
     */
    suspend fun toggleCommentary(id: String) =
        context.dataStore.edit { prefs ->
            val current = parseCommentaries(prefs[KEY_COMMENTARIES])
            val next = if (id in current) {
                if (current.size > 1) current - id else current // keep at least one
            } else {
                current + id
            }
            prefs[KEY_COMMENTARIES] = next.joinToString(SEPARATOR)
        }

    suspend fun toggleFavoriteFont(fontId: String) =
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_FONTS] ?: emptySet()
            prefs[KEY_FAVORITE_FONTS] = if (fontId in current) current - fontId else current + fontId
        }

    suspend fun resetToDefaults() =
        context.dataStore.edit { prefs ->
            val defaults = UserPreferences()
            prefs[KEY_FONT_ID]        = defaults.fontId
            prefs[KEY_THEME_ID]       = defaults.themeId
            prefs[KEY_FONT_SIZE]      = defaults.fontSize
            prefs[KEY_WIDGET_OPACITY] = defaults.widgetOpacity
            prefs[KEY_REFRESH_MODE]   = defaults.refreshMode
            prefs[KEY_COMMENTARIES]   = defaults.selectedCommentaries.joinToString(SEPARATOR)
            prefs[KEY_FAVORITE_FONTS] = defaults.favoriteFonts
        }

    /** Blocking snapshot for use inside widget update suspend functions. */
    suspend fun snapshot(): UserPreferences = userPreferencesFlow.first()

    // ── Private ───────────────────────────────────────────────────────────────

    private fun parseCommentaries(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return DEFAULT_COMMENTARIES
        val parsed = raw.split(SEPARATOR).filter { it in VALID_IDS }
        return parsed.ifEmpty { DEFAULT_COMMENTARIES }
    }

    private fun Preferences.toUserPreferences() = UserPreferences(
        fontId               = this[KEY_FONT_ID]        ?: "atm_001",
        themeId              = this[KEY_THEME_ID]       ?: "ink_dark",
        fontSize             = this[KEY_FONT_SIZE]      ?: 18f,
        widgetOpacity        = this[KEY_WIDGET_OPACITY] ?: 0.85f,
        refreshMode          = this[KEY_REFRESH_MODE]   ?: "daily",
        selectedCommentaries = parseCommentaries(this[KEY_COMMENTARIES]),
        favoriteFonts        = this[KEY_FAVORITE_FONTS] ?: emptySet()
    )
}
