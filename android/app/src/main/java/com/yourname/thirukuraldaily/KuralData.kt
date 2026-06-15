package com.yourname.thirukuraldaily

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Kural(
    val id: Int,
    val paal: String,
    val kuralLine1: String,
    val kuralLine2: String,
    val urai: String
)

object KuralData {
    fun getRandomKural(context: Context): Kural? {
        val randomId = (1..1330).random()
        return fetchKuralFromDb(context, randomId)
    }

    fun getTodayKural(context: Context): Kural? {
        val cycleJsonStr = context.assets.open("kural_cycle.json").bufferedReader().use { it.readText() }
        val cycleJson = JSONObject(cycleJsonStr)
        val epochStr = cycleJson.getString("epoch_date")
        val cycleLength = cycleJson.getInt("cycle_length")
        val orderArray = cycleJson.getJSONArray("order")
        
        val epochDate = LocalDate.parse(epochStr)
        val today = LocalDate.now()
        val daysSinceEpoch = ChronoUnit.DAYS.between(epochDate, today).toInt()
        
        val dayIndex = ((daysSinceEpoch % cycleLength) + cycleLength) % cycleLength
        val kuralId = orderArray.getInt(dayIndex)
        
        return fetchKuralFromDb(context, kuralId)
    }

    private fun fetchKuralFromDb(context: Context, id: Int): Kural? {
        val dbPath = context.getDatabasePath("thirukural.db")
        if (!dbPath.exists()) {
            dbPath.parentFile?.mkdirs()
            context.assets.open("thirukural.db").use { input ->
                FileOutputStream(dbPath).use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT * FROM kurals WHERE ID = ?", arrayOf(id.toString()))
        var kural: Kural? = null
        if (cursor.moveToFirst()) {
            val paal = cursor.getString(cursor.getColumnIndexOrThrow("Paal"))
            val kuralText = cursor.getString(cursor.getColumnIndexOrThrow("Kural")).replace("<br />", "\n")
            val lines = kuralText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val line1 = if (lines.isNotEmpty()) lines[0] else ""
            val line2 = if (lines.size > 1) lines[1] else ""
            val urai = cursor.getString(cursor.getColumnIndexOrThrow("Kalaingar_Urai"))
            
            kural = Kural(id, paal, line1, line2, urai)
        }
        cursor.close()
        db.close()
        return kural
    }
}
