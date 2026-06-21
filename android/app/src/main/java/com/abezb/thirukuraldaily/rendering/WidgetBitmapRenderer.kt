package com.abezb.thirukuraldaily.rendering

import android.content.Context
import android.graphics.*
import android.util.LruCache
import com.abezb.thirukuraldaily.data.model.KuralModel
import com.abezb.thirukuraldaily.preferences.UserPreferences
import com.abezb.thirukuraldaily.preferences.commentaryById
import com.abezb.thirukuraldaily.preferences.getCommentaryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Enterprise-grade widget rendering engine.
 *
 * Solves Android's RemoteViews custom-font limitation by rasterizing the entire
 * Kural card — including multiple commentaries stacked vertically — onto a [Bitmap].
 *
 * Design goals:
 *  - Zero clipping: full height is measured before canvas allocation.
 *  - Multi-commentary: renders any number of selected commentaries in order.
 *  - Custom Tamil fonts via [FontManager] LruCache.
 *  - XXHDPI (3×) render density for crisp display on all screens.
 *  - Two-tier cache: in-process [LruCache] → file cache keyed by content hash.
 *  - Background-thread only ([Dispatchers.Default] applied internally).
 */
object WidgetBitmapRenderer {

    // ── Constants ─────────────────────────────────────────────────────────────

    private const val WIDGET_WIDTH_DP  = 320
    private const val DENSITY_SCALE   = 3.0f       // XXHDPI
    private const val PADDING_DP      = 24
    private const val CORNER_RADIUS_DP = 24f
    private const val DIVIDER_HEIGHT_DP = 1f
    private const val CACHE_DIR_NAME  = "widget_bitmaps"

    private val memoryCache = LruCache<String, Bitmap>(4)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a [Bitmap] for the given [kural] and [prefs].
     * Memory cache → file cache → fresh render.
     */
    suspend fun getOrRender(
        context: Context,
        kural: KuralModel,
        prefs: UserPreferences
    ): Bitmap = withContext(Dispatchers.Default) {
        val key = buildCacheKey(kural.id, prefs)

        memoryCache.get(key)?.let { return@withContext it }

        val cacheFile = getCacheFile(context, key)
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { bmp ->
                memoryCache.put(key, bmp)
                return@withContext bmp
            }
        }

        val theme    = ThemeManager.getTheme(prefs.themeId)
        val typeface = FontManager.getTypeface(context, prefs.fontId)
        val bitmap   = renderKuralBitmap(kural, prefs, theme, typeface)

        memoryCache.put(key, bitmap)
        saveBitmapToFile(bitmap, cacheFile)
        bitmap
    }

    /** Removes all cached bitmaps (call after any preference change). */
    fun invalidateCache(context: Context) {
        memoryCache.evictAll()
        getCacheDir(context).listFiles()?.forEach { it.delete() }
    }

    /** Removes the cached bitmap for a specific kural + prefs combination. */
    fun invalidateEntry(context: Context, kuralId: Int, prefs: UserPreferences) {
        val key = buildCacheKey(kuralId, prefs)
        memoryCache.remove(key)
        getCacheFile(context, key).takeIf { it.exists() }?.delete()
    }

    // ── Rendering core ────────────────────────────────────────────────────────

    private fun renderKuralBitmap(
        kural: KuralModel,
        prefs: UserPreferences,
        theme: WidgetTheme,
        typeface: Typeface
    ): Bitmap {
        val d         = DENSITY_SCALE
        val padPx     = (PADDING_DP * d).toInt()
        val widthPx   = (WIDGET_WIDTH_DP * d).toInt()
        val cornerPx  = CORNER_RADIUS_DP * d
        val divPx     = DIVIDER_HEIGHT_DP * d

        // ── Paints ─────────────────────────────────────────────────────────────

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = theme.accentColor
            textSize = 12f * d
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        val paalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = theme.accentColor
            textSize = 11f * d
            alpha    = 200
        }
        val kuralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = theme.primaryTextColor
            textSize = prefs.fontSize * d
            setTypeface(typeface)
        }
        val commentaryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = theme.accentColor
            textSize = 10f * d
            setTypeface(Typeface.DEFAULT_BOLD)
            alpha    = 200
        }
        val commentaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = theme.secondaryTextColor
            textSize = 13f * d
            setTypeface(typeface)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = theme.dividerColor
            strokeWidth = divPx
        }

        // ── Resolve commentaries ───────────────────────────────────────────────

        data class CommentaryBlock(
            val label: String,
            val lines: List<String>
        )

        val contentWidth = widthPx - 2 * padPx

        val commentaryBlocks: List<CommentaryBlock> = prefs.selectedCommentaries
            .mapNotNull { id ->
                val text = kural.getCommentaryText(id)
                if (text.isBlank()) null
                else {
                    val label = commentaryById(id)?.label ?: id
                    CommentaryBlock(label, greedyWrap(text.split(" "), commentaryPaint, contentWidth))
                }
            }

        // ── Height measurement (full layout pass) ─────────────────────────────

        // Auto-sizing to prevent cramped text
        var line1Lines = balanceWrapText(kural.kuralLine1, kuralPaint, contentWidth)
        var line2Lines = balanceWrapText(kural.kuralLine2, kuralPaint, contentWidth)
        
        var sizingAttempts = 0
        while ((line1Lines.size > 2 || line2Lines.size > 2) && sizingAttempts < 4) {
            kuralPaint.textSize *= 0.9f
            line1Lines = balanceWrapText(kural.kuralLine1, kuralPaint, contentWidth)
            line2Lines = balanceWrapText(kural.kuralLine2, kuralPaint, contentWidth)
            sizingAttempts++
        }
        
        val kuralLines = line1Lines + line2Lines

        val lineH           = kuralPaint.descent() - kuralPaint.ascent()
        val commentaryLineH = commentaryPaint.descent() - commentaryPaint.ascent()
        val headerH         = headerPaint.descent() - headerPaint.ascent()
        val labelH          = commentaryLabelPaint.descent() - commentaryLabelPaint.ascent()

        var totalHeight = (padPx * 2).toFloat()
        totalHeight += headerH + 4 * d                              // kural num + paal row
        totalHeight += divPx + 8 * d                               // main divider
        totalHeight += kuralLines.size * (lineH + 2 * d)           // kural couplet lines

        for ((i, block) in commentaryBlocks.withIndex()) {
            totalHeight += 12 * d                                   // gap before block
            if (commentaryBlocks.size > 1) {
                // Only show section label when multiple commentaries are selected
                totalHeight += labelH + 4 * d
            }
            totalHeight += block.lines.size * (commentaryLineH + 2 * d)
            if (i < commentaryBlocks.lastIndex) {
                totalHeight += 8 * d + divPx                       // inter-commentary divider
            }
        }

        totalHeight += 16 * d // Extra explicit bottom padding
        val heightPx = totalHeight.toInt().coerceAtLeast(200)

        // ── Canvas draw ────────────────────────────────────────────────────────

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background card
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemeManager.themedBackground(theme)
        }
        canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), cornerPx, cornerPx, bgPaint)

        var y = padPx.toFloat() - headerPaint.ascent()

        // Header: "குறள் 42"  +  "— அறத்துப்பால் • அன்புடைமை"
        canvas.drawText("குறள் ${kural.id}", padPx.toFloat(), y, headerPaint)
        val paalX = padPx + headerPaint.measureText("குறள் ${kural.id}") + 12 * d
        canvas.drawText(kural.headerString(), paalX, y, paalPaint)
        y += headerH + 4 * d

        // Main divider
        canvas.drawLine(padPx.toFloat(), y, (widthPx - padPx).toFloat(), y, dividerPaint)
        y += divPx + 8 * d

        // Kural couplet (custom Tamil font)
        y -= kuralPaint.ascent()
        for (line in kuralLines) {
            canvas.drawText(line, padPx.toFloat(), y, kuralPaint)
            y += lineH + 2 * d
        }
        y += kuralPaint.ascent()

        // Commentaries stacked vertically
        for ((i, block) in commentaryBlocks.withIndex()) {
            y += 12 * d     // gap before this block

            if (commentaryBlocks.size > 1) {
                // Section header label (e.g. "கலைஞர் உரை" / "English Couplet")
                y -= commentaryLabelPaint.ascent()
                canvas.drawText(block.label + ":", padPx.toFloat(), y, commentaryLabelPaint)
                y += labelH + 4 * d
            }

            // Commentary text (custom Tamil font)
            y -= commentaryPaint.ascent()
            for (line in block.lines) {
                canvas.drawText(line, padPx.toFloat(), y, commentaryPaint)
                y += commentaryLineH + 2 * d
            }
            y += commentaryPaint.ascent()

            // Thin separator between commentaries (but not after the last one)
            if (i < commentaryBlocks.lastIndex) {
                y += 8 * d
                canvas.drawLine(
                    (padPx + 20 * d), y,
                    (widthPx - padPx - 20 * d).toFloat(), y,
                    dividerPaint.also { it.alpha = 80 }
                )
                y += divPx
            }
        }

        return bitmap
    }

    // ── Text wrapping ─────────────────────────────────────────────────────────

    private fun balanceWrapText(text: String, paint: Paint, maxWidthPx: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        if (words.size == 1) return words

        // Check if fits in one line
        if (paint.measureText(text) <= maxWidthPx) return listOf(text)

        // Try 2 lines
        val best2Lines = findBalancedLines(words, paint, maxWidthPx, 2)
        if (best2Lines != null) return best2Lines

        // Try 3 lines
        val best3Lines = findBalancedLines(words, paint, maxWidthPx, 3)
        if (best3Lines != null) return best3Lines

        // Fallback to greedy
        return greedyWrap(words, paint, maxWidthPx)
    }

    private fun findBalancedLines(words: List<String>, paint: Paint, maxWidthPx: Int, numLines: Int): List<String>? {
        if (numLines == 2) {
            var bestVariance = Float.MAX_VALUE
            var bestSplit: List<String>? = null
            for (i in 1 until words.size) {
                val line1 = words.take(i).joinToString(" ")
                val line2 = words.drop(i).joinToString(" ")
                val w1 = paint.measureText(line1)
                val w2 = paint.measureText(line2)

                if (w1 <= maxWidthPx && w2 <= maxWidthPx) {
                    val variance = Math.abs(w1 - w2)
                    if (variance < bestVariance) {
                        bestVariance = variance
                        bestSplit = listOf(line1, line2)
                    }
                }
            }
            return bestSplit
        }
        if (numLines == 3 && words.size >= 3) {
            var bestVariance = Float.MAX_VALUE
            var bestSplit: List<String>? = null
            for (i in 1 until words.size - 1) {
                for (j in i + 1 until words.size) {
                    val line1 = words.take(i).joinToString(" ")
                    val line2 = words.subList(i, j).joinToString(" ")
                    val line3 = words.drop(j).joinToString(" ")
                    val w1 = paint.measureText(line1)
                    val w2 = paint.measureText(line2)
                    val w3 = paint.measureText(line3)

                    if (w1 <= maxWidthPx && w2 <= maxWidthPx && w3 <= maxWidthPx) {
                        val mean = (w1 + w2 + w3) / 3
                        val variance = Math.abs(w1 - mean) + Math.abs(w2 - mean) + Math.abs(w3 - mean)
                        if (variance < bestVariance) {
                            bestVariance = variance
                            bestSplit = listOf(line1, line2, line3)
                        }
                    }
                }
            }
            return bestSplit
        }
        return null
    }

    private fun greedyWrap(words: List<String>, paint: Paint, maxWidthPx: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidthPx) {
                current = StringBuilder(test)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(words.joinToString(" ")) }
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private fun buildCacheKey(kuralId: Int, prefs: UserPreferences): String {
        val commentaryKey = prefs.selectedCommentaries.joinToString("-")
        return "${kuralId}_${prefs.fontId}_${prefs.themeId}_${prefs.fontSize.toInt()}" +
               "_${(prefs.widgetOpacity * 100).toInt()}_$commentaryKey"
    }

    private fun getCacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }

    private fun getCacheFile(context: Context, key: String): File =
        File(getCacheDir(context), "$key.png")

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            android.util.Log.w("BitmapRenderer", "Cache write failed: ${e.message}")
        }
    }
}
