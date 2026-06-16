package com.abezb.thirukuraldaily

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CycleManager {
    fun getTodayKuralId(context: Context): Int {
        val jsonStr = context.assets.open("kural_cycle.json").bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)
        val epochStr = json.getString("epoch_date")
        val cycleLength = json.getInt("cycle_length")
        val orderArray = json.getJSONArray("order")

        val epochDate = LocalDate.parse(epochStr)
        val today = LocalDate.now()
        val daysSinceEpoch = ChronoUnit.DAYS.between(epochDate, today).toInt()

        val dayIndex = ((daysSinceEpoch % cycleLength) + cycleLength) % cycleLength
        return orderArray.getInt(dayIndex)
    }
}
