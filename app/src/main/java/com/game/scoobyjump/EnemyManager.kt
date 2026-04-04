package com.game.scoobyjump

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.random.Random
enum class EnemyType { STATIC_CLOUD }
data class Enemy(
    var x: Float, 
    val y: Float, 
    val width: Float, 
    val height: Float, 
    var movingRight: Boolean, 
    val speed: Float,
    val type: EnemyType,
    var rotation: Float = 0f,
    var tick: Float = 0f,
    var nearMissTriggered: Boolean = false
)

class EnemyManager(private val screenWidth: Float, private val screenHeight: Float) {
    private val enemies = mutableListOf<Enemy>()
    private var lastSpawnY = 0f

    // Visual Paints
    private val staticCloudPaint = Paint().apply {
        color = Color.parseColor("#FF1744") // Bright Red
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.RED)
    }

    fun update(cameraY: Float, difficulty: Float, score: Int) {
        // Spawn
        val spawnThreshold = cameraY - screenHeight * 1.5f
        while (lastSpawnY > spawnThreshold) {
            if (Random.nextFloat() < (0.15f * difficulty)) { // Only spawn when difficulty > 0
                val width = 60f
                val height = 60f
                val startX = Random.nextFloat() * (screenWidth - width)
                val newY = lastSpawnY - Random.nextFloat() * 400f
                val speed = 3f + (difficulty * 5f)
                
                val type = EnemyType.STATIC_CLOUD

                enemies.add(Enemy(startX, newY, width, height, Random.nextBoolean(), speed, type))
            }
            lastSpawnY -= 800f // Try spawning every 800 units
        }

        // Move and Animate
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.tick += 0.1f

            if (e.movingRight) {
                e.x += e.speed
                if (e.x + e.width > screenWidth) e.movingRight = false
            } else {
                e.x -= e.speed
                if (e.x < 0) e.movingRight = true
            }

            // Animation
            e.rotation = (Math.sin(e.tick.toDouble()) * 10).toFloat() // Shiver

            // Cleanup
            if (e.y > cameraY + screenHeight + 200f) {
                iterator.remove()
            }
        }
    }

    fun getCollidingEnemy(player: Player): Enemy? {
        if (player.isShieldActive || player.isGhostSprintActive) return null

        val margin = 10f
        for (e in enemies) {
            if (player.x + margin < e.x + e.width && player.x + player.width - margin > e.x &&
                player.y + margin < e.y + e.height && player.y + player.height - margin > e.y) {
                return e
            }
        }
        return null
    }

    fun removeEnemy(enemy: Enemy) {
        enemies.remove(enemy)
    }

    fun draw(canvas: Canvas, cameraY: Float) {
        for (e in enemies) {
            val drawY = e.y - cameraY
            val centerX = e.x + e.width / 2
            val centerY = drawY + e.height / 2

            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.rotate(e.rotation)

            // Since STATIC_CLOUD is the only remaining type:
            val path = Path()
            val r = e.width / 2
            // Draw a jagged polygon
            path.moveTo(0f, -r)
            path.lineTo(r * 0.8f, -r * 0.3f)
            path.lineTo(r, r * 0.2f)
            path.lineTo(r * 0.5f, r)
            path.lineTo(-r * 0.2f, r * 0.8f)
            path.lineTo(-r, r * 0.1f)
            path.lineTo(-r * 0.6f, -r * 0.5f)
            path.close()
            // Apply red pulse
            val glow = 15f + Math.sin(e.tick.toDouble() * 3).toFloat() * 15f
            if (glow > 0) staticCloudPaint.setShadowLayer(glow, 0f, 0f, Color.RED)
            
            canvas.drawPath(path, staticCloudPaint)
            
            // Inner lightning
            if (e.tick % 2 < 1) {
                 canvas.drawLine(-r/2, -r/2, r/2, r/2, staticCloudPaint)
                 canvas.drawLine(r/2, -r/2, -r/2, r/2, staticCloudPaint)
            }
            canvas.restore()
        }
    }
    
    fun getEnemies(): List<Enemy> = enemies
    
    fun reset() {
        enemies.clear()
        lastSpawnY = 0f
    }
}
