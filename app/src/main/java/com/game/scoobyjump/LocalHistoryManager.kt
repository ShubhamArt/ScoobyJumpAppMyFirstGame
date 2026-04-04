package com.game.scoobyjump

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class RunHistoryRecord(
    val id: Long = 0,
    val score: Int,
    val coins: Int,
    val durationSeconds: Int,
    val timestamp: Long,
    val skinId: Int,
    val ghostPathJson: String // Serialized [(score:y), ...]
)

class LocalHistoryManager(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "ScoobyJumpHistory.db"
        
        // Table matching
        private const val TABLE_RUNS = "run_history"
        private const val KEY_ID = "id"
        private const val KEY_SCORE = "score"
        private const val KEY_COINS = "coins"
        private const val KEY_DURATION = "duration"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_SKIN_ID = "skin_id"
        private const val KEY_GHOST_PATH = "ghost_path"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_RUNS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_SCORE + " INTEGER,"
                + KEY_COINS + " INTEGER,"
                + KEY_DURATION + " INTEGER,"
                + KEY_TIMESTAMP + " INTEGER,"
                + KEY_SKIN_ID + " INTEGER,"
                + KEY_GHOST_PATH + " TEXT" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RUNS)
        onCreate(db)
    }

    fun insertRun(record: RunHistoryRecord) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(KEY_SCORE, record.score)
        values.put(KEY_COINS, record.coins)
        values.put(KEY_DURATION, record.durationSeconds)
        values.put(KEY_TIMESTAMP, record.timestamp)
        values.put(KEY_SKIN_ID, record.skinId)
        values.put(KEY_GHOST_PATH, record.ghostPathJson)
        db.insert(TABLE_RUNS, null, values)
        db.close()
    }

    fun getTopRuns(limit: Int = 10): List<RunHistoryRecord> {
        val runList = mutableListOf<RunHistoryRecord>()
        val selectQuery = "SELECT * FROM $TABLE_RUNS ORDER BY $KEY_SCORE DESC, $KEY_TIMESTAMP DESC LIMIT $limit"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val record = RunHistoryRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID)),
                    score = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_SCORE)),
                    coins = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_COINS)),
                    durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_DURATION)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP)),
                    skinId = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_SKIN_ID)),
                    ghostPathJson = cursor.getString(cursor.getColumnIndexOrThrow(KEY_GHOST_PATH))
                )
                runList.add(record)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return runList
    }

    fun getHighestScoreRun(): RunHistoryRecord? {
        val runs = getTopRuns(1)
        return runs.firstOrNull()
    }

    fun getHighestAltitude(): Int {
        return getHighestScoreRun()?.score ?: 0
    }

    fun getMaxCoinsPerRun(): Int {
        val selectQuery = "SELECT MAX($KEY_COINS) as max_coins FROM $TABLE_RUNS"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        var max = 0
        if (cursor.moveToFirst()) {
            max = cursor.getInt(cursor.getColumnIndexOrThrow("max_coins"))
        }
        cursor.close()
        db.close()
        return max
    }

    fun getLongestSprintTime(): Int {
        val selectQuery = "SELECT MAX($KEY_DURATION) as max_duration FROM $TABLE_RUNS"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        var max = 0
        if (cursor.moveToFirst()) {
            max = cursor.getInt(cursor.getColumnIndexOrThrow("max_duration"))
        }
        cursor.close()
        db.close()
        return max
    }
}
