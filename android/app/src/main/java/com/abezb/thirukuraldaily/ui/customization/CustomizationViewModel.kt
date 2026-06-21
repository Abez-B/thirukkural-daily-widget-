package com.abezb.thirukuraldaily.ui.customization

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abezb.thirukuraldaily.data.KuralRepository
import com.abezb.thirukuraldaily.data.model.KuralModel
import com.abezb.thirukuraldaily.preferences.UserPreferences
import com.abezb.thirukuraldaily.preferences.UserPreferencesRepository
import com.abezb.thirukuraldaily.rendering.FontManager
import com.abezb.thirukuraldaily.rendering.ThemeManager
import com.abezb.thirukuraldaily.rendering.WidgetBitmapRenderer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for:
 *  - Dashboard (MainActivity) — live preview, kural metadata
 *  - Customization Hub (CustomizationBottomSheet)
 *
 * SAVE pattern (#5):
 *  The commentary selection uses a local pending state inside the BottomSheet.
 *  Only when the user taps SAVE does [setSelectedCommentaries] actually persist.
 *  All other preferences (font, theme, sliders) remain live-updating because
 *  their changes are cheap and immediately visible in the preview.
 *
 * Navigation fix (#8):
 *  [loadKuralForPreview] accepts a specific kuralId so MainActivity can show
 *  the widget-displayed kural instead of always recalculating today's.
 */
class CustomizationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = UserPreferencesRepository(application)
    private val kuralRepo = KuralRepository(application)

    // ── State ─────────────────────────────────────────────────────────────────

    val preferences: StateFlow<UserPreferences> = prefsRepo.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    private val _previewKural = MutableStateFlow<KuralModel?>(null)
    val previewKural: StateFlow<KuralModel?> = _previewKural.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Default: load today's kural for preview
        viewModelScope.launch {
            _previewKural.value = kuralRepo.getTodayKural()
        }

        // Re-render preview whenever preferences or kural changes
        viewModelScope.launch {
            combine(preferences, previewKural) { prefs, kural -> prefs to kural }
                .collect { (prefs, kural) ->
                    kural ?: return@collect
                    _isLoading.value = true
                    WidgetBitmapRenderer.invalidateEntry(getApplication(), kural.id, prefs)
                    _previewBitmap.value = WidgetBitmapRenderer.getOrRender(
                        getApplication(), kural, prefs
                    )
                    _isLoading.value = false
                }
        }
    }

    // ── Navigation fix #8 ─────────────────────────────────────────────────────

    /**
     * Loads a specific kural for the preview (e.g., when MainActivity is opened
     * from a widget tap and the widget was showing a random kural).
     */
    fun loadKuralForPreview(kuralId: Int) = viewModelScope.launch {
        if (_previewKural.value?.id == kuralId) return@launch
        val kural = kuralRepo.getKuralById(kuralId)
        if (kural != null) _previewKural.value = kural
    }

    // ── Live preference mutations (immediate write, no SAVE needed) ───────────

    fun selectFont(fontId: String) = viewModelScope.launch {
        prefsRepo.setFont(fontId)
    }

    fun selectTheme(themeId: String) = viewModelScope.launch {
        prefsRepo.setTheme(themeId)
    }

    fun setFontSize(size: Float) = viewModelScope.launch {
        prefsRepo.setFontSize(size)
    }

    fun setWidgetOpacity(opacity: Float) = viewModelScope.launch {
        prefsRepo.setWidgetOpacity(opacity)
    }

    fun setRefreshMode(mode: String) = viewModelScope.launch {
        prefsRepo.setRefreshMode(mode)
    }

    fun toggleFavoriteFont(fontId: String) = viewModelScope.launch {
        prefsRepo.toggleFavoriteFont(fontId)
    }

    fun resetToDefaults() = viewModelScope.launch {
        prefsRepo.resetToDefaults()
        WidgetBitmapRenderer.invalidateCache(getApplication())
    }

    // ── Commentary — SAVE pattern (#5) ────────────────────────────────────────

    /**
     * Called from [CustomizationBottomSheet] only when the user taps SAVE.
     * Until SAVE is tapped, selection state lives only in the BottomSheet.
     */
    fun saveSelectedCommentaries(ids: List<String>) = viewModelScope.launch {
        prefsRepo.setSelectedCommentaries(ids)
    }

    // ── Derived helpers ───────────────────────────────────────────────────────

    val allFonts get() = FontManager.catalog
    val allThemes get() = ThemeManager.builtInThemes

    fun isFontFavorited(fontId: String): Boolean =
        fontId in preferences.value.favoriteFonts

    fun isCommentarySelected(id: String): Boolean =
        id in preferences.value.selectedCommentaries

    fun currentTheme() = ThemeManager.getTheme(preferences.value.themeId)

    fun notifyWidgetUpdate(context: android.content.Context) {
        val intent = android.content.Intent(
            com.abezb.thirukuraldaily.widget.KuralWidgetProvider.ACTION_PREFS_CHANGED
        ).apply { setPackage(context.packageName) }
        context.sendBroadcast(intent)
    }
}
