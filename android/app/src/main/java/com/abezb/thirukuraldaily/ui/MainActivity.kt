package com.abezb.thirukuraldaily.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.abezb.thirukuraldaily.R
import com.abezb.thirukuraldaily.data.KuralRepository
import com.abezb.thirukuraldaily.ui.customization.CustomizationBottomSheet
import com.abezb.thirukuraldaily.ui.customization.CustomizationViewModel
import com.abezb.thirukuraldaily.widget.KuralWidgetProvider
import kotlinx.coroutines.launch

/**
 * Dashboard Activity — Phase 3 Revision.
 *
 * Handles:
 *  - Displaying the currently shown widget kural (Bug #8 fix: respects the kural
 *    ID passed from the widget via Intent, not today's date).
 *  - Live widget preview via CustomizationViewModel.
 *  - Opening KuralDetailActivity with the correct kural.
 *  - Launching ShareBottomSheet.
 *  - Opening the Customization Hub.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: CustomizationViewModel by viewModels()

    private lateinit var previewImage: ImageView
    private lateinit var previewLoading: ProgressBar
    private lateinit var kuralNumText: TextView
    private lateinit var kuralPaalText: TextView
    private lateinit var kuralLine1Text: TextView
    private lateinit var kuralLine2Text: TextView
    private lateinit var btnRead: Button
    private lateinit var btnShare: Button
    private lateinit var btnCustomize: Button

    // The kural to display — either from widget tap intent or today's kural
    private var displayedKuralId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Fit status bar by applying window insets to the spacer view
        val spacer = findViewById<View>(R.id.status_bar_spacer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spacer.setOnApplyWindowInsetsListener { v, insets ->
                val systemInsets = insets.getInsets(WindowInsets.Type.statusBars())
                v.layoutParams.height = systemInsets.top
                v.requestLayout()
                insets
            }
        }

        previewImage    = findViewById(R.id.main_preview_image)
        previewLoading  = findViewById(R.id.main_preview_loading)
        kuralNumText    = findViewById(R.id.main_kural_num)
        kuralPaalText   = findViewById(R.id.main_paal)
        kuralLine1Text  = findViewById(R.id.main_kural_line1)
        kuralLine2Text  = findViewById(R.id.main_kural_line2)
        btnRead         = findViewById(R.id.btn_view_detail)
        btnShare        = findViewById(R.id.btn_share_main)
        btnCustomize    = findViewById(R.id.btn_customize)

        // BUG #8 FIX: if widget passed a specific kural ID, use it
        displayedKuralId = intent.getIntExtra(KuralWidgetProvider.EXTRA_KURAL_ID, -1)

        // If widget asked us to open share immediately (from share button tap)
        if (intent.getBooleanExtra(KuralWidgetProvider.EXTRA_OPEN_SHARE, false)) {
            openShareSheet()
        }

        btnRead.setOnClickListener {
            val kural = viewModel.previewKural.value ?: return@setOnClickListener
            startActivity(
                android.content.Intent(this, KuralDetailActivity::class.java)
                    .putExtra("kural_id", kural.id)
            )
        }

        btnShare.setOnClickListener { openShareSheet() }

        btnCustomize.setOnClickListener {
            CustomizationBottomSheet.newInstance()
                .show(supportFragmentManager, CustomizationBottomSheet.TAG)
        }

        findViewById<TextView>(R.id.btn_website).setOnClickListener {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://bharath.is-cool.dev/")))
        }
        
        findViewById<TextView>(R.id.btn_contribute).setOnClickListener {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Abez-B/thirukkural-daily-widget-")))
        }
        
        findViewById<TextView>(R.id.btn_issues).setOnClickListener {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Abez-B/thirukkural-daily-widget-/issues")))
        }

        observeViewModel()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newId = intent.getIntExtra(KuralWidgetProvider.EXTRA_KURAL_ID, -1)
        if (newId != -1 && newId != displayedKuralId) {
            displayedKuralId = newId
            viewModel.loadKuralForPreview(newId)
        }
        if (intent.getBooleanExtra(KuralWidgetProvider.EXTRA_OPEN_SHARE, false)) {
            openShareSheet()
        }
    }

    private fun openShareSheet() {
        val kural = viewModel.previewKural.value ?: return
        ShareBottomSheet.newInstance(kural)
            .show(supportFragmentManager, ShareBottomSheet.TAG)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.previewBitmap.collect { bitmap ->
                        if (bitmap != null) {
                            previewImage.setImageBitmap(bitmap)
                            previewImage.visibility = View.VISIBLE
                            previewLoading.visibility = View.GONE
                        } else {
                            previewLoading.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.previewKural.collect { kural ->
                        kural ?: return@collect
                        kuralNumText.text = "குறள் ${kural.id}"
                        kuralPaalText.text = kural.headerString()
                        kuralLine1Text.text = kural.kuralLine1
                        kuralLine2Text.text = kural.kuralLine2
                    }
                }
            }
        }

        // If a specific kural was passed from the widget, load it
        if (displayedKuralId != -1) {
            viewModel.loadKuralForPreview(displayedKuralId)
        }
    }
}
