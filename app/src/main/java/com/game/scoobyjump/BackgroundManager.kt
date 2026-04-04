package com.game.scoobyjump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.random.Random

enum class Climate { MIDNIGHT_NEON, SUNSET_HORIZON, ARCTIC_AURORA, DEEP_SPACE }

class BackgroundManager(private val screenWidth: Float, private val height: Float, context: Context) {
    var currentClimate = Climate.values().random()

    fun updateClimateBasedOnScore(score: Int) {
        val newClimate = when {
            score >= 8000 -> Climate.DEEP_SPACE
            score >= 5000 -> Climate.ARCTIC_AURORA
            score >= 2000 -> Climate.SUNSET_HORIZON
            else -> Climate.MIDNIGHT_NEON
        }
        
        if (currentClimate != newClimate) {
            currentClimate = newClimate
        }
    }

    fun randomizeClimate() {
        currentClimate = Climate.values().random()
    }

    private val stars = List(150) {
        Triple(Random.nextFloat() * screenWidth, Random.nextFloat() * height, Random.nextFloat())
    }

    private val starPaint = Paint().apply { 
        color = Color.WHITE
        isAntiAlias = true 
    }
    
    private val auroraPaint = Paint().apply {
        color = Color.parseColor("#4000FF99") // Semi-transparent green
        isAntiAlias = true
        style = Paint.Style.FILL
        maskFilter = android.graphics.BlurMaskFilter(50f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val auroraPath = Path()

    private val motePaints = Array(3) { i ->
        Paint().apply {
            color = Color.WHITE // Default, will be updated per climate
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter((i + 1) * 4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    private data class Mote(
        var x: Float, 
        var y: Float, 
        var speedY: Float, 
        var speedX: Float, 
        var size: Float, 
        var baseAlpha: Int,
        var paintIndex: Int,
        var parallaxFactor: Float
    )

    private val motes = Array(150) {
        val depthTier = Random.nextInt(0, 3)
        Mote(
            x = Random.nextFloat() * screenWidth,
            y = Random.nextFloat() * height,
            speedY = Random.nextFloat() * (depthTier + 1) * 1.5f + 0.5f,
            speedX = Random.nextFloat() * 1f - 0.5f,
            size = (depthTier + 1) * 4f + Random.nextFloat() * 4f,
            baseAlpha = Random.nextInt(40, 140),
            paintIndex = depthTier,
            parallaxFactor = (depthTier + 1) * 0.3f
        )
    }

    private var time = 0f

    fun draw(canvas: Canvas, cameraY: Float) {
        time += 0.01f
        val depth = -cameraY

        when (currentClimate) {
            Climate.MIDNIGHT_NEON -> {
                val bgShader = android.graphics.LinearGradient(
                    0f, 0f, screenWidth, height,
                    Color.parseColor("#0D0628"), // Deep Black
                    Color.parseColor("#1A0B2E"), // Midnight Purple
                    android.graphics.Shader.TileMode.CLAMP
                )
                val bgPaint = Paint().apply { this.shader = bgShader }
                canvas.drawRect(0f, 0f, screenWidth, height, bgPaint)
                updateMoteColor(Color.CYAN)
                drawMotes(canvas, cameraY)
            }
            Climate.SUNSET_HORIZON -> {
                // Warm Purple to Orange Gradient
                val shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, height,
                    Color.parseColor("#800080"), Color.parseColor("#FF4500"),
                    android.graphics.Shader.TileMode.CLAMP
                )
                val paint = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, screenWidth, height, paint)
                
                updateMoteColor(Color.parseColor("#FF8C00")) // Embers
                drawMotes(canvas, cameraY)
            }
            Climate.ARCTIC_AURORA -> {
                canvas.drawColor(Color.parseColor("#0B3D3D")) // Dark Teal
                drawAurora(canvas, cameraY)
                updateMoteColor(Color.parseColor("#B2FFD8"))
                drawMotes(canvas, cameraY)
            }
            Climate.DEEP_SPACE -> {
                canvas.drawColor(Color.parseColor("#050510")) // Pitch Black
                drawStars(canvas, 1f, cameraY)
                updateMoteColor(Color.parseColor("#A050FF")) // Nebula motes
                drawMotes(canvas, cameraY)
            }
        }
    }

    private fun updateMoteColor(color: Int) {
        for (paint in motePaints) {
            paint.color = color
        }
    }

    private fun drawAurora(canvas: Canvas, cameraY: Float) {
        auroraPath.reset()
        val parallaxOffset = cameraY * 0.1f
        // Dynamic sine waves for aurora
        auroraPath.moveTo(0f, height * 0.5f)
        for (i in 0..screenWidth.toInt() step 50) {
            val yOffset = Math.sin((i * 0.01f + time).toDouble()) * 100f
            val yOffset2 = Math.cos((i * 0.005f - time * 0.5f).toDouble()) * 50f
            auroraPath.lineTo(i.toFloat(), height * 0.3f + yOffset.toFloat() + yOffset2.toFloat() - parallaxOffset % height)
        }
        auroraPath.lineTo(screenWidth, height)
        auroraPath.lineTo(0f, height)
        auroraPath.close()
        canvas.drawPath(auroraPath, auroraPaint)
    }

    private fun drawStars(canvas: Canvas, alphaRatio: Float, cameraY: Float) {
        val baseAlpha = (255 * alphaRatio).toInt()
        
        for (star in stars) {
            val parallaxFactor = 0.02f + (star.third * 0.08f) // Extremely deep parallax
            var sy = (star.second - cameraY * parallaxFactor) % height
            if (sy < 0) sy += height
            
            val twinkle = if (Random.nextFloat() > 0.98f) 0 else baseAlpha
            starPaint.alpha = twinkle
            
            val radius = 2f + (star.third * 3f) // Closer stars are bigger
            canvas.drawCircle(star.first, sy, radius, starPaint)
        }
        starPaint.alpha = baseAlpha
    }

    private fun drawMotes(canvas: Canvas, cameraY: Float) {
        for (mote in motes) {
            val parallaxOffset = cameraY * mote.parallaxFactor
            
            mote.y -= mote.speedY
            mote.x += mote.speedX

            var drawY = (mote.y - parallaxOffset) % height
            if (drawY < 0) drawY += height
            
            var drawX = mote.x % screenWidth
            if (drawX < 0) drawX += screenWidth

            val p = motePaints[mote.paintIndex]
            val originalColor = p.color
            // Keep alpha specific to the mote
            p.alpha = mote.baseAlpha
            canvas.drawCircle(drawX, drawY, mote.size, p)
        }
    }


}
