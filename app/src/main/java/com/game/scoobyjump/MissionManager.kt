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
        Mission(0, MissionType.Height, "Reach 100m altitude", "Reach 100m altitude", 100, 50),
        Mission(1, MissionType.Collect, "Collect 10 Coins", "Collect 10 coins", 10, 50),
        Mission(2, MissionType.Survive, "Survive for 10 seconds", "Survive for 10 seconds", 10, 50),
        Mission(3, MissionType.ComboKing, "Jump 5 times in a row", "Jump 5 times in a row", 1, 50),
        Mission(4, MissionType.Collect, "Collect 30 Coins", "Collect 30 coins", 30, 100),
        Mission(5, MissionType.Height, "Reach 200m altitude", "Reach 200m altitude", 200, 100),
        Mission(6, MissionType.PerfectLanding, "Execute a Perfect Landing", "Get 1 Perfect Landing", 1, 50),
        Mission(7, MissionType.Magnetic, "Collect a Magnet", "Collect 1 Magnet", 1, 50),
        Mission(8, MissionType.EdgeLanding, "Execute an Edge Landing", "Get 1 Edge Landing", 1, 50),
        Mission(9, MissionType.Pacifist, "Score 1000m with 0 coins", "Score 1000m with 0 coins", 1, 100),
        // Medium
        Mission(10, MissionType.Height, "Reach 800m altitude", "Reach 800m altitude", 800, 300),
        Mission(11, MissionType.GravityDefier, "Collect 3 Power-ups", "Collect 3 Power-ups", 1, 300),
        Mission(12, MissionType.Survive, "Survive for 40 seconds", "Survive for 40 seconds", 40, 300),
        Mission(13, MissionType.Collect, "Collect 50 Coins", "Collect 50 coins", 50, 250),
        Mission(14, MissionType.NearMiss, "Dodge closely to 1 enemy", "Dodge super close to 1 enemy", 1, 200),
        Mission(15, MissionType.ComboKing, "Jump 5 times in a row 3 times", "Jump 5 times in a row 3 times", 3, 300),
        Mission(16, MissionType.PerfectLanding, "Get 3 Perfect Landings", "Get 3 Perfect Landings", 3, 300),
        Mission(17, MissionType.SpeedRunner, "Reach 1000m in 25s", "Reach 1000m in 25s", 1, 300),
        Mission(18, MissionType.Height, "Reach 1200m altitude", "Reach 1200m altitude", 1200, 400),
        Mission(19, MissionType.BluePlatformRun, "Reach 1500m on blue platforms", "Reach 1500m on blue platforms", 1, 500),
        // Hard
        Mission(20, MissionType.BlindJumper, "Reach 500m safely with no powerups", "Reach 500m safely with no powerups", 1, 800),
        Mission(21, MissionType.HeartAttack, "Matrix Dodge 3 enemies", "Dodge super close to 3 enemies", 1, 1000),
        Mission(22, MissionType.PerfectLanding, "Get 5 Perfect Landings", "Get 5 Perfect Landings", 5, 800),
        Mission(23, MissionType.NoJetpack, "Reach 2000m manual climb", "Reach 2000m without any Jetpack", 1, 1000),
        Mission(24, MissionType.NearMissTimed, "3 Close calls in under 10 seconds", "3 Close calls in under 10 seconds", 1, 1500),
        Mission(25, MissionType.ThreadNeedle, "Jump directly between 2 enemies", "Jump directly between 2 enemies", 1, 800),
        Mission(26, MissionType.CoinMagnet, "Collect 120 coins in one jump", "Collect 120 coins in one jump", 1, 800),
        Mission(27, MissionType.Height, "Reach 5000m altitude", "Reach 5000m altitude", 5000, 2000, unlocksSkin = 11),
        Mission(28, MissionType.BluePlatformRun, "Reach 2000m on blue platforms", "Reach 2000m on blue platforms", 1, 1500),
        Mission(29, MissionType.Survive, "Survive for 100 seconds", "Survive for 100 seconds", 100, 2000),
        // Expert
        Mission(30, MissionType.Pacifist, "The Ascetic", "Score 3,000m collecting exactly 0 coins.", 1, 3000),
        Mission(31, MissionType.HeartAttack, "Ghost Whisperer", "Dodge super close to 10 enemies in one life", 10, 4000),
        Mission(32, MissionType.SpeedRunner, "Speed Demon", "Reach 3,000m in under 45 seconds", 1, 5000),
        Mission(33, MissionType.NoJetpack, "Raw Muscle", "Reach 4000m without any Jetpack", 1, 5000),
        Mission(34, MissionType.Height, "Absolute Pinnacle", "Reach a staggering 10,000m altitude", 10000, 10000, unlocksSkin = 14)
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
                trackSeasonProgress()
                
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
    

    fun getSeasonDaysRemaining(): Int {
        var startEpoch = saveManager.getLong("season_start_epoch", 0L)
        if (startEpoch == 0L) {
            startEpoch = System.currentTimeMillis()
            saveManager.saveLong("season_start_epoch", startEpoch)
        }
        val currentEpoch = System.currentTimeMillis()
        val daysElapsed = ((currentEpoch - startEpoch) / (1000 * 60 * 60 * 24)).toInt()
        val daysRemaining = 21 - daysElapsed
        
        if (daysRemaining <= 0) {
            // Reset Season
            saveManager.saveLong("season_start_epoch", currentEpoch)
            saveManager.saveInt("current_linear_mission_index", 0)
            saveManager.saveInt("current_linear_mission_prog", 0)
            loadMissions()
            return 21
        }
        return daysRemaining
    }
    
    private fun trackSeasonProgress() {
        // We can add seasonal rewards here if needed
    }

}
