package com.game.scoobyjump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs
import kotlin.random.Random

class PlatformManager(private val screenWidth: Float, private val screenHeight: Float, context: Context) {
    private val platforms = mutableListOf<BasePlatform>()
    
    // Load and tint drawables efficiently once
    private val staticDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.platform_shape)?.mutate()
    private val movingDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.platform_shape)?.mutate()?.apply {
        DrawableCompat.setTint(this, Color.parseColor("#2196F3"))
    }
    private val breakableDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.platform_shape)?.mutate()?.apply {
        DrawableCompat.setTint(this, Color.parseColor("#795548"))
    }
    private val springDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.platform_shape)?.mutate()?.apply {
        DrawableCompat.setTint(this, Color.parseColor("#FFC107"))
    }
    private val goldDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.platform_shape)?.mutate()?.apply {
        DrawableCompat.setTint(this, Color.parseColor("#FFD700"))
    }
    
    private val platformWidth = 180f
    private val platformHeight = 40f
    private val minVerticalDistance = 160f
    private val maxVerticalDistance = 280f
    
    private var highestPlatformY = 0f

    // 1. 5 Equal Lanes
    private val laneCount = 5
    private val laneWidth = screenWidth / laneCount
    private val lanes = FloatArray(laneCount) { i ->
        laneWidth * (i + 0.5f)
    }

    // 2. Shuffle Bag
    private val laneBag = mutableListOf<Int>()
    private fun getNextLane(): Int {
        if (laneBag.isEmpty()) {
            laneBag.addAll(listOf(0, 1, 2, 3, 4).shuffled())
        }
        return laneBag.removeAt(0)
    }

    // 3. Anti Repeat Rule Tracking
    private var lastLane = -1
    private var repeatCount = 0

    // 6. Zig Zag Movement
    private var direction = listOf(-1, 1).random()

    // 8. Debug Mode
    var isDebugMode = true

    private var totalSpawned = 0

    fun spawnInitialPlatforms(startScreenHeight: Float) {
        platforms.clear()
        totalSpawned = 0
        
        // 7. Safe Margins
        val margin = 24f
        val startX = (screenWidth / 2f - platformWidth / 2f).coerceIn(margin, screenWidth - platformWidth - margin)
        val startY = startScreenHeight - 200f
        platforms.add(StaticPlatform(startX, startY, platformWidth, platformHeight, staticDrawable))
        highestPlatformY = startY

        lastLane = 2 // Center lane
        repeatCount = 0
        laneBag.clear()

        // Pass 0 score for initial
        while (highestPlatformY > -startScreenHeight * 2) {
            spawnNextPlatform(0)
        }
    }

    private fun spawnNextPlatform(currentScore: Int) {
        // 5. Difficulty Scaling based on score
        var nextLane = 2
        val useZigZag = Random.nextFloat() < 0.5f // 50% chance to mix zig-zag with shuffle bag

        if (currentScore <= 50) {
            // LOW (0-50): Use only center lanes (1,2,3)
            val centerLanes = listOf(1, 2, 3)
            nextLane = centerLanes.random()
        } else if (currentScore <= 150) {
            // MID (50-150): Use all lanes, moderate variation
            if (useZigZag) {
                // Mix in Zig-Zag
                nextLane = lastLane + direction
                if (nextLane < 0 || nextLane >= laneCount) {
                    direction *= -1
                    nextLane = lastLane + direction
                }
            } else {
                nextLane = getNextLane()
            }
        } else {
            // HIGH (150+): Faster lane switching
            if (useZigZag && Random.nextFloat() < 0.7f) { // Aggressive zig zag
                nextLane = lastLane + direction
                if (nextLane < 0 || nextLane >= laneCount) {
                    direction *= -1
                    nextLane = lastLane + direction
                }
            } else {
                nextLane = getNextLane()
            }
        }

        // 3. Strict Anti-Repeat Rule (Prevent Vertical Sandwiched Ceilings)
        if (nextLane == lastLane) {
            val possibleLanes = listOf(lastLane - 1, lastLane + 1).filter { it in 0 until laneCount }
            if (possibleLanes.isNotEmpty()) {
                nextLane = possibleLanes.random()
            } else {
                nextLane = if (lastLane == 0) 1 else laneCount - 2
            }
        }
        repeatCount = 0

        // Calculate theoretical X based on center of chosen lane
        var newX = lanes[nextLane] - platformWidth / 2f

        // 4. Reachability Check (MUST IMPLEMENT)
        val playerJumpVelocityX = 16f * 25f // approximately 400f reachability horizontally
        val maxHorizontalJump = playerJumpVelocityX * 0.9f
        val previousX = platforms.lastOrNull()?.x ?: (screenWidth / 2f)
        
        val distance = abs(newX - previousX)
        if (distance > maxHorizontalJump) {
            // Pick adjacent lane instead natively to ensure it's reachable
            val directionTowardsPrev = if (previousX > newX) 1 else -1
            nextLane += directionTowardsPrev
            nextLane = nextLane.coerceIn(0, laneCount - 1)
            newX = lanes[nextLane] - platformWidth / 2f
        }

        // 7. Safe Margins
        val margin = 24f
        newX = newX.coerceIn(margin, screenWidth - platformWidth - margin)

        // 8. Debug Mode Log
        if (isDebugMode) {
            Log.d("SPAWN", "Lane: $nextLane X: $newX Score: $currentScore")
        }

        lastLane = nextLane

        var gap = Random.nextFloat() * (maxVerticalDistance - minVerticalDistance) + minVerticalDistance
        if (totalSpawned < 3) {
            gap = gap.coerceAtLeast(200f)
        }
        val newY = highestPlatformY - gap

        // Determine platform type
        val difficultyScaling = (currentScore / 1000f).coerceIn(0f, 1f)
        val roll = Random.nextFloat()
        val probMoving = 0.05f + (0.25f * difficultyScaling)
        val probBreakable = 0.0f + (0.20f * difficultyScaling)
        val probSpring = 0.05f + (0.05f * difficultyScaling)

        val platform = when {
            roll < probSpring -> SpringPlatform(newX, newY, platformWidth, platformHeight, springDrawable)
            roll < probSpring + probMoving -> MovingPlatform(newX, newY, platformWidth, platformHeight, screenWidth, movingDrawable)
            roll < probSpring + probMoving + probBreakable -> BreakablePlatform(newX, newY, platformWidth, platformHeight, breakableDrawable)
            else -> StaticPlatform(newX, newY, platformWidth, platformHeight, staticDrawable)
        }

        // Feature: Safe Path Logic Backup
        if (platform is MovingPlatform || platform is SpringPlatform) {
            val safeX = if (newX > screenWidth / 2) newX - 250f else newX + 250f
            val boundedSafeX = safeX.coerceIn(margin, screenWidth - platformWidth - margin)
            val backupPlatform = StaticPlatform(boundedSafeX, newY + 80f, platformWidth, platformHeight, staticDrawable)
            platforms.add(backupPlatform)
        }

        // Coins spawning
        if (platform !is BreakablePlatform && roll > 0.1f) {
            if (Random.nextFloat() < 0.25f) {
                platform.hasCoin = true
            }
        }

        // Possessed Platform Spawning (Disabled natively, now manual only via button)
        // if (Random.nextFloat() < 0.05f) {
        //     platform.isPossessed = true
        //     platform.hasCoin = false // Clear coins for possessed platforms
        // }

        platforms.add(platform)
        highestPlatformY = newY
        totalSpawned++
    }

    fun update(cameraY: Float, currentScore: Int) {
        val spawnThresholdY = cameraY - screenHeight * 1.5f
        while (highestPlatformY > spawnThresholdY) {
            spawnNextPlatform(currentScore)
        }

        val speedMultiplier = if (currentScore >= 10000) 1.5f else 1.0f
        val iterator = platforms.iterator()
        while (iterator.hasNext()) {
            val platform = iterator.next()
            if (platform is MovingPlatform) {
                platform.moveSpeed = 5f * speedMultiplier
            }
            platform.update()

            if (platform.y > cameraY + screenHeight + 200f || platform.isBroken) {
                iterator.remove()
            }
        }
    }

    fun draw(canvas: Canvas, cameraY: Float) {
        if (isDebugMode) {
            val lanePaint = Paint().apply {
                color = Color.argb(80, 255, 255, 255)
                strokeWidth = 3f
            }
            for (i in 1 until laneCount) {
                val lineX = i * laneWidth
                canvas.drawLine(lineX, 0f, lineX, screenHeight, lanePaint)
            }
        }

        for (platform in platforms) {
            platform.draw(canvas, cameraY)
            if (isDebugMode) {
                val spawnPaint = Paint().apply { color = Color.RED }
                canvas.drawCircle(platform.x + platformWidth / 2f, platform.y - cameraY, 8f, spawnPaint)
            }
        }
    }

    fun getPlatforms(): List<BasePlatform> {
        return platforms
    }
    
    
    fun spawnSafePlatform(x: Float, y: Float) {
        // Find safe spawn pos, clamping if needed
        val safeX = x.coerceIn(24f, screenWidth - platformWidth - 24f)
        val platform = StaticPlatform(safeX, y, platformWidth, platformHeight, staticDrawable)
        platforms.add(platform)
        
        // Ensure this platform is technically the highest so logic doesn't skip
        if (y < highestPlatformY) {
            highestPlatformY = y
        }
    }

    fun spawnGoldenPlatform(x: Float, y: Float) {
        val safeX = x.coerceIn(24f, screenWidth - platformWidth - 24f)
        val platform = StaticPlatform(safeX, y, platformWidth, platformHeight, goldDrawable)
        platforms.add(platform)
        
        if (y < highestPlatformY) {
            highestPlatformY = y
        }
    }

    fun spawnManualPossessedPlatform() {
        // Calculate the center lane coordinate directly
        val safeX = (screenWidth / 2f) - (platformWidth / 2f)
        // Spawn exactly top platform + 200 distance
        val newY = highestPlatformY - 200f
        
        val platform = StaticPlatform(safeX, newY, platformWidth, platformHeight, staticDrawable)
        platform.isPossessed = true
        platform.hasCoin = false
        platforms.add(platform)
        
        highestPlatformY = newY
    }
}
