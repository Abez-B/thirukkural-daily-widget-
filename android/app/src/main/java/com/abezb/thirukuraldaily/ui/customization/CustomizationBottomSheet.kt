package com.abezb.thirukuraldaily.ui.customization

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.abezb.thirukuraldaily.R
import com.abezb.thirukuraldaily.preferences.ALL_COMMENTARIES
import com.abezb.thirukuraldaily.rendering.FontManager
import com.abezb.thirukuraldaily.rendering.FontMeta
import com.abezb.thirukuraldaily.rendering.ThemeManager
import com.abezb.thirukuraldaily.rendering.WidgetTheme
import kotlinx.coroutines.launch

/**
 * Customization Hub — Phase 3 Revision.
 *
 * SAVE pattern (#5):
 *  Commentary selection is held in local [pendingCommentaries] until SAVE is tapped.
 *  This gives immediate visual feedback in the checklist without delayed DataStore writes.
 *  The live preview still reflects the last SAVED state (not pending) to avoid
 *  confusing partial renders.
 *
 * All other preferences (font, theme, sliders) remain live — their changes
 * are cheap and the preview shows them instantly.
 */
class CustomizationBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: CustomizationViewModel by activityViewModels()

    private lateinit var previewImage: ImageView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var fontContainer: LinearLayout
    private lateinit var themeContainer: LinearLayout
    private lateinit var fontSizeSlider: Slider
    private lateinit var opacitySlider: Slider
    private lateinit var commentaryContainer: LinearLayout
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button

    // ── SAVE pattern state (#5) ──────────────────────────────────────────────
    // Local copy of commentary selection. Only committed to DataStore on SAVE.
    private val pendingCommentaries: MutableList<String> = mutableListOf()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_customization, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewImage        = view.findViewById(R.id.preview_image)
        loadingIndicator    = view.findViewById(R.id.preview_loading)
        fontContainer       = view.findViewById(R.id.font_container)
        themeContainer      = view.findViewById(R.id.theme_container)
        fontSizeSlider      = view.findViewById(R.id.slider_font_size)
        opacitySlider       = view.findViewById(R.id.slider_opacity)
        commentaryContainer = view.findViewById(R.id.commentary_container)
        btnSave             = view.findViewById(R.id.btn_save_commentaries)
        btnReset            = view.findViewById(R.id.btn_reset_defaults)

        // Initialize pending state from current DataStore values
        pendingCommentaries.clear()
        pendingCommentaries.addAll(viewModel.preferences.value.selectedCommentaries)

        buildFontList()
        buildThemeList()
        buildCommentaryList()
        setupSliders()
        setupButtons()
        observeViewModel()
    }

    // ── UI builders ───────────────────────────────────────────────────────────

    private fun buildFontList() {
        val ctx   = requireContext()
        fontContainer.removeAllViews()
        val prefs = viewModel.preferences.value

        FontManager.catalog.forEach { font ->
            fontContainer.addView(buildFontChip(ctx, font, prefs.fontId, prefs.favoriteFonts))
        }
    }

    private fun buildFontChip(
        ctx: Context, font: FontMeta, selectedId: String, favorites: Set<String>
    ): View {
        val isSelected = font.id == selectedId
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            background = if (isSelected) {
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#222222"))
                    setStroke(2, Color.parseColor("#F5F5F5"))
                    cornerRadius = 12f
                }
            } else {
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#171717"))
                    setStroke(1, Color.parseColor("#2A2A2A"))
                    cornerRadius = 12f
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
        }

        val previewLabel = TextView(ctx).apply {
            text = font.previewText
            textSize = 15f
            setTextColor(if (isSelected) Color.parseColor("#F5F5F5") else Color.parseColor("#707070"))
            try { typeface = FontManager.getTypeface(ctx, font.id) } catch (_: Exception) {}
        }
        val nameLabel = TextView(ctx).apply {
            text = font.displayName
            textSize = 9f
            setTextColor(Color.parseColor("#505050"))
        }

        container.addView(previewLabel)
        container.addView(nameLabel)

        container.setOnClickListener {
            viewModel.selectFont(font.id)
            buildFontList()
        }
        return container
    }

    private fun buildThemeList() {
        val ctx = requireContext()
        themeContainer.removeAllViews()
        val currentId = viewModel.preferences.value.themeId

        ThemeManager.builtInThemes.forEach { theme ->
            themeContainer.addView(buildThemeCard(ctx, theme, currentId))
        }
    }

    private fun buildThemeCard(ctx: Context, theme: WidgetTheme, selectedId: String): View {
        val isSelected = theme.id == selectedId
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(
                (140 * resources.displayMetrics.density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#171717"))
                setStroke(if (isSelected) 2 else 1, if (isSelected) Color.parseColor("#F5F5F5") else Color.parseColor("#2A2A2A"))
                cornerRadius = 12f
            }
        }
        val swatch = View(ctx).apply {
            setBackgroundColor(ThemeManager.themedBackground(theme))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 36)
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = 8
        }
        val sampleText = TextView(ctx).apply {
            text = "குறள்"
            setTextColor(theme.primaryTextColor)
            textSize = 13f
        }
        val nameLabel = TextView(ctx).apply {
            text = theme.displayName
            setTextColor(Color.parseColor("#505050"))
            textSize = 10f
        }
        card.addView(swatch)
        card.addView(sampleText)
        card.addView(nameLabel)
        card.setOnClickListener {
            viewModel.selectTheme(theme.id)
            buildThemeList()
        }
        return card
    }

    /**
     * Builds the commentary checklist using [pendingCommentaries] for state.
     * Does NOT read from DataStore — all changes are local until SAVE.
     * This provides instant, reliable visual feedback (#5).
     */
    private fun buildCommentaryList() {
        val ctx = requireContext()
        commentaryContainer.removeAllViews()

        var lastLanguage: String? = null
        ALL_COMMENTARIES.forEach { commentary ->
            if (commentary.language != lastLanguage) {
                lastLanguage = commentary.language
                val header = TextView(ctx).apply {
                    text = commentary.language.uppercase()
                    textSize = 9f
                    setTextColor(Color.parseColor("#404040"))
                    setPadding(0, if (commentaryContainer.childCount == 0) 0 else 20, 0, 8)
                    letterSpacing = 0.1f
                }
                commentaryContainer.addView(header)
            }

            val isSelected = commentary.id in pendingCommentaries
            val isOnly = pendingCommentaries.size == 1 && isSelected

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10, 0, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val checkBox = CheckBox(ctx).apply {
                this.isChecked = isSelected
                isEnabled = !isOnly
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor(if (isSelected) "#F5F5F5" else "#404040")
                )
            }
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(14, 0, 0, 0)
            }
            val labelView = TextView(ctx).apply {
                text = commentary.label
                textSize = 14f
                setTextColor(if (isSelected) Color.parseColor("#F5F5F5") else Color.parseColor("#505050"))
            }
            val descView = TextView(ctx).apply {
                text = commentary.description
                textSize = 11f
                setTextColor(Color.parseColor("#383838"))
            }
            textCol.addView(labelView)
            textCol.addView(descView)

            // Order badge — shows position number when multiple selected
            val orderBadge = TextView(ctx).apply {
                val idx = pendingCommentaries.indexOf(commentary.id)
                text = if (idx >= 0 && pendingCommentaries.size > 1) "${idx + 1}" else ""
                textSize = 11f
                setTextColor(Color.parseColor("#505050"))
                minWidth = (28 * resources.displayMetrics.density).toInt()
                gravity = Gravity.CENTER
            }

            row.addView(checkBox)
            row.addView(textCol)
            row.addView(orderBadge)

            val toggle = {
                if (isOnly) {
                    Toast.makeText(ctx, "At least one meaning must be selected", Toast.LENGTH_SHORT).show()
                    checkBox.isChecked = true
                } else {
                    if (commentary.id in pendingCommentaries) {
                        pendingCommentaries.remove(commentary.id)
                    } else {
                        pendingCommentaries.add(commentary.id)
                    }
                    buildCommentaryList()
                }
            }
            row.setOnClickListener { toggle() }
            checkBox.setOnClickListener { toggle() }

            commentaryContainer.addView(row)
        }

        // Show unsaved-changes indicator if pending differs from saved
        val savedCommentaries = viewModel.preferences.value.selectedCommentaries
        val hasChanges = pendingCommentaries.toSet() != savedCommentaries.toSet()
        btnSave.alpha = if (hasChanges) 1f else 0.4f
        btnSave.text = if (hasChanges) "Save Meanings" else "Meanings Saved"
    }

    private fun setupSliders() {
        val prefs = viewModel.preferences.value

        fontSizeSlider.value = prefs.fontSize
        fontSizeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setFontSize(value)
        }

        opacitySlider.value = prefs.widgetOpacity
        opacitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setWidgetOpacity(value)
        }
    }

    private fun setupButtons() {
        // SAVE: persist pending commentary selection (#5)
        btnSave.setOnClickListener {
            if (pendingCommentaries.isEmpty()) {
                Toast.makeText(requireContext(), "Select at least one meaning", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveSelectedCommentaries(pendingCommentaries.toList())
            buildCommentaryList() // Refresh to update badge state
            Toast.makeText(requireContext(), "Meanings saved", Toast.LENGTH_SHORT).show()
        }

        btnReset.setOnClickListener {
            viewModel.resetToDefaults()
            pendingCommentaries.clear()
            pendingCommentaries.addAll(listOf("kalaingar"))
            buildFontList()
            buildThemeList()
            buildCommentaryList()
            val prefs = viewModel.preferences.value
            fontSizeSlider.value = prefs.fontSize
            opacitySlider.value  = prefs.widgetOpacity
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.previewBitmap.collect { bitmap ->
                        if (bitmap != null) {
                            previewImage.setImageBitmap(bitmap)
                            previewImage.visibility = View.VISIBLE
                            loadingIndicator.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.notifyWidgetUpdate(requireContext())
    }

    companion object {
        const val TAG = "CustomizationBottomSheet"
        fun newInstance() = CustomizationBottomSheet()
    }
}
