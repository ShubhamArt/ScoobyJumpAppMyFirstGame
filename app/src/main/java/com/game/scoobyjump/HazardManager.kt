package com.game.scoobyjump

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

enum class HazardType { SPIKES }
data class Hazard(val type: HazardType, val x: Float, val y: Float, val radius: Float)

class HazardManager(private val screenWidth: Float, private val screenHeight: Float) {
    private val hazards = mutableListOf<Hazard>()
    private var lastSpawnY = 0f

    private val spikePaint = Paint().apply { color = Color.parseColor("#FF5252") }

    fun update(cameraY: Float, difficulty: Float) {
        val spawnThreshold = cameraY - screenHeight * 1.5f
        while (lastSpawnY > spawnThreshold) {
            if (Random.nextFloat() < (0.2f * difficulty)) {
                val type = HazardType.SPIKES
                val radius = 30f
                val newX = Random.nextFloat() * (screenWidth - radius * 2) + radius
                val newY = lastSpawnY - Random.nextFloat() * 500f
                hazards.add(Hazard(type, newX, newY, radius))
            }
            lastSpawnY -= 1000f
        }

        hazards.removeAll { it.y > cameraY + screenHeight + 200f }
    }

    fun checkCollision(player: Player): Boolean {
        if (player.isShieldActive) return false

        for (h in hazards) {
            val px = player.x + player.width / 2
            val py = player.y + player.height / 2
            
            val dx = px - h.x
            val dy = py - h.y
            val distSq = dx*dx + dy*dy
            
            val hitRadius = h.radius + 20f
            if (distSq < hitRadius * hitRadius) return true
        }
        return false
    }

    fun draw(canvas: Canvas, cameraY: Float) {
        for (h in hazards) {
            val drawY = h.y - cameraY
            val path = android.graphics.Path()
            path.moveTo(h.x, drawY - h.radius)
            path.lineTo(h.x - h.radius, drawY + h.radius)
            path.lineTo(h.x + h.radius, drawY + h.radius)
            path.close()
            canvas.drawPath(path, spikePaint)
        }
    }
    
    fun reset() {
        hazards.clear()
        lastSpawnY = 0f
    }
}
