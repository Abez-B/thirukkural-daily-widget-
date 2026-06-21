package com.abezb.thirukuraldaily.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.abezb.thirukuraldaily.R
import com.abezb.thirukuraldaily.data.model.KuralModel
import com.abezb.thirukuraldaily.preferences.getCommentaryText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Share Options Bottom Sheet — Phase 3.
 *
 * Requirement #7: instead of immediately launching the share sheet, present
 * the user with content selection options first.
 *
 * Usage:
 *   ShareBottomSheet.newInstance(kural).show(supportFragmentManager, ShareBottomSheet.TAG)
 */
class ShareBottomSheet : BottomSheetDialogFragment() {

    private var kural: KuralModel? = null

    // Selection state (local to bottom sheet, not persisted)
    private var includeTamilKural  = true
    private var includeEnglish     = false
    private var includeKalaignar   = true
    private var includeVaradarajan = false
    private var includeSolomon     = false
    private var includeAttribut    = true

    companion object {
        const val TAG = "ShareBottomSheet"
        private const val ARG_KURAL_ID = "kural_id"

        fun newInstance(kural: KuralModel): ShareBottomSheet {
            val sheet = ShareBottomSheet()
            sheet.arguments = Bundle().apply {
                putInt(ARG_KURAL_ID, kural.id)
            }
            sheet._kural = kural
            return sheet
        }
    }

    // Workaround: pass kural directly (fragments can't take arbitrary constructor args)
    private var _kural: KuralModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kural = _kural
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_share, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val kural = kural ?: run { dismiss(); return }

        // Wire up checkboxes
        val cbTamil      = view.findViewById<CheckBox>(R.id.cb_tamil_kural)
        val cbEnglish    = view.findViewById<CheckBox>(R.id.cb_english)
        val cbKalaignar  = view.findViewById<CheckBox>(R.id.cb_kalaignar)
        val cbVaradarajan = view.findViewById<CheckBox>(R.id.cb_varadarajan)
        val cbSolomon    = view.findViewById<CheckBox>(R.id.cb_solomon)
        val cbAttrib     = view.findViewById<CheckBox>(R.id.cb_attribution)
        val btnContinue  = view.findViewById<Button>(R.id.btn_share_continue)
        val previewText  = view.findViewById<TextView>(R.id.share_preview)

        // Initial state
        cbTamil.isChecked   = includeTamilKural
        cbEnglish.isChecked = includeEnglish
        cbKalaignar.isChecked = includeKalaignar
        cbVaradarajan.isChecked = includeVaradarajan
        cbSolomon.isChecked = includeSolomon
        cbAttrib.isChecked  = includeAttribut

        fun updatePreview() {
            previewText.text = buildShareText(kural)
        }

        updatePreview()

        val updateState: (CompoundButton: CheckBox) -> Unit = { _ ->
            includeTamilKural  = cbTamil.isChecked
            includeEnglish     = cbEnglish.isChecked
            includeKalaignar   = cbKalaignar.isChecked
            includeVaradarajan = cbVaradarajan.isChecked
            includeSolomon     = cbSolomon.isChecked
            includeAttribut    = cbAttrib.isChecked
            updatePreview()
        }

        cbTamil.setOnCheckedChangeListener      { _, _ -> updateState(cbTamil) }
        cbEnglish.setOnCheckedChangeListener    { _, _ -> updateState(cbEnglish) }
        cbKalaignar.setOnCheckedChangeListener  { _, _ -> updateState(cbKalaignar) }
        cbVaradarajan.setOnCheckedChangeListener { _, _ -> updateState(cbVaradarajan) }
        cbSolomon.setOnCheckedChangeListener    { _, _ -> updateState(cbSolomon) }
        cbAttrib.setOnCheckedChangeListener     { _, _ -> updateState(cbAttrib) }

        btnContinue.setOnClickListener {
            val text = buildShareText(kural)
            if (text.isBlank()) return@setOnClickListener
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Kural"))
            dismiss()
        }
    }

    private fun buildShareText(kural: KuralModel): String {
        val sb = StringBuilder()

        if (includeTamilKural) {
            sb.appendLine("குறள் ${kural.id}")
            sb.appendLine(kural.kuralLine1)
            sb.appendLine(kural.kuralLine2)
        }

        if (includeEnglish) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("English:")
            sb.appendLine(kural.getCommentaryText("couplet"))
        }

        if (includeKalaignar) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("கலைஞர் உரை:")
            sb.appendLine(kural.getCommentaryText("kalaingar"))
        }

        if (includeVaradarajan) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("வரதராஜனார் உரை:")
            sb.appendLine(kural.getCommentaryText("mVaradharajanar"))
        }

        if (includeSolomon) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("சாலமன் பாப்பையா உரை:")
            sb.appendLine(kural.getCommentaryText("solomonPappaiya"))
        }

        if (includeAttribut) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine("— திருக்குறள் (${kural.paal})")
            sb.append("Thirukkural Daily · https://github.com/abez-b/thirukkural-daily-widget-")
        }

        return sb.toString().trim()
    }
}
