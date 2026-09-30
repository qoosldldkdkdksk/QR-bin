package com.example.qrbinary.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val fileName: String,
    val bytes: Int,
    val format: String,
    val sha256: String,
    val time: Long
)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun add(entry: HistoryEntry) {
        val old = JSONArray(prefs.getString("items", "[]"))
        val out = JSONArray()
        out.put(JSONObject().apply {
            put("fileName", entry.fileName)
            put("bytes", entry.bytes)
            put("format", entry.format)
            put("sha256", entry.sha256)
            put("time", entry.time)
        })
        for (i in 0 until minOf(old.length(), 49)) out.put(old.getJSONObject(i))
        prefs.edit().putString("items", out.toString()).apply()
    }

    fun all(): List<HistoryEntry> {
        val a = JSONArray(prefs.getString("items", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryEntry(
                o.getString("fileName"),
                o.getInt("bytes"),
                o.getString("format"),
                o.getString("sha256"),
                o.getLong("time")
            )
        }
    }
}
