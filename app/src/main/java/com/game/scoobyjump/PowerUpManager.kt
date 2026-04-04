package com.game.scoobyjump

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

enum class PowerUpType { 
    JETPACK, SHIELD, DOUBLE_JUMP, ANTI_GRAVITY,
    MAGNET, ENERGY_GEM, MULTIPLIER, LEGENDARY_STAR
}
data class PowerUp(val type: PowerUpType, val x: Float, val y: Float, val size: Float)

class PowerUpManager(private val screenWidth: Float, private val screenHeight: Float) {
    private val powerUps = mutableListOf<PowerUp>()
    private var lastSpawnY = 0f

    private val jetpackPaint = Paint().apply { color = Color.parseColor("#FF9800") } // Orange
    private val shieldPaint = Paint().apply { color = Color.parseColor("#00BCD4") } // Cyan
    private val doubleJumpPaint = Paint().apply { color = Color.parseColor("#8BC34A") } // Light Green
    private val antiGravityPaint = Paint().apply { color = Color.parseColor("#9C27B0") } // Deep Purple

    // New Tiered Loot Paints
    private val magnetPaint = Paint().apply { color = Color.parseColor("#00BFFF"); setShadowLayer(10f, 0f, 0f, Color.CYAN) } // Magnet Blue
    private val energyGemPaint = Paint().apply { color = Color.parseColor("#FF1493"); setShadowLayer(10f, 0f, 0f, Color.MAGENTA) } // Hot Pink
    private val multiplierPaint = Paint().apply { color = Color.parseColor("#FFDF00"); setShadowLayer(15f, 0f, 0f, Color.YELLOW) } // Gold
    private val legendaryPaint = Paint().apply { color = Color.parseColor("#FFFFFF"); setShadowLayer(25f, 0f, 0f, Color.WHITE) } // Rainbow later

    private var frameOffset = 0f

    fun update(cameraY: Float) {
        val spawnThreshold = cameraY - screenHeight * 1.5f
        while (lastSpawnY > spawnThreshold) {
            // 15% chance every 1500 units to spawn a power-up
            if (Random.nextFloat() < 0.15f) {
                val roll = Random.nextFloat()
                val type = when {
                    roll < 0.02f -> PowerUpType.LEGENDARY_STAR // 2%
                    roll < 0.12f -> PowerUpType.MULTIPLIER // 10%
                    roll < 0.32f -> PowerUpType.ENERGY_GEM // 20%
                    roll < 0.72f -> PowerUpType.MAGNET // 40%
                    else -> listOf(PowerUpType.JETPACK, PowerUpType.SHIELD, PowerUpType.DOUBLE_JUMP, PowerUpType.ANTI_GRAVITY).random() // remaining 28% legacy tech
                }
                val size = if (type == PowerUpType.LEGENDARY_STAR) 60f else 40f
                val newX = Random.nextFloat() * (screenWidth - size)
                val newY = lastSpawnY - Random.nextFloat() * 500f
                powerUps.add(PowerUp(type, newX, newY, size))
            }
            lastSpawnY -= 1500f
        }

        powerUps.removeAll { it.y > cameraY + screenHeight + 200f }
    }

    fun checkCollision(player: Player): PowerUpType? {
        val iterator = powerUps.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (player.x < p.x + p.size && player.x + player.width > p.x &&
                player.y < p.y + p.size && player.y + player.height > p.y) {
                iterator.remove()
                return p.type
            }
        }
        return null
    }

    fun draw(canvas: Canvas, cameraY: Float) {
        frameOffset += 0.1f
        for (p in powerUps) {
            val drawY = p.y - cameraY
            val paint = when (p.type) {
                PowerUpType.JETPACK -> jetpackPaint
                PowerUpType.SHIELD -> shieldPaint
                PowerUpType.DOUBLE_JUMP -> doubleJumpPaint
                PowerUpType.ANTI_GRAVITY -> antiGravityPaint
                PowerUpType.MAGNET -> magnetPaint
                PowerUpType.ENERGY_GEM -> energyGemPaint
                PowerUpType.MULTIPLIER -> multiplierPaint
                PowerUpType.LEGENDARY_STAR -> legendaryPaint
            }
            
            canvas.save()
            canvas.translate(p.x + p.size / 2, drawY + p.size / 2)
            
            if (p.type == PowerUpType.LEGENDARY_STAR) {
                // pulsing massive rainbow star
                val scale = 1.0f + (kotlin.math.sin(frameOffset) * 0.3f)
                canvas.scale(scale, scale)
                canvas.rotate(frameOffset * 10f)
                legendaryPaint.color = Color.HSVToColor(floatArrayOf((frameOffset * 50) % 360f, 1f, 1f))
            } else if (p.type == PowerUpType.MULTIPLIER) {
                val scale = 1.0f + (kotlin.math.sin(frameOffset * 2f).toFloat() * 0.15f)
                canvas.scale(scale, scale)
                canvas.rotate(45f + kotlin.math.sin(frameOffset)*10f)
            } else {
                canvas.rotate(45f)
            }
            
            canvas.drawRect(-p.size / 2, -p.size / 2, p.size / 2, p.size / 2, paint)
            canvas.restore()
        }
    }
    
    fun reset() {
        powerUps.clear()
        lastSpawnY = 0f
    }
}
