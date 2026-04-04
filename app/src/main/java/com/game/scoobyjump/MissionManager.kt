package com.game.scoobyjump

import kotlin.random.Random

enum class MissionTier(val targetMultiplier: Float, val rewardMultiplier: Float, val prefix: String) {
    BRONZE(1.0f, 1.0f, "Bronze"),
    SILVER(1.5f, 2.0f, "Silver"),
    GOLD(2.0f, 3.5f, "Gold"),
    LEGENDARY(4.0f, 10.0f, "Legendary")
}

enum class MissionEvent { 
    COIN_COLLECTED, METER_CLIMBED, NEAR_MISS, SECOND_SURVIVED,
    MORNING_JUMP, NO_POWERUP_SCORE, TOTAL_COINS, PLATFORMS_LANDED, TOTAL_PLATFORMS,
    PACIFIST_SUCCESS, COMBO_KING_SUCCESS, PERFECT_LANDING, EDGE_LANDING,
    NEEDLE_THREADED, COIN_MAGNETISM_SUCCESS, SPEEDRUNNER_SUCCESS,
    GRAVITY_DEFIER_SUCCESS, HEART_ATTACK_SUCCESS,
    MAGNET_COLLECTED, NO_JETPACK_RUN, NEAR_MISS_TIMED, BLUE_PLATFORMS_RUN
}

enum class MissionType(val id: Int) {
    Height(0), Collect(1), NearMiss(2), Survive(3),
    Pacifist(4), ComboKing(5), PerfectLanding(6), BlindJumper(7), 
    SpeedRunner(8), ThreadNeedle(9), CoinMagnet(10), EdgeLanding(11), 
    GravityDefier(12), HeartAttack(13), NoJetpack(14), NearMissTimed(15), 
    BluePlatformRun(16), Magnetic(17);
    
    companion object {
        fun fromId(id: Int) = values().find { it.id == id } ?: Height
    }
}

data class Mission(
    val slot: Int,
    val type: MissionType,
    val title: String,
    val desc: String,
    val target: Int,
    val reward: Int,
    var progress: Int = 0,
    var completed: Boolean = false,
    var claimed: Boolean = false,
    val unlocksSkin: Int = -1
)

class MissionManager(private val saveManager: SaveManager, private val currencyManager: CurrencyManager) {
    val missions = mutableListOf<Mission>()
    var onMissionCompleted: ((Mission) -> Unit)? = null
    var onStreakUnlockedCallback: (() -> Unit)? = null

    private val ALL_MISSIONS = listOf(
        // Easy
        Mission(0, MissionType.Height, "Level 1: Reach Altitude", "Reach 100m altitude", 100, 50),
        Mission(1, MissionType.Collect, "Level 2: Collect Coins", "Collect 10 coins", 10, 50),
        Mission(2, MissionType.Survive, "Level 3: Survive Time", "Survive for 10 seconds", 10, 50),
        Mission(3, MissionType.ComboKing, "Level 4: Jump Streak", "Jump 5 times in a row", 1, 50),
        Mission(4, MissionType.Collect, "Level 5: Collect 30 Coins", "Collect 30 coins", 30, 100),
        Mission(5, MissionType.Height, "Level 6: Reach 200m", "Reach 200m altitude", 200, 100),
        Mission(6, MissionType.PerfectLanding, "Level 7: Perfect Drop", "Get 1 Perfect Landing", 1, 50),
        Mission(7, MissionType.Magnetic, "Level 8: Find Magnet", "Collect 1 Magnet", 1, 50),
        Mission(8, MissionType.EdgeLanding, "Level 9: Living on Edge", "Get 1 Edge Landing", 1, 50),
        Mission(9, MissionType.Pacifist, "Level 10: Poverty Run", "Score 1000m with 0 coins", 1, 100),
        // Medium
        Mission(10, MissionType.Height, "Level 11: Reach 800m", "Reach 800m altitude", 800, 300),
        Mission(11, MissionType.GravityDefier, "Level 12: Power Up", "Collect 3 Power-ups", 1, 300),
        Mission(12, MissionType.Survive, "Level 13: Survive 40s", "Survive for 40 seconds", 40, 300),
        Mission(13, MissionType.Collect, "Level 14: Collect 50 Coins", "Collect 50 coins", 50, 250),
        Mission(14, MissionType.NearMiss, "Level 15: Close Call", "Dodge super close to 1 enemy", 1, 200),
        Mission(15, MissionType.ComboKing, "Level 16: Jump Master", "Jump 5 times in a row 3 times", 3, 300),
        Mission(16, MissionType.PerfectLanding, "Level 17: Perfect Flow", "Get 3 Perfect Landings", 3, 300),
        Mission(17, MissionType.SpeedRunner, "Level 18: Speed Run", "Reach 1000m in 25s", 1, 300),
        Mission(18, MissionType.Height, "Level 19: Reach 1200m", "Reach 1200m altitude", 1200, 400),
        Mission(19, MissionType.BluePlatformRun, "Level 20: Blue Run", "Reach 1500m on blue platforms", 1, 500),
        // Hard
        Mission(20, MissionType.BlindJumper, "Level 21: No Powerups", "Reach 500m safely with no powerups", 1, 800),
        Mission(21, MissionType.HeartAttack, "Level 22: Matrix Dodge", "Dodge super close to 3 enemies", 1, 1000),
        Mission(22, MissionType.PerfectLanding, "Level 23: Perfect Legend", "Get 5 Perfect Landings", 5, 800),
        Mission(23, MissionType.NoJetpack, "Level 24: Manual Climb", "Reach 2000m without any Jetpack", 1, 1000),
        Mission(24, MissionType.NearMissTimed, "Level 25: Fast Reflex", "3 Close calls in under 10 seconds", 1, 1500),
        Mission(25, MissionType.ThreadNeedle, "Level 26: Thread Needle", "Jump directly between 2 enemies", 1, 800),
        Mission(26, MissionType.CoinMagnet, "Level 27: Collect 120 Coins", "Collect 120 coins in one jump", 1, 800),
        Mission(27, MissionType.Height, "Level 28: Reach 5000m", "Reach 5000m altitude", 5000, 2000, unlocksSkin = 11),
        Mission(28, MissionType.BluePlatformRun, "Level 29: Blue Legend", "Reach 2000m on blue platforms", 1, 1500),
        Mission(29, MissionType.Survive, "Level 30: Marathon", "Survive for 100 seconds", 100, 2000)
    )

    init {
        loadMissions()
    }

    private fun loadMissions() {
        missions.clear()
        
        val currentIndex = saveManager.getInt("current_linear_mission_index", 0)
        val currentProgress = saveManager.getInt("current_linear_mission_prog", 0)
        
        for ((i, template) in ALL_MISSIONS.withIndex()) {
            val mission = template.copy()
            if (i < currentIndex) {
                mission.progress = mission.target
                mission.completed = true
                mission.claimed = true
            } else if (i == currentIndex) {
                mission.progress = currentProgress
                mission.completed = false
                mission.claimed = false
            } else {
                mission.progress = 0
                mission.completed = false
                mission.claimed = false
            }
            missions.add(mission)
        }
    }

    fun trackEvent(event: MissionEvent, amount: Int = 1) {
        val currentIndex = saveManager.getInt("current_linear_mission_index", 0)
        if (currentIndex >= missions.size) return 
        
        val m = missions[currentIndex]

        val matched = when (event) {
            MissionEvent.COIN_COLLECTED -> m.type == MissionType.Collect
            MissionEvent.METER_CLIMBED -> m.type == MissionType.Height
            MissionEvent.NEAR_MISS -> m.type == MissionType.NearMiss
            MissionEvent.SECOND_SURVIVED -> m.type == MissionType.Survive
            
            MissionEvent.NO_POWERUP_SCORE -> m.type == MissionType.BlindJumper
            MissionEvent.COMBO_KING_SUCCESS -> m.type == MissionType.ComboKing
            MissionEvent.PERFECT_LANDING -> m.type == MissionType.PerfectLanding
            MissionEvent.SPEEDRUNNER_SUCCESS -> m.type == MissionType.SpeedRunner
            MissionEvent.COIN_MAGNETISM_SUCCESS -> m.type == MissionType.CoinMagnet
            
            MissionEvent.PACIFIST_SUCCESS -> m.type == MissionType.Pacifist
            MissionEvent.NEEDLE_THREADED -> m.type == MissionType.ThreadNeedle
            MissionEvent.EDGE_LANDING -> m.type == MissionType.EdgeLanding
            MissionEvent.GRAVITY_DEFIER_SUCCESS -> m.type == MissionType.GravityDefier
            MissionEvent.HEART_ATTACK_SUCCESS -> m.type == MissionType.HeartAttack
            
            MissionEvent.MAGNET_COLLECTED -> m.type == MissionType.Magnetic
            MissionEvent.NO_JETPACK_RUN -> m.type == MissionType.NoJetpack
            MissionEvent.NEAR_MISS_TIMED -> m.type == MissionType.NearMissTimed
            MissionEvent.BLUE_PLATFORMS_RUN -> m.type == MissionType.BluePlatformRun
            else -> false
        }

        if (matched) {
            m.progress += amount
            if (m.progress >= m.target) {
                m.progress = m.target
                m.completed = true
                m.claimed = true // Auto-claim
                
                onMissionCompleted?.invoke(m)
                trackDailyStreak()
                
                saveManager.saveInt("current_linear_mission_index", currentIndex + 1)
                saveManager.saveInt("current_linear_mission_prog", 0)
                
                if (currentIndex + 1 < missions.size) {
                    missions[currentIndex + 1].progress = 0
                }
            } else {
                saveManager.saveInt("current_linear_mission_prog", m.progress)
            }
        }
    }
    

    
    private fun trackDailyStreak() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        var claimedToday = saveManager.getInt("claimed_today_$today", 0)
        claimedToday++
        saveManager.saveInt("claimed_today_$today", claimedToday)

        if (claimedToday == 3) {
            val lastDate = saveManager.getString("last_streak_date", "")
            var streak = saveManager.getInt("daily_streak", 0)
            
            if (lastDate != today) {
                // Determine if streak continues
                try {
                    val format = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                    val lastDateObj = if (lastDate.isNotEmpty()) format.parse(lastDate) else null
                    val todayObj = format.parse(today)
                    if (lastDateObj != null && todayObj != null && (todayObj.time - lastDateObj.time) > 2L * 24 * 60 * 60 * 1000) {
                        streak = 1 // Missed a day, reset streak
                    } else {
                        streak++
                    }
                } catch (e: Exception) { streak = 1 }
                
                saveManager.saveString("last_streak_date", today)
                saveManager.saveInt("daily_streak", streak)

                if (streak >= 5) {
                    saveManager.saveBoolean("skin_unlocked_12", true) // Unlock Chrome Edition
                    onStreakUnlockedCallback?.invoke()
                }
            }
        }
    }
    

}
