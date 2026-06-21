package com.abezb.thirukuraldaily.data.model

/**
 * The single canonical data model for a Thirukkural entry.
 * Replaces both the old KuralModel.kt and the inner Kural data class in KuralData.kt.
 */
data class KuralModel(
    val id: Int,
    val paal: String,           // The Paal (section): அறத்துப்பால் / பொருட்பால் / காமத்துப்பால்
    val iyal: String,           // The Iyal (sub-section)
    val adhigaram: String,      // The Adhigaram (chapter name in Tamil)
    val adhigaramId: Int,       // Adhigaram number (1–133)
    val kuralLine1: String,     // First line of the couplet
    val kuralLine2: String,     // Second line of the couplet
    val transliteration: String,
    val vilakam: String,        // Brief meaning
    val couplet: String,        // English couplet translation
    val chapter: String,        // Chapter name in English
    val section: String,        // Section name in English
    val athigaram: String,      // Alternate romanization of Adhigaram
    val kalaingarUrai: String,  // Kalaingar M. Karunanidhi's commentary
    val parimezhalagarUrai: String, // Parimezhalakar's classical commentary
    val mVaradharajanar: String,
    val solomonPappaiya: String
) {
    /** Returns the Paal label shown on the widget header. Adhigaram is excluded (use adhigaram field directly in detail screens). */
    fun paalLabel(): String = paal

    /** Returns the formatted header string shown on the widget — Paal only, no Adhigaram. */
    fun headerString(): String = paal

    /** Returns the full breadcrumb for the Detail screen: Paal › Adhigaram. */
    fun breadcrumb(): String = "$paal  ›  $adhigaram"

    /** Returns the share text for the native share sheet. */
    fun shareText(commentary: String = kalaingarUrai): String =
        "குறள் $id\n$kuralLine1\n$kuralLine2\n\n" +
        "பொருள்:\n$commentary\n\n" +
        "— திருக்குறள் ($paal)\n" +
        "Thirukkural Daily · https://github.com/abez-b/thirukkural-daily-widget-"

}
