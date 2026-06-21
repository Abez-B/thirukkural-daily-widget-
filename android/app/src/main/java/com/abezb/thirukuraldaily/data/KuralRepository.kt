package com.abezb.thirukuraldaily.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.abezb.thirukuraldaily.data.model.KuralModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The single source of truth for all Kural data.
 * Handles DB provisioning, querying, and daily-cycle logic.
 * All public functions are suspend functions — safe to call from any coroutine.
 */
class KuralRepository(private val context: Context) {

    private val dbName = "thirukural.db"

    // ── Database helpers ──────────────────────────────────────────────────────

    private fun provisionDatabase(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(dbName).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the Kural whose ID matches today in the rotation cycle. */
    suspend fun getTodayKural(): KuralModel? = withContext(Dispatchers.IO) {
        getKuralById(getTodayKuralId())
    }

    /** Returns a randomly selected Kural (ID 1–1330). */
    suspend fun getRandomKural(): KuralModel? = withContext(Dispatchers.IO) {
        getKuralById((1..1330).random())
    }

    /** Returns the Kural with the given ID, or null if not found. */
    suspend fun getKuralById(id: Int): KuralModel? = withContext(Dispatchers.IO) {
        fetchKuralByIdSync(id)
    }

    /** Blocking variant — for use in widget update where a coroutine scope is provided. */
    fun getKuralByIdSync(id: Int): KuralModel? = fetchKuralByIdSync(id)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun getTodayKuralId(): Int {
        val jsonStr = context.assets.open("kural_cycle.json").bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)
        val epochDate = LocalDate.parse(json.getString("epoch_date"))
        val cycleLength = json.getInt("cycle_length")
        val orderArray = json.getJSONArray("order")
        val daysSinceEpoch = ChronoUnit.DAYS.between(epochDate, LocalDate.now()).toInt()
        val dayIndex = ((daysSinceEpoch % cycleLength) + cycleLength) % cycleLength
        return orderArray.getInt(dayIndex)
    }

    private fun fetchKuralByIdSync(id: Int): KuralModel? {
        var kuralModel: KuralModel? = null
        val db = provisionDatabase()
        db.rawQuery("SELECT * FROM kurals WHERE ID = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                val rawKural = cursor.getString(cursor.getColumnIndexOrThrow("Kural")) ?: ""
                val cleaned = rawKural.replace("<br />", "\n")
                val lines = cleaned.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                kuralModel = KuralModel(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                    paal = cursor.getString(cursor.getColumnIndexOrThrow("Paal")) ?: "",
                    iyal = cursor.getString(cursor.getColumnIndexOrThrow("Iyal")) ?: "",
                    adhigaram = cursor.getString(cursor.getColumnIndexOrThrow("Adhigaram")) ?: "",
                    adhigaramId = cursor.getInt(cursor.getColumnIndexOrThrow("Adhigaram_ID")),
                    kuralLine1 = if (lines.isNotEmpty()) lines[0] else "",
                    kuralLine2 = if (lines.size > 1) lines[1] else "",
                    transliteration = cursor.getString(cursor.getColumnIndexOrThrow("Transliteration")) ?: "",
                    vilakam = cursor.getString(cursor.getColumnIndexOrThrow("Vilakam")) ?: "",
                    couplet = cursor.getString(cursor.getColumnIndexOrThrow("Couplet")) ?: "",
                    chapter = cursor.getString(cursor.getColumnIndexOrThrow("Chapter")) ?: "",
                    section = cursor.getString(cursor.getColumnIndexOrThrow("Section")) ?: "",
                    athigaram = cursor.getString(cursor.getColumnIndexOrThrow("Athigaram")) ?: "",
                    kalaingarUrai = cursor.getString(cursor.getColumnIndexOrThrow("Kalaingar_Urai")) ?: "",
                    parimezhalagarUrai = cursor.getString(cursor.getColumnIndexOrThrow("Parimezhalagar_Urai")) ?: "",
                    mVaradharajanar = cursor.getString(cursor.getColumnIndexOrThrow("M_Varadharajanar")) ?: "",
                    solomonPappaiya = cursor.getString(cursor.getColumnIndexOrThrow("Solomon_Pappaiya")) ?: ""
                )
            }
        }
        db.close()
        return kuralModel
    }
}
