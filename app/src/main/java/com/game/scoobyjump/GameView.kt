package com.game.scoobyjump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

enum class GameState { HOME, PLAYING, PAUSED, GAME_OVER, AD_PLAYING }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var gameThread: GameThread? = null
    var gameState = GameState.HOME
    
    // Core metrics
    private var screenWidth = 0f
    private var screenHeight = 0f
    
    // Managers
    private var player: Player? = null
    private var cameraController: CameraController? = null
    private var platformManager: PlatformManager? = null
    var scoreManager: ScoreManager = ScoreManager()
        private set
    private var enemyManager: EnemyManager? = null
    private var hazardManager: HazardManager? = null
    private var powerUpManager: PowerUpManager? = null
    private var backgroundManager: BackgroundManager? = null
    
    // Callbacks for UI
    var onGameOver: ((Int) -> Unit)? = null
    var onScoreChange: ((Int) -> Unit)? = null
    var onCoinsChange: ((Int) -> Unit)? = null
    var onSpiritChargeChange: ((Int) -> Unit)? = null

    var lastPossessedUseScore = -3000
    var isPossessedButtonReady = false
    var onPossessedReadyChange: ((Boolean) -> Unit)? = null

    var missionManager: MissionManager? = null
    var currencyManager: CurrencyManager? = null
    var analyticsManager: AnalyticsManager? = null
    var localHistoryManager: LocalHistoryManager? = null
    
    private var particleManager: ParticleManager? = null
    private var runStartTime: Long = 0L
    var sessionCoins = 0
    private var platformsLanded = 0
    private var powerUpUsedThisRun = false
    var spiritCharge = 0
        private set
        
    var ghostPathJson: String = "[]"
    private val recordedGhostPath = mutableListOf<Pair<Int, Float>>()
    private var historicalGhostPath: List<Pair<Int, Float>> = emptyList()
    private var localHighestScore = 0
    
    // Advanced Mission Trackers
    private var coinsCollectedThisRun = 0
    private var perfectLandingsThisRun = 0
    private var edgeLandingsThisRun = 0
    private var nearMissesThisRun = 0
    private var powerUpsThisRun = 0
    private var coinsInCurrentJump = 0
    private var platformsInCurrentJump = 0
    private var highestYInCurrentJump = 0f

    // Hard Mission Trackers
    private var runUsedJetpack = false
    private var landedOnNonBlueThisRun = false
    private val nearMissCallTimes = mutableListOf<Long>()

    // Juice & Review Mechanisms
    private data class FloatingText(var x: Float, var y: Float, var life: Float, val text: String, val color: Int)
    private val floatingTexts = mutableListOf<FloatingText>()
    private val floatingTextPaint = Paint().apply {
        textSize = 45f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private val warningPaint = Paint().apply {
        color = Color.RED
        textSize = 70f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(15f, 0f, 0f, Color.YELLOW)
    }

    // Audio reference
    var audioManager: AudioManager? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        
        initializeGame()

        gameThread = GameThread(holder, this)
        gameThread?.setRunning(true)
        gameThread?.start()
    }
    
    fun suspendMainLoop() {
        gameThread?.suspendMainLoop()
    }

    fun resumeMainLoop() {
        gameThread?.resumeMainLoop()
    }

    fun addSpiritCharge(amount: Int) {
        if (player?.isGhostSprintActive == true) return
        val prev = spiritCharge
        spiritCharge = (spiritCharge + amount).coerceIn(0, 100)
        if (prev != spiritCharge) {
            onSpiritChargeChange?.invoke(spiritCharge)
        }
    }

    fun triggerManualOverdrive() {
        if (spiritCharge >= 100) {
            spiritCharge = 0
            onSpiritChargeChange?.invoke(0)
            player?.activateGhostSprint(300)
            cameraController?.triggerShake(15f, 20)
            audioManager?.playPowerUp()
        }
    }

    fun spawnManualPossessed() {
        if (isPossessedButtonReady) {
            platformManager?.spawnManualPossessedPlatform()
            lastPossessedUseScore = scoreManager.getTotalScore()
            isPossessedButtonReady = false
            onPossessedReadyChange?.invoke(false)
        }
    }

    fun changeClimate() {
        backgroundManager?.randomizeClimate()
    }
    
    fun initializeGame(skinColor: String = "#E91E63") {
        backgroundManager = BackgroundManager(screenWidth, screenHeight, context)
        cameraController = CameraController(screenHeight)
        player = Player(screenWidth, screenHeight, context, skinColor)
        
        platformManager = PlatformManager(screenWidth, screenHeight, context)
        platformManager?.spawnInitialPlatforms(screenHeight)
        
        // Initial Collision Snap to exactly on top of the first platform
        val pMs = platformManager?.getPlatforms()
        val p = player
        if (pMs != null && pMs.isNotEmpty() && p != null) {
            val startP = pMs[0]
            p.x = startP.x + startP.width / 2f - p.width / 2f
            p.y = startP.y - p.height
        }
        
        enemyManager = EnemyManager(screenWidth, screenHeight)
        hazardManager = HazardManager(screenWidth, screenHeight)
        powerUpManager = PowerUpManager(screenWidth, screenHeight)
        particleManager = ParticleManager()
        
        scoreManager.reset()
        sessionCoins = 0
        spiritCharge = 0
        platformsLanded = 0
        powerUpUsedThisRun = false
        coinsCollectedThisRun = 0
        perfectLandingsThisRun = 0
        edgeLandingsThisRun = 0
        nearMissesThisRun = 0
        powerUpsThisRun = 0
        coinsInCurrentJump = 0
        platformsInCurrentJump = 0
        highestYInCurrentJump = player?.y ?: 0f
        
        runUsedJetpack = false
        landedOnNonBlueThisRun = false
        nearMissCallTimes.clear()
        
        val highestRun = localHistoryManager?.getHighestScoreRun()
        localHighestScore = highestRun?.score ?: 0
        if (highestRun != null && highestRun.ghostPathJson.length > 2) {
            try {
                val array = org.json.JSONArray(highestRun.ghostPathJson)
                val path = mutableListOf<Pair<Int, Float>>()
                for(i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    path.add(Pair(obj.getInt("s"), obj.getDouble("y").toFloat()))
                }
                historicalGhostPath = path
            } catch(e: Exception) { e.printStackTrace(); historicalGhostPath = emptyList() }
        } else {
            historicalGhostPath = emptyList()
        }
        recordedGhostPath.clear()
        
        onScoreChange?.invoke(0)
        onCoinsChange?.invoke(0)
        onSpiritChargeChange?.invoke(0)
        
        runStartTime = System.currentTimeMillis()
        analyticsManager?.logGameStart()
        
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour < 10) {
            missionManager?.trackEvent(MissionEvent.MORNING_JUMP, 1)
        }
    }

    fun applyStartingPowerUp(type: String) {
        powerUpUsedThisRun = true
        when (type) {
            "shield" -> {
                player?.activateShield(999999)
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "SHIELD EQUIPPED!", Color.CYAN))
            }
            "magnet" -> {
                player?.activateMagnet(999999)
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "MAGNET EQUIPPED!", Color.parseColor("#E040FB")))
            }
            "boost" -> {
                player?.activateJetpack()
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "BOOST EQUIPPED!", Color.YELLOW))
            }
            "double" -> {
                scoreManager.multiplier = 2
                player?.activateMultiplier(999999)
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "DOUBLE SCORE EQUIPPED!", Color.parseColor("#FFDF00")))
            }
            "antigravity" -> {
                player?.activateAntiGravity()
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "ANTI-GRAVITY READY!", Color.parseColor("#80DEEA")))
            }
            "doublejump" -> {
                player?.gainDoubleJump()
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "DOUBLE JUMP READY!", Color.parseColor("#FF9800")))
            }
            "energygem" -> {
                spiritCharge = 100
                onSpiritChargeChange?.invoke(spiritCharge)
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "SPIRIT CHARGED!", Color.parseColor("#4CAF50")))
            }
            "legendary" -> {
                player?.activateGhostSprint(300)
                scoreManager.addBonus(10000)
                floatingTexts.add(FloatingText(screenWidth/2, screenHeight/2 - 100f, 1f, "LEGENDARY STAR!", Color.parseColor("#FFC107")))
            }
        }
        audioManager?.playPowerUp()
        particleManager?.spawnExplosion(screenWidth/2, screenHeight/2, Color.YELLOW)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread?.setRunning(false)
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun update() {
        if (gameState != GameState.PLAYING) return

        val prevScore = scoreManager.getTotalScore()

        // Player Updates
        player?.update()
        
        val p = player
        if (p != null) {
            cameraController?.update(p.y)
            if (p.y < highestYInCurrentJump) {
                highestYInCurrentJump = p.y
            }
            if (!p.isMultiplierActive && scoreManager.multiplier > 1) {
                scoreManager.multiplier = 1
            }
            
            val currScore = scoreManager.getTotalScore()
            if (recordedGhostPath.isEmpty() || currScore >= recordedGhostPath.last().first + 20) {
                recordedGhostPath.add(Pair(currScore, p.y))
            }
            
            // Space Mode Milestone
            if (currScore >= 5000) {
                p.gravity = 0.85f
                p.jumpForceMultiplier = 1.05f
            } else {
                p.gravity = 1.0f
                p.jumpForceMultiplier = 1.0f
            }
            
            // HDR Glow Emission
            if (localHighestScore > 0) {
                val ratio = (currScore.toFloat() / localHighestScore.toFloat()).coerceIn(0f, 1f)
                p.glowIntensity = ratio
            }
        }
        
        val camY = cameraController?.cameraY ?: 0f
        val difficulty = (scoreManager.getTotalScore() / 1500f).coerceIn(0f, 10f)
        enemyManager?.update(camY, difficulty, scoreManager.getTotalScore())
        platformManager?.update(camY, scoreManager.getTotalScore())
        // hazardManager update is called later on line 283 so removing the erroneous call here
        
        val enemies = enemyManager?.getEnemies()
        if (enemies != null && p != null && !p.isGhostSprintActive) {
            for (e in enemies) {
                // Vanguard Warning Spark
                val distY = p.y - e.y
                if (distY > 0 && distY < 800f) {
                    if (Math.random() < 0.1) {
                        particleManager?.spawnWarningSpark(e.x + e.width / 2, p.y + p.height - 20f)
                    }
                }
            }
        }
        
        val ftIter = floatingTexts.iterator()
        while(ftIter.hasNext()) {
            val ft = ftIter.next()
            ft.life -= 0.02f
            ft.y -= 3f // floats up
            if (ft.life <= 0) ftIter.remove()
        }
        
        hazardManager?.update(camY, difficulty)
        powerUpManager?.update(camY)
        scoreManager.updateCameraScore(camY)
        particleManager?.update()
        
        val currScore = scoreManager.getTotalScore()
        backgroundManager?.updateClimateBasedOnScore(currScore)
        
        // Manual Possessed Platform Cooldown Check
        if (currScore >= lastPossessedUseScore + 3000) {
            if (!isPossessedButtonReady) {
                isPossessedButtonReady = true
                onPossessedReadyChange?.invoke(true)
            }
        } else {
            if (isPossessedButtonReady) {
                isPossessedButtonReady = false
                onPossessedReadyChange?.invoke(false)
            }
        }

        val runTimeSeconds = ((System.currentTimeMillis() - runStartTime) / 1000).toInt()
        missionManager?.trackEvent(MissionEvent.SECOND_SURVIVED, runTimeSeconds)
        missionManager?.trackEvent(MissionEvent.METER_CLIMBED, scoreManager.getTotalScore())
        if (!powerUpUsedThisRun) {
            missionManager?.trackEvent(MissionEvent.NO_POWERUP_SCORE, scoreManager.getTotalScore())
        }
        if (!runUsedJetpack && scoreManager.getTotalScore() >= 2000) {
            missionManager?.trackEvent(MissionEvent.NO_JETPACK_RUN, 1)
        }
        if (!landedOnNonBlueThisRun && scoreManager.getTotalScore() >= 1500) {
            missionManager?.trackEvent(MissionEvent.BLUE_PLATFORMS_RUN, 1)
        }
        
        // Pacifist & Speedrunner
        if (coinsCollectedThisRun == 0 && scoreManager.getTotalScore() >= 1000) {
            missionManager?.trackEvent(MissionEvent.PACIFIST_SUCCESS, 1)
        }
        if (scoreManager.getTotalScore() >= 1000 && runTimeSeconds <= 25) {
            missionManager?.trackEvent(MissionEvent.SPEEDRUNNER_SUCCESS, 1)
        }

        if (scoreManager.getTotalScore() != prevScore) {
            onScoreChange?.invoke(scoreManager.getTotalScore())
        }

        checkCollisions()
        
        // Game Over: Fall out
        if (p != null && cameraController != null) {
           val playerScreenY = p.y - camY
           val fallBoundary = if (scoreManager.getTotalScore() < 500) screenHeight + 200f else screenHeight
           if (playerScreenY > fallBoundary) {
               cameraController?.triggerShake(20f, 30) // Shake on fall out
               triggerGameOver("Fell out of bounds")
           }
        }
    }

    private fun checkCollisions() {
        val p = player ?: return
        
        // 1. PowerUp Collection
        val powerUpType = powerUpManager?.checkCollision(p)
        if (powerUpType != null) {
            powerUpUsedThisRun = true
            powerUpsThisRun++
            if (powerUpsThisRun >= 3) {
                missionManager?.trackEvent(MissionEvent.GRAVITY_DEFIER_SUCCESS, 1)
            }
            scoreManager.addBonus(500)
            audioManager?.playPowerUp()
            particleManager?.spawnExplosion(p.x + p.width/2, p.y + p.height/2, android.graphics.Color.YELLOW)
            val powerUpSave = SaveManager(context)
            when (powerUpType) {
                PowerUpType.JETPACK -> {
                    val level = powerUpSave.getInt("powerup_level_lightning", 1)
                    val duration = 180 + ((level - 1) * 120) // Base 3 sec + 2 sec per level
                    p.activateJetpack(duration)
                    runUsedJetpack = true
                }
                PowerUpType.SHIELD -> {
                    val level = powerUpSave.getInt("powerup_level_shield", 1)
                    val duration = 300 + ((level - 1) * 120) // Base 5 sec + 2 sec per level
                    p.activateShield(duration)
                }
                PowerUpType.DOUBLE_JUMP -> p.gainDoubleJump()
                PowerUpType.ANTI_GRAVITY -> p.activateAntiGravity()
                PowerUpType.MAGNET -> {
                    val level = powerUpSave.getInt("powerup_level_magnet", 1)
                    val duration = 600 + ((level - 1) * 120) // Base 10 sec + 2 sec per level
                    p.activateMagnet(duration)
                    missionManager?.trackEvent(MissionEvent.MAGNET_COLLECTED, 1)
                    floatingTexts.add(FloatingText(p.x, p.y, 1f, "MAGNET!", Color.CYAN))
                }
                PowerUpType.ENERGY_GEM -> {
                    addSpiritCharge(25)
                    floatingTexts.add(FloatingText(p.x, p.y, 1f, "+25 SPIRIT!", Color.parseColor("#FF1493")))
                }
                PowerUpType.MULTIPLIER -> {
                    p.activateMultiplier(900)
                    scoreManager.multiplier = 2 // Double score
                    floatingTexts.add(FloatingText(p.x, p.y, 1f, "2X SCORE!", Color.parseColor("#FFDF00")))
                }
                PowerUpType.LEGENDARY_STAR -> {
                    p.activateGhostSprint(300)
                    scoreManager.addBonus(10000)
                    cameraController?.triggerShake(25f, 30)
                    floatingTexts.add(FloatingText(p.x, p.y, 1f, "LEGENDARY!", Color.WHITE))
                }
            }
        }

        // 1.5 Coin Collection
        val platforms = platformManager?.getPlatforms()
        if (platforms != null) {
            for (platform in platforms) {
                if (platform.hasCoin) {
                    val cx = platform.x + platform.width / 2
                    val cy = platform.y - platform.coinRadius - 10f
                    val px = p.x + p.width / 2
                    val py = p.y + p.height / 2
                    
                    val dx = cx - px
                    val dy = cy - py
                    val distanceSq = dx*dx + dy*dy
                    val hitRadii = platform.coinRadius + (p.width / 2)
                    
                    var vacuumHit = false
                    if (p.isGhostSprintActive) {
                        val distX = kotlin.math.abs(cx - px)
                        val distYY = py - cy
                        // In Ghost sprint, suck up coins that we pass
                        if (distX < 250f && distYY in -100f..400f) {
                            vacuumHit = true
                        }
                    }
                    if (p.isMagnetActive) {
                        val distanceSqM = dx*dx + dy*dy
                        if (distanceSqM < 600f * 600f) {
                            vacuumHit = true
                        }
                    }
                    
                    if (distanceSq < hitRadii * hitRadii || vacuumHit) {
                        platform.hasCoin = false
                        sessionCoins++
                        coinsCollectedThisRun++
                        coinsInCurrentJump++
                        missionManager?.trackEvent(MissionEvent.COIN_COLLECTED, 1)
                        missionManager?.trackEvent(MissionEvent.TOTAL_COINS, sessionCoins)
                        audioManager?.playPowerUp()
                        particleManager?.spawnExplosion(cx, cy, android.graphics.Color.YELLOW)
                        onCoinsChange?.invoke(sessionCoins)
                        addSpiritCharge(2)
                    }
                }
            }
        }

        // 2. Fatal Collisions
        val hitEnemy = enemyManager?.getCollidingEnemy(p)
        if (hitEnemy != null) {
            if (p.isStomping) {
                // Combat: Destroy target, exaggerated bounce
                enemyManager?.removeEnemy(hitEnemy)
                p.jump(1.5f) // Exaggerated bounce
                particleManager?.spawnStompImpact(p.x + p.width/2, p.y + p.height/2)
                scoreManager.addBonus(200)
                audioManager?.playLand()
            } else if (!p.isShieldActive && !p.isGhostSprintActive) {
                audioManager?.playCrash()
                cameraController?.triggerShake(25f, 20)
                particleManager?.spawnExplosion(p.x + p.width/2, p.y + p.height/2, android.graphics.Color.RED)
                triggerGameOver("Hit Enemy")
                return
            }
        } else if (hazardManager?.checkCollision(p) == true) {
            if (!p.isShieldActive && !p.isGhostSprintActive) {
                audioManager?.playCrash()
                cameraController?.triggerShake(25f, 20)
                particleManager?.spawnExplosion(p.x + p.width/2, p.y + p.height/2, android.graphics.Color.RED)
                triggerGameOver("Hit Hazard")
                return
            }
        }
        
        // 2.1 Near-Miss Call (Juice Loop)
        if (!p.isGhostSprintActive && !p.isShieldActive) {
            val enemies = enemyManager?.getEnemies()
            if (enemies != null) {
                for (e in enemies) {
                    if (!e.nearMissTriggered) {
                        val cx = e.x + e.width / 2
                        val cy = e.y + e.height / 2
                        val px = p.x + p.width / 2
                        val py = p.y + p.height / 2
                        val distSq = (cx - px) * (cx - px) + (cy - py) * (cy - py)
                        if (distSq < 225f * 225f) {
                            e.nearMissTriggered = true
                            nearMissesThisRun++
                            if (nearMissesThisRun >= 10) {
                                missionManager?.trackEvent(MissionEvent.HEART_ATTACK_SUCCESS, 1)
                            }
                            
                            val now = System.currentTimeMillis()
                            nearMissCallTimes.add(now)
                            nearMissCallTimes.removeAll { now - it > 10000L }
                            if (nearMissCallTimes.size >= 3) {
                                missionManager?.trackEvent(MissionEvent.NEAR_MISS_TIMED, 1)
                                nearMissCallTimes.clear()
                            }
                            
                            triggerCloseCall(p.x + p.width/2, p.y - 50f)
                        }
                    }
                }
            }
        }

        // 2.5 Platform Ceilings (Bonk when moving up)
        if (p.velocityY < 0 && !p.jetpackActive && !p.isGhostSprintActive) {
            if (platforms != null) {
                for (platform in platforms) {
                    if (platform.isBroken) continue
                    
                    if (p.x + p.width > platform.x && p.x < platform.x + platform.width) {
                        val playerTop = p.y
                        val playerOldTop = playerTop - p.velocityY
                        val platformBottom = platform.y + platform.height
                        
                        // Check if player's head crossed the bottom of the platform
                        if (playerOldTop >= platformBottom - 16f && playerTop <= platformBottom + 16f) {
                            p.y = platformBottom
                            p.velocityY = 0f // Bonk! Stop upward momentum
                            audioManager?.playLand() // Tap
                            break
                        }
                    }
                }
            }
        }

        // 3. Platform Landings
        if (p.velocityY > 0 && !p.jetpackActive) {
            if (platforms != null) {
                for (platform in platforms) {
                    if (platform.isBroken) continue
                    
                    if (p.x + p.width > platform.x - 20f && p.x < platform.x + platform.width + 20f) {
                        val playerBottom = p.y + p.height
                        val playerOldBottom = playerBottom - p.velocityY
                        
                        // Wait: if stomping, break platform if breakable!
                        if (p.isStomping) {
                            if (platform is BreakablePlatform) {
                                platform.isBroken = true
                                particleManager?.spawnStompImpact(platform.x + platform.width / 2, platform.y)
                                audioManager?.playCrash()
                                continue // don't land on it
                            }
                        }

                        if (playerOldBottom <= platform.y + 24f && playerBottom >= platform.y - 24f) {
                            if (platform !is MovingPlatform) {
                                landedOnNonBlueThisRun = true
                            }
                            val forceMultiplier = if (p.isStomping) 1.5f else platform.onLandedOn()
                            p.jump(forceMultiplier)
                            particleManager?.spawnDust(p.x + p.width / 2, p.y + p.height)
                            audioManager?.playLand()
                            audioManager?.playJump()
                            
                            val pCx = p.x + p.width / 2
                            val platCx = platform.x + platform.width / 2
                            val dist = kotlin.math.abs(pCx - platCx)
                            
                            if (dist < 15f) {
                                perfectLandingsThisRun++
                                missionManager?.trackEvent(MissionEvent.PERFECT_LANDING, 1)
                                particleManager?.spawnExplosion(pCx, p.y + p.height, Color.CYAN)
                                floatingTexts.add(FloatingText(pCx, p.y, 1f, "PERFECT!", Color.CYAN))
                                addSpiritCharge(15)
                            } else if (dist > (platform.width / 2 - 15f)) {
                                edgeLandingsThisRun++
                                missionManager?.trackEvent(MissionEvent.EDGE_LANDING, 1)
                                particleManager?.spawnExplosion(pCx, p.y + p.height, Color.RED)
                                floatingTexts.add(FloatingText(pCx, p.y, 1f, "EDGE!", Color.RED))
                            }
                            
                            platformsInCurrentJump++
                            if (platformsInCurrentJump >= 5) {
                                missionManager?.trackEvent(MissionEvent.COMBO_KING_SUCCESS, 1)
                            }
                            if (coinsInCurrentJump >= 10) {
                                missionManager?.trackEvent(MissionEvent.COIN_MAGNETISM_SUCCESS, 1)
                            }
                            
                            val fallDist = p.y - highestYInCurrentJump
                            if (fallDist > 700f) {
                                missionManager?.trackEvent(MissionEvent.NEEDLE_THREADED, 1)
                                floatingTexts.add(FloatingText(pCx, p.y - 50f, 1f, "THREADED!", Color.MAGENTA))
                                addSpiritCharge(15)
                            }
                            
                            platformsInCurrentJump = 0
                            coinsInCurrentJump = 0
                            highestYInCurrentJump = p.y
                            
                            if (platform.isPossessed) {
                                p.activateGhostSprint(180) // 3 seconds burst
                                platform.isPossessed = false
                                cameraController?.triggerShake(15f, 15)
                                particleManager?.spawnExplosion(platCx, platform.y, Color.parseColor("#E040FB"))
                                floatingTexts.add(FloatingText(pCx, p.y - 80f, 1f, "POSSESSED!", Color.parseColor("#E040FB")))
                            }
                            
                            platformsLanded++
                            missionManager?.trackEvent(MissionEvent.PLATFORMS_LANDED, platformsLanded)
                            missionManager?.trackEvent(MissionEvent.TOTAL_PLATFORMS, platformsLanded)
                            break 
                        }
                    }
                }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        val cameraY = cameraController?.cameraY ?: 0f
        
        // Draw Dynamic Background First
        backgroundManager?.draw(canvas, cameraY)
        
        platformManager?.draw(canvas, cameraY)
        enemyManager?.draw(canvas, cameraY)
        
        val enemiesList = enemyManager?.getEnemies()
        if (enemiesList != null && gameState == GameState.PLAYING) {
            val p = player
            for (e in enemiesList) {
                if (p != null) {
                    val cx = p.x + p.width/2f
                    val cy = p.y + p.height/2f - cameraY
                    val dx = (e.x + e.width/2f) - (p.x + p.width/2f)
                    val dy = e.y - p.y
                    val distSq = dx*dx + dy*dy
                    if (distSq < 300f*300f && distSq > 50f*50f) {
                        val dist = kotlin.math.sqrt(distSq.toDouble()).toFloat()
                        val nx = dx / dist
                        val ny = dy / dist
                        warningPaint.alpha = (150 + Math.sin(System.currentTimeMillis()/100.0).toFloat()*105).toInt().coerceIn(0,255)
                        canvas.drawText("!", cx + nx * 100f, cy + ny * 100f + 25f, warningPaint)
                    }
                }
                
                if (e.y < cameraY) {
                    // Enemy is above screen bounds
                    warningPaint.alpha = 255
                    val drawX = Math.max(40f, Math.min(screenWidth - 40f, e.x + e.width / 2))
                    canvas.drawText("!", drawX, 90f, warningPaint)
                }
            }
        }
        hazardManager?.draw(canvas, cameraY)
        powerUpManager?.draw(canvas, cameraY)
        particleManager?.draw(canvas, cameraY)
        
        if (gameState != GameState.HOME) {
            player?.draw(canvas, cameraY)
            
            val currScore = scoreManager.getTotalScore()
            if (localHighestScore > 50 && currScore >= localHighestScore - 50 && historicalGhostPath.isNotEmpty()) {
                val ghostPoint = historicalGhostPath.minByOrNull { kotlin.math.abs(it.first - currScore) }
                if (ghostPoint != null) {
                    val ghostPaint = Paint().apply {
                        color = Color.WHITE
                        alpha = 76 // 30% of 255
                    }
                    val gx = (screenWidth / 2f) + (Math.sin(System.currentTimeMillis()/200.0)*20).toFloat()
                    val gy = ghostPoint.second - cameraY
                    canvas.drawRoundRect(gx, gy, gx + 80f, gy + 80f, 24f, 24f, ghostPaint)
                    canvas.drawText("GHOST", gx + 40f, gy - 20f, floatingTextPaint.apply { color = Color.WHITE; alpha = 100 })
                }
            }
            
            for (ft in floatingTexts) {
                floatingTextPaint.color = ft.color
                val a = (ft.life * 255).toInt().coerceIn(0, 255)
                floatingTextPaint.alpha = a
                canvas.drawText(ft.text, ft.x, ft.y - cameraY, floatingTextPaint)
            }
        }
        
        // Removed `scoreManager.draw(canvas)` since it's now handled by XML TextView overlay
    }

    private fun triggerCloseCall(x: Float, y: Float) {
        audioManager?.playDing()
        scoreManager.addBonus(50)
        floatingTexts.add(FloatingText(x, y, 1.0f, "+50 CLOSE CALL!", Color.parseColor("#00E5FF")))
        missionManager?.trackEvent(MissionEvent.NEAR_MISS, 1)
    }

    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var lastTapTime = 0L
    private var hasUsedEmergencyDash = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState == GameState.PLAYING) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownY = event.y
                    touchDownTime = System.currentTimeMillis()
                    
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < 300) { // Double tap detect
                        val p = player
                        if (p != null && !hasUsedEmergencyDash && p.velocityY > 0f) {
                            val coins = currencyManager?.getCoins() ?: 0
                            if (coins >= 100) {
                                hasUsedEmergencyDash = true
                                currencyManager?.spendCoins(100)
                                onCoinsChange?.invoke(currencyManager?.getCoins() ?: 0)
                                p.velocityY = -45f
                                particleManager?.spawnExplosion(p.x + p.width/2, p.y + p.height/2, Color.parseColor("#FFD700"))
                                floatingTexts.add(FloatingText(p.x, p.y, 1f, "-100 DASH!", Color.YELLOW))
                            }
                        }
                    }
                    lastTapTime = currentTime
                    
                    val p = player
                    if (p != null) {
                        p.handleTouch(event)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = player
                    p?.handleTouch(event)
                }
                MotionEvent.ACTION_UP -> {
                    val p = player
                    if (p != null) {
                        val deltaY = event.y - touchDownY
                        val deltaTime = System.currentTimeMillis() - touchDownTime
                        if (deltaY > 150f && deltaTime < 400L) {
                            p?.activateStomp()
                        }
                        p?.handleTouch(event)
                    }
                }
            }
        }
        return true
    }

    fun triggerGameOver(reason: String = "Unknown") {
        if (gameState == GameState.GAME_OVER) return
        gameState = GameState.GAME_OVER
        
        val fScore = scoreManager.getTotalScore()
        analyticsManager?.logPlayerDeath(reason, fScore)
        
        // Serialize ghost path
        val array = org.json.JSONArray()
        for (pt in recordedGhostPath) {
            val obj = org.json.JSONObject()
            obj.put("s", pt.first)
            obj.put("y", pt.second.toDouble())
            array.put(obj)
        }
        ghostPathJson = array.toString()
        
        onGameOver?.invoke(fScore)
    }

    fun safeguardPlayer() {
        // Clear immediate threats
        enemyManager?.reset()
        hazardManager?.reset()
        particleManager?.clearAll()
        
        val p = player
        if (p != null) {
            val camY = cameraController?.cameraY ?: 0f
            p.y = camY + screenHeight / 2f
            p.velocityY = 0f
            p.x = screenWidth / 2f - p.width / 2f // Center align player
            // Generate golden safe platform explicitly below the character
            platformManager?.spawnGoldenPlatform(p.x, p.y + p.height + 50f)
            
            p.activateShield(180)
            particleManager?.spawnExplosion(p.x + p.width / 2, p.y + p.height / 2, android.graphics.Color.GREEN)
        }
    }

    fun prepareRevive() {
        // Used to be the body of safeguardPlayer, now handled before ad loads.
    }
    
    fun getRunTimeSeconds(): Int {
        return ((System.currentTimeMillis() - runStartTime) / 1000).toInt()
    }
    
    fun executeRevive() {
        gameState = GameState.PLAYING
        val p = player
        if (p != null) {
            p.velocityY = 0f // Physics freeze clear
            p.activateGhostSprint(600) // 10 seconds of Ghost Sprint
        }
    }
}
