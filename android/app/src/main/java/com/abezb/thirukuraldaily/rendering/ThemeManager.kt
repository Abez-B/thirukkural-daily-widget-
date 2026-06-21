package com.abezb.thirukuraldaily.rendering

import android.graphics.Color

/**
 * A fully self-contained theme definition for the widget bitmap renderer.
 */
data class WidgetTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val backgroundColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentColor: Int,
    val dividerColor: Int,
    val backgroundAlpha: Int = 255
)

/**
 * Central theme registry — Phase 3 Revision.
 *
 * Design philosophy: monochromatic, reader-first.
 * Themes are named after paper/material textures, not color families.
 * No dominant blue. No colorful gradients. Hierarchy via contrast + spacing.
 */
object ThemeManager {

    // ── Built-in themes ───────────────────────────────────────────────────────

    /** Pure ink-on-paper dark — the default. */
    val INK_DARK = WidgetTheme(
        id                = "ink_dark",
        displayName       = "Ink",
        isDark            = true,
        backgroundColor   = Color.parseColor("#0F0F0F"),
        primaryTextColor  = Color.parseColor("#F5F5F5"),
        secondaryTextColor= Color.parseColor("#A0A0A0"),
        accentColor       = Color.parseColor("#D0D0D0"),
        dividerColor      = Color.parseColor("#303030"),
        backgroundAlpha   = 230
    )

    /** Near-black AMOLED — maximum battery saving. */
    val AMOLED = WidgetTheme(
        id                = "amoled",
        displayName       = "AMOLED",
        isDark            = true,
        backgroundColor   = Color.parseColor("#000000"),
        primaryTextColor  = Color.parseColor("#FFFFFF"),
        secondaryTextColor= Color.parseColor("#808080"),
        accentColor       = Color.parseColor("#C8C8C8"),
        dividerColor      = Color.parseColor("#1A1A1A"),
        backgroundAlpha   = 255
    )

    /** Ghost — translucent over home screen wallpaper. */
    val GHOST = WidgetTheme(
        id                = "ghost",
        displayName       = "Ghost",
        isDark            = true,
        backgroundColor   = Color.parseColor("#0F0F0F"),
        primaryTextColor  = Color.parseColor("#F0F0F0"),
        secondaryTextColor= Color.parseColor("#B0B0B0"),
        accentColor       = Color.parseColor("#E0E0E0"),
        dividerColor      = Color.parseColor("#404040"),
        backgroundAlpha   = 100
    )

    /** Warm paper — light theme for day reading. */
    val PAPER = WidgetTheme(
        id                = "paper",
        displayName       = "Paper",
        isDark            = false,
        backgroundColor   = Color.parseColor("#FAFAFA"),
        primaryTextColor  = Color.parseColor("#111111"),
        secondaryTextColor= Color.parseColor("#666666"),
        accentColor       = Color.parseColor("#1A1A1A"),
        dividerColor      = Color.parseColor("#E5E5E5"),
        backgroundAlpha   = 245
    )

    /** Aged parchment — warm sepia reading mode. */
    val PARCHMENT = WidgetTheme(
        id                = "parchment",
        displayName       = "Parchment",
        isDark            = false,
        backgroundColor   = Color.parseColor("#F5F0E8"),
        primaryTextColor  = Color.parseColor("#2C1A0E"),
        secondaryTextColor= Color.parseColor("#5C4033"),
        accentColor       = Color.parseColor("#3D2414"),
        dividerColor      = Color.parseColor("#D8C9B3"),
        backgroundAlpha   = 245
    )

    /** Stone inscription — dark warm tone, like temple carvings. */
    val STONE = WidgetTheme(
        id                = "stone",
        displayName       = "Stone",
        isDark            = true,
        backgroundColor   = Color.parseColor("#1A1610"),
        primaryTextColor  = Color.parseColor("#E8DCC8"),
        secondaryTextColor= Color.parseColor("#A89070"),
        accentColor       = Color.parseColor("#CDB890"),
        dividerColor      = Color.parseColor("#2E2820"),
        backgroundAlpha   = 235
    )

    /** Palm leaf manuscript — deep olive/brown, classical feel. */
    val PALM_LEAF = WidgetTheme(
        id                = "palm_leaf",
        displayName       = "Palm Leaf",
        isDark            = true,
        backgroundColor   = Color.parseColor("#1C1A0C"),
        primaryTextColor  = Color.parseColor("#F2E8C4"),
        secondaryTextColor= Color.parseColor("#B8A870"),
        accentColor       = Color.parseColor("#D4C080"),
        dividerColor      = Color.parseColor("#2E2C14"),
        backgroundAlpha   = 245
    )

    // ── Catalog ───────────────────────────────────────────────────────────────

    val builtInThemes: List<WidgetTheme> = listOf(
        INK_DARK, AMOLED, GHOST, PAPER, PARCHMENT, STONE, PALM_LEAF
    )

    private val catalogMap: Map<String, WidgetTheme> = builtInThemes.associateBy { it.id }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the theme for [themeId], falling back to [INK_DARK]. */
    fun getTheme(themeId: String): WidgetTheme = catalogMap[themeId] ?: INK_DARK

    /** Creates a user-defined custom theme. */
    fun createCustomTheme(
        backgroundColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        accentColor: Int,
        dividerColor: Int,
        backgroundAlpha: Int = 217
    ) = WidgetTheme(
        id                = "custom",
        displayName       = "Custom",
        isDark            = isColorDark(backgroundColor),
        backgroundColor   = backgroundColor,
        primaryTextColor  = primaryTextColor,
        secondaryTextColor= secondaryTextColor,
        accentColor       = accentColor,
        dividerColor      = dividerColor,
        backgroundAlpha   = backgroundAlpha
    )

    /** Returns the background color with the theme's alpha applied. */
    fun themedBackground(theme: WidgetTheme): Int {
        val base = theme.backgroundColor
        return Color.argb(
            theme.backgroundAlpha,
            Color.red(base), Color.green(base), Color.blue(base)
        )
    }

    private fun isColorDark(color: Int): Boolean {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance < 0.5
    }
}
