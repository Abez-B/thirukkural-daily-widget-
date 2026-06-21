package com.abezb.thirukuraldaily.rendering

import android.content.Context
import android.graphics.Typeface
import android.util.LruCache

/**
 * Metadata describing a single Tamil font entry.
 *
 * @param id Unique stable identifier (matches the filename key in assets/fonts/)
 * @param displayName Human-readable name shown in the font picker
 * @param assetPath Path relative to assets/
 * @param previewText Tamil sample text for the preview chip
 * @param category Aesthetic category for grouping in the UI
 */
data class FontMeta(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val previewText: String = "யாகாவா ராயினும் நாகாக்க",
    val category: FontCategory = FontCategory.CLASSIC
)

enum class FontCategory(val label: String) {
    CLASSIC("Classic"),
    MODERN("Modern"),
    DECORATIVE("Decorative"),
    MANUSCRIPT("Manuscript")
}

/**
 * Singleton font registry.
 *
 * Responsibilities:
 *  - Maintains the curated catalog of 25 Tamil fonts.
 *  - Provides an LruCache for loaded Typeface objects (avoids re-parsing TTF files).
 *  - Validates font availability before use.
 *  - Exposes lookup by ID.
 *
 * Thread safety: [getTypeface] uses a synchronized LruCache and is safe to call
 * from background threads (e.g., [Dispatchers.Default] in the rendering pipeline).
 */
object FontManager {

    // In-memory Typeface cache — max 10 entries (~10 × ~1MB = ~10MB upper bound)
    private val typefaceCache = LruCache<String, Typeface>(10)

    // ── Font Catalog ──────────────────────────────────────────────────────────

    val catalog: List<FontMeta> = listOf(
        FontMeta("atm_001", "ATM Classic I",    "fonts/atm_001.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_005", "ATM Classic V",    "fonts/atm_005.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_010", "ATM Classic X",    "fonts/atm_010.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_015", "ATM Classic XV",   "fonts/atm_015.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_020", "ATM Classic XX",   "fonts/atm_020.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_025", "ATM Script XXV",   "fonts/atm_025.ttf", category = FontCategory.MODERN),
        FontMeta("atm_030", "ATM Script XXX",   "fonts/atm_030.ttf", category = FontCategory.MODERN),
        FontMeta("atm_035", "ATM Modern XXXV",  "fonts/atm_035.ttf", category = FontCategory.MODERN),
        FontMeta("atm_040", "ATM Modern XL",    "fonts/atm_040.ttf", category = FontCategory.MODERN),
        FontMeta("atm_045", "ATM Modern XLV",   "fonts/atm_045.ttf", category = FontCategory.MODERN),
        FontMeta("atm_050", "ATM Serif L",      "fonts/atm_050.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_055", "ATM Serif LV",     "fonts/atm_055.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_060", "ATM Serif LX",     "fonts/atm_060.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_065", "ATM Serif LXV",    "fonts/atm_065.ttf", category = FontCategory.CLASSIC),
        FontMeta("atm_070", "ATM Display LXX",  "fonts/atm_070.ttf", category = FontCategory.DECORATIVE),
        FontMeta("atm_075", "ATM Display LXXV", "fonts/atm_075.ttf", category = FontCategory.DECORATIVE),
        FontMeta("atm_080", "ATM Display LXXX", "fonts/atm_080.ttf", category = FontCategory.DECORATIVE),
        FontMeta("atm_085", "ATM Rounded LXXXV","fonts/atm_085.ttf", category = FontCategory.MODERN),
        FontMeta("atm_090", "ATM Rounded XC",   "fonts/atm_090.ttf", category = FontCategory.MODERN),
        FontMeta("atm_095", "ATM Rounded XCV",  "fonts/atm_095.ttf", category = FontCategory.MODERN),
        FontMeta("atm_100", "ATM Manuscript C", "fonts/atm_100.ttf", "யாகாவா ராயினும்", FontCategory.MANUSCRIPT),
        FontMeta("atm_110", "ATM Manuscript CX","fonts/atm_110.ttf", "நாகாக்க காக்க", FontCategory.MANUSCRIPT),
        FontMeta("atm_120", "ATM Heritage CXX", "fonts/atm_120.ttf", "வையத்துள்",      FontCategory.MANUSCRIPT),
        FontMeta("atm_130", "ATM Heritage CXXX","fonts/atm_130.ttf", "வாழ்வாங்கு",     FontCategory.MANUSCRIPT),
        FontMeta("atm_140", "ATM Heritage CXL", "fonts/atm_140.ttf", "வாழ்பவன்",       FontCategory.MANUSCRIPT)
    )

    /** Map of font ID → FontMeta for O(1) lookup. */
    private val catalogMap: Map<String, FontMeta> = catalog.associateBy { it.id }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the [FontMeta] for the given [fontId], or the default (atm_001) if not found. */
    fun getMeta(fontId: String): FontMeta =
        catalogMap[fontId] ?: catalog.first()

    /**
     * Returns a [Typeface] for the given font ID, loading from assets if not cached.
     * Falls back to [Typeface.DEFAULT] on any error to prevent rendering failures.
     */
    fun getTypeface(context: Context, fontId: String): Typeface {
        typefaceCache.get(fontId)?.let { return it }
        val meta = getMeta(fontId)
        return try {
            val tf = Typeface.createFromAsset(context.assets, meta.assetPath)
            typefaceCache.put(fontId, tf)
            tf
        } catch (e: Exception) {
            android.util.Log.w("FontManager", "Failed to load font '$fontId', using default. ${e.message}")
            Typeface.DEFAULT
        }
    }

    /**
     * Validates that the font file actually exists in the app's assets.
     * Use this in tests or diagnostics.
     */
    fun isAvailable(context: Context, fontId: String): Boolean {
        val meta = catalogMap[fontId] ?: return false
        return try {
            context.assets.open(meta.assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Clears the in-memory typeface cache (e.g., on low-memory callback). */
    fun clearCache() = typefaceCache.evictAll()

    /** Returns the IDs of fonts marked as favorites. */
    fun filterFavorites(favoriteIds: Set<String>): List<FontMeta> =
        catalog.filter { it.id in favoriteIds }

    /** Fonts filtered by category. */
    fun byCategory(category: FontCategory): List<FontMeta> =
        catalog.filter { it.category == category }
}
