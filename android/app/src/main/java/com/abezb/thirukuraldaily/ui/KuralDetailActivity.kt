package com.abezb.thirukuraldaily.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abezb.thirukuraldaily.R
import com.abezb.thirukuraldaily.data.KuralRepository
import com.abezb.thirukuraldaily.preferences.getCommentaryText
import kotlinx.coroutines.launch

/**
 * Full Kural Detail — Phase 3 Revision.
 *
 * Implements Requirement #6: all content visible, all 4 commentaries shown,
 * Paal + Adhigaram breadcrumb, attribution footer.
 *
 * Share launches [ShareBottomSheet] instead of directly calling ACTION_SEND.
 */
class KuralDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val kuralId = intent.getIntExtra("kural_id", -1)
        if (kuralId == -1) { finish(); return }

        val repository = KuralRepository(this)

        lifecycleScope.launch {
            val kural = repository.getKuralById(kuralId) ?: return@launch

            fun clean(text: String) = text.replace("<br />", "\n").replace("<br/>", "\n").trim()

            // Header
            findViewById<TextView>(R.id.detail_kural_num).text = "குறள் ${kural.id}"
            findViewById<TextView>(R.id.detail_breadcrumb).text = kural.breadcrumb()

            // Kural couplet — primary focus
            findViewById<TextView>(R.id.detail_kural).text =
                "${kural.kuralLine1}\n${kural.kuralLine2}"

            // Transliteration
            val translit = clean(kural.transliteration)
            val translitView = findViewById<TextView>(R.id.detail_transliteration)
            if (translit.isNotBlank()) {
                translitView.text = translit
            } else {
                translitView.visibility = View.GONE
            }

            // English
            findViewById<TextView>(R.id.detail_couplet).text = clean(kural.couplet)
            val vilakam = clean(kural.vilakam)
            val vilakamView = findViewById<TextView>(R.id.detail_vilakam)
            if (vilakam.isNotBlank()) {
                vilakamView.text = vilakam
            } else {
                vilakamView.visibility = View.GONE
            }

            // Kalaignar
            findViewById<TextView>(R.id.detail_commentary).text = clean(kural.kalaingarUrai)

            // Parimezhalakar
            val parimezh = clean(kural.parimezhalagarUrai)
            val parimezhView = findViewById<TextView>(R.id.detail_parimezh)
            if (parimezh.isNotBlank()) {
                parimezhView.text = parimezh
            } else {
                parimezhView.visibility = View.GONE
            }

            // M. Varadarajan
            val varad = clean(kural.mVaradharajanar)
            val varadView = findViewById<TextView>(R.id.detail_varadarajan)
            if (varad.isNotBlank()) {
                varadView.text = varad
            } else {
                varadView.visibility = View.GONE
            }

            // Solomon Pappaiya
            val solomon = clean(kural.solomonPappaiya)
            val solomonView = findViewById<TextView>(R.id.detail_solomon)
            if (solomon.isNotBlank()) {
                solomonView.text = solomon
            } else {
                solomonView.visibility = View.GONE
            }

            // Share → bottom sheet (requirement #7)
            findViewById<Button>(R.id.btn_share_detail).setOnClickListener {
                ShareBottomSheet.newInstance(kural)
                    .show(supportFragmentManager, ShareBottomSheet.TAG)
            }
        }

        findViewById<Button>(R.id.btn_close).setOnClickListener { finish() }
    }
}
