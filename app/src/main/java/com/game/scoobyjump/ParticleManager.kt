package com.game.scoobyjump

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

class ParticleManager {
    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 0f
        var color = 0
    }
    
    private val POOL_SIZE = 150
    private val particles = Array(POOL_SIZE) { Particle() }
    private val paint = Paint().apply { isAntiAlias = true }

    private fun getFreeParticle(): Particle? {
        for (i in 0 until POOL_SIZE) {
            if (!particles[i].active) return particles[i]
        }
        return null
    }

    fun spawnDust(x: Float, bottomY: Float, count: Int = 6) {
        for (i in 0 until count) {
            val p = getFreeParticle() ?: break
            p.active = true
            p.x = x
            p.y = bottomY
            p.vx = (Random.nextFloat() - 0.5f) * 10f
            p.vy = -Random.nextFloat() * 5f
            p.life = 15f
            p.maxLife = 15f
            p.color = Color.parseColor("#B0BEC5")
        }
    }

    fun spawnWarningSpark(x: Float, y: Float) {
        val p = getFreeParticle() ?: return
        p.active = true
        p.x = x
        p.y = y
        p.vx = (Random.nextFloat() - 0.5f) * 4f
        p.vy = -Random.nextFloat() * 12f - 4f
        p.life = 15f
        p.maxLife = 15f
        p.color = Color.parseColor("#FF1744")
    }

    fun spawnExplosion(x: Float, y: Float, color: Int, count: Int = 15) {
        for (i in 0 until count) {
            val p = getFreeParticle() ?: break
            p.active = true
            p.x = x
            p.y = y
            val angle = Random.nextFloat() * Math.PI * 2
            val speed = Random.nextFloat() * 12f + 5f
            p.vx = (Math.cos(angle) * speed).toFloat()
            p.vy = (Math.sin(angle) * speed).toFloat()
            p.life = 25f
            p.maxLife = 25f
            p.color = color
        }
    }

    fun spawnDashTrail(x: Float, y: Float, color: Int) {
        val p = getFreeParticle() ?: return
        p.active = true
        p.x = x
        p.y = y
        p.vx = 0f
        p.vy = 0f
        p.life = 15f
        p.maxLife = 15f
        p.color = color
    }
    
    fun spawnStompImpact(x: Float, y: Float) {
        for (i in 0 until 30) {
            val p = getFreeParticle() ?: break
            p.active = true
            p.x = x
            p.y = y
            val angle = Random.nextFloat() * Math.PI
            val speed = Random.nextFloat() * 20f + 10f
            p.vx = (Math.cos(angle) * speed).toFloat()
            p.vy = -(Math.sin(angle) * speed).toFloat()
            p.life = 30f
            p.maxLife = 30f
            p.color = Color.parseColor("#FFD54F")
        }
    }

    fun clearAll() {
        for (i in 0 until POOL_SIZE) {
            particles[i].active = false
        }
    }

    fun update() {
        for (i in 0 until POOL_SIZE) {
            val p = particles[i]
            if (p.active) {
                p.x += p.vx
                p.y += p.vy
                p.life -= 1f
                if (p.life <= 0) {
                    p.active = false
                }
            }
        }
    }

    fun draw(canvas: Canvas, cameraY: Float) {
        for (i in 0 until POOL_SIZE) {
            val p = particles[i]
            if (p.active) {
                paint.color = p.color
                paint.alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
                val drawY = p.y - cameraY
                canvas.drawRect(p.x - 3f, drawY - 3f, p.x + 3f, drawY + 3f, paint)
            }
        }
    }
}
