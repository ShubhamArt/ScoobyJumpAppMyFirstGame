package com.game.scoobyjump

import android.util.Log

class AnalyticsManager(private val saveManager: SaveManager) {
    
    private val TOTAL_RUNS_KEY = "analytics_total_runs"
    private val TOTAL_SCORE_KEY = "analytics_total_score"
    private val SESSION_LENGTH_KEY = "analytics_session_length"
    
    private var sessionStartTime: Long = 0
    private var runStartTime: Long = 0

    fun logGameStart() {
        Log.d("AnalyticsManager", "Event: game_start")
        runStartTime = System.currentTimeMillis()
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
        }
    }
    
    fun logPlayerDeath(reason: String, finalScore: Int) {
        Log.d("AnalyticsManager", "Event: player_death (Reason: \$reason, Score: \$finalScore)")
        val totalRuns = saveManager.getInt(TOTAL_RUNS_KEY, 0) + 1
        val totalScore = saveManager.getInt(TOTAL_SCORE_KEY, 0) + finalScore
        
        saveManager.saveInt(TOTAL_RUNS_KEY, totalRuns)
        saveManager.saveInt(TOTAL_SCORE_KEY, totalScore)
        
        val averageScore = if (totalRuns > 0) totalScore / totalRuns else 0
        Log.d("AnalyticsManager", "Stats - Total Runs: \$totalRuns, Avg Score: \$averageScore")
        
        val reasonKey = "analytics_death_\$reason"
        val count = saveManager.getInt(reasonKey, 0) + 1
        saveManager.saveInt(reasonKey, count)
        
        val runTime = (System.currentTimeMillis() - runStartTime) / 1000
        Log.d("AnalyticsManager", "Run lasted \$runTime seconds.")
    }
    
    fun logAdViewed(adType: String) {
        Log.d("AnalyticsManager", "Event: ad_viewed (Type: \$adType)")
        val count = saveManager.getInt("analytics_ad_\$adType", 0) + 1
        saveManager.saveInt("analytics_ad_\$adType", count)
    }
    
    fun logSkinUnlocked(skinId: Int) {
        Log.d("AnalyticsManager", "Event: skin_unlocked (Skin ID: \$skinId)")
    }
    
    fun logSessionEnd() {
        if (sessionStartTime > 0) {
            val sessionTime = (System.currentTimeMillis() - sessionStartTime) / 1000
            val totalTime = saveManager.getLong(SESSION_LENGTH_KEY, 0L) + sessionTime
            saveManager.saveLong(SESSION_LENGTH_KEY, totalTime)
            Log.d("AnalyticsManager", "Session ended. Total accumulated playtime: \$totalTime seconds.")
            sessionStartTime = 0L
        }
    }
}
