package com.game.scoobyjump

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class RunRecord(
    val score: Int,
    val coins: Int,
    val durationSeconds: Int,
    val timestamp: Long,
    val skinId: Int
)

class SaveManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScoobyJumpData", Context.MODE_PRIVATE)

    fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    fun saveInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    fun saveBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    
    fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    fun saveLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
    fun saveString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    private val historyManager by lazy { LocalHistoryManager(context) }

    fun getTopRunRecords(): List<RunRecord> {
        return historyManager.getTopRuns(10).map {
            RunRecord(it.score, it.coins, it.durationSeconds, it.timestamp, it.skinId)
        }
    }

    fun saveRunRecord(record: RunRecord) {
        historyManager.insertRun(RunHistoryRecord(
            score = record.score,
            coins = record.coins,
            durationSeconds = record.durationSeconds,
            timestamp = record.timestamp,
            skinId = record.skinId,
            ghostPathJson = "[]"
        ))
    }
}
