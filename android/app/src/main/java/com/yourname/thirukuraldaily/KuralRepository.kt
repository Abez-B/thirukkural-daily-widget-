package com.yourname.thirukuraldaily

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

class KuralRepository(private val context: Context) {
    private val dbName = "thirukural.db"

    private fun getDatabase(): SQLiteDatabase {
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

    fun getKuralById(id: Int): KuralModel? {
        var kuralModel: KuralModel? = null
        val db = getDatabase()
        db.rawQuery("SELECT * FROM kurals WHERE ID = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                kuralModel = KuralModel(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                    paal = cursor.getString(cursor.getColumnIndexOrThrow("Paal")) ?: "",
                    iyal = cursor.getString(cursor.getColumnIndexOrThrow("Iyal")) ?: "",
                    adhigaram = cursor.getString(cursor.getColumnIndexOrThrow("Adhigaram")) ?: "",
                    kural = cursor.getString(cursor.getColumnIndexOrThrow("Kural")) ?: "",
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
