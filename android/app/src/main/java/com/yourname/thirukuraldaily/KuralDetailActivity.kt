package com.yourname.thirukuraldaily

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class KuralDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val kuralId = intent.getIntExtra("kural_id", -1)
        if (kuralId != -1) {
            val repository = KuralRepository(this)
            val kural = repository.getKuralById(kuralId)
            
            kural?.let {
                findViewById<TextView>(R.id.detail_kural).text = it.kural.replace("<br />", "\n")
                findViewById<TextView>(R.id.detail_transliteration).text = it.transliteration.replace("<br />", "\n")
                findViewById<TextView>(R.id.detail_couplet).text = it.couplet.replace("<br />", "\n")
                findViewById<TextView>(R.id.detail_vilakam).text = it.vilakam.replace("<br />", "\n")
                findViewById<TextView>(R.id.detail_commentary).text = it.kalaingarUrai.replace("<br />", "\n")
            }
        }

        findViewById<Button>(R.id.btn_close).setOnClickListener {
            finish()
        }
    }
}
