package com.abezb.thirukuraldaily.preferences

/**
 * Immutable snapshot of all user-configurable preferences.
 * Flows from DataStore → ViewModel → UI and Widget.
 */
data class UserPreferences(
    val fontId: String = "atm_001",
    val themeId: String = "ink_dark",
    val fontSize: Float = 18f,
    val widgetOpacity: Float = 0.85f,
    val refreshMode: String = "daily",            // "daily" | "random"
    /**
     * Ordered list of commentary IDs to display on the widget.
     * Each ID maps to a [Commentary] entry. Users can enable multiple
     * commentaries and they will be stacked vertically on the rendered card.
     *
     * Valid IDs: "kalaingar", "parimezhalagarUrai", "mVaradharajanar",
     *            "solomonPappaiya", "couplet", "vilakam"
     */
    val selectedCommentaries: List<String> = listOf("kalaingar"),
    val favoriteFonts: Set<String> = emptySet()
)

/**
 * Metadata for a single commentary/meaning option.
 *
 * @param id Stable DataStore key value
 * @param label Short display label shown as a section header on the widget
 * @param language "Tamil" or "English" — used for grouping in the UI
 * @param description Shown in the Customization Hub for clarity
 */
data class Commentary(
    val id: String,
    val label: String,
    val language: String,
    val description: String
)

/**
 * All available commentary options, in display order.
 * Maps directly to KuralModel fields.
 */
val ALL_COMMENTARIES: List<Commentary> = listOf(
    Commentary(
        id = "kalaingar",
        label = "கலைஞர் உரை",
        language = "Tamil",
        description = "Kalaingar M. Karunanidhi"
    ),
    Commentary(
        id = "parimezhalagarUrai",
        label = "பரிமேலழகர் உரை",
        language = "Tamil",
        description = "Parimezhalakar (Classical)"
    ),
    Commentary(
        id = "mVaradharajanar",
        label = "வரதராஜனார் உரை",
        language = "Tamil",
        description = "M. Varadharajanar"
    ),
    Commentary(
        id = "solomonPappaiya",
        label = "சாலமன் பாப்பையா உரை",
        language = "Tamil",
        description = "Solomon Pappaiya"
    ),
    Commentary(
        id = "couplet",
        label = "English Couplet",
        language = "English",
        description = "G.U. Pope's English translation"
    ),
    Commentary(
        id = "vilakam",
        label = "விளக்கம்",
        language = "Tamil",
        description = "Brief meaning (Vilakam)"
    ),
)

/** Resolves a commentary ID to the actual text from a [KuralModel]. */
fun com.abezb.thirukuraldaily.data.model.KuralModel.getCommentaryText(id: String): String =
    when (id) {
        "kalaingar"          -> kalaingarUrai
        "parimezhalagarUrai" -> parimezhalagarUrai
        "mVaradharajanar"    -> mVaradharajanar
        "solomonPappaiya"    -> solomonPappaiya
        "couplet"            -> couplet
        "vilakam"            -> vilakam
        else                 -> kalaingarUrai
    }.replace("<br />", "\n").trim()

/** Returns the [Commentary] metadata for a given ID, or null if unknown. */
fun commentaryById(id: String): Commentary? = ALL_COMMENTARIES.find { it.id == id }
