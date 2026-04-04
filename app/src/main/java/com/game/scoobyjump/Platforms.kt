package com.game.scoobyjump

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

abstract class BasePlatform(
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    val drawable: android.graphics.drawable.Drawable? = null
) {
    var isBroken = false // Used for cleaning up breakable platforms
    var hasCoin = false
    var isPossessed = false
    val coinRadius = 15f

    abstract fun update()
    abstract fun draw(canvas: Canvas, cameraY: Float)
    abstract fun onLandedOn(): Float // Returns jump force multiplier

    fun drawCoin(canvas: Canvas, cameraY: Float) {
        if (hasCoin) {
            val drawY = y - cameraY
            val coinPaint = Paint().apply { color = Color.parseColor("#FFD700"); isAntiAlias = true }
            val outlinePaint = Paint().apply { 
                color = Color.parseColor("#F57F17") 
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true 
            }
            val cx = x + width / 2
            val cy = drawY - coinRadius - 10f
            canvas.drawCircle(cx, cy, coinRadius, coinPaint)
            canvas.drawCircle(cx, cy, coinRadius, outlinePaint)
        }
    }

    fun drawPossessedAura(canvas: Canvas, cameraY: Float) {
        if (isPossessed && !isBroken) {
            val drawY = y - cameraY
            val auraPaint = Paint().apply {
                color = Color.parseColor("#E040FB")
                style = Paint.Style.STROKE
                strokeWidth = 6f
                alpha = 150 + (kotlin.math.sin(System.currentTimeMillis() / 150.0) * 50).toInt().coerceIn(0, 100)
                isAntiAlias = true
                setShadowLayer(10f, 0f, 0f, Color.parseColor("#E040FB"))
            }
            canvas.drawRoundRect(x - 4f, drawY - 4f, x + width + 4f, drawY + height + 4f, 20f, 20f, auraPaint)
        }
    }
}

class StaticPlatform(x: Float, y: Float, width: Float, height: Float, drawable: android.graphics.drawable.Drawable?) :
    BasePlatform(x, y, width, height, drawable) {

    override fun update() {}

    override fun draw(canvas: Canvas, cameraY: Float) {
        if (!isBroken) {
            val drawY = y - cameraY
            
            drawable?.let {
                it.setBounds(x.toInt(), drawY.toInt(), (x + width).toInt(), (drawY + height).toInt())
                it.draw(canvas)
            } ?: run {
                // Fallback if drawable fails to load
                val paint = Paint().apply { color = Color.parseColor("#4CAF50") }
                canvas.drawRoundRect(x, drawY, x + width, drawY + height, 16f, 16f, paint)
            }
            drawPossessedAura(canvas, cameraY)
            drawCoin(canvas, cameraY)
        }
    }

    override fun onLandedOn(): Float = 1.0f
}

class MovingPlatform(x: Float, y: Float, width: Float, height: Float, private val screenWidth: Float, drawable: android.graphics.drawable.Drawable?) :
    BasePlatform(x, y, width, height, drawable) {
    
    var moveSpeed = 5f
    private var movingRight = true

    override fun update() {
        if (movingRight) {
            x += moveSpeed
            if (x + width > screenWidth) {
                x = screenWidth - width
                movingRight = false
            }
        } else {
            x -= moveSpeed
            if (x < 0) {
                x = 0f
                movingRight = true
            }
        }
    }

    override fun draw(canvas: Canvas, cameraY: Float) {
        if (!isBroken) {
            val drawY = y - cameraY
            drawable?.let {
                it.setBounds(x.toInt(), drawY.toInt(), (x + width).toInt(), (drawY + height).toInt())
                it.draw(canvas)
            } ?: run {
                val paint = Paint().apply { color = Color.parseColor("#2196F3") }
                canvas.drawRoundRect(x, drawY, x + width, drawY + height, 16f, 16f, paint)
            }
            drawPossessedAura(canvas, cameraY)
            drawCoin(canvas, cameraY)
        }
    }

    override fun onLandedOn(): Float = 1.0f
}

class BreakablePlatform(x: Float, y: Float, width: Float, height: Float, drawable: android.graphics.drawable.Drawable?) :
    BasePlatform(x, y, width, height, drawable) {

    private var breakTimer = -1
    private val framesToBreak = 15 // 0.25 seconds at 60fps

    override fun update() {
        if (breakTimer > 0) {
            breakTimer--
        } else if (breakTimer == 0) {
            isBroken = true
            breakTimer = -1
        }
    }

    override fun draw(canvas: Canvas, cameraY: Float) {
        if (!isBroken) {
            val drawY = y - cameraY
            // Give it a slightly cracked look via alpha if breaking
            drawable?.let {
                if (breakTimer > 0) {
                    it.alpha = 150
                } else {
                    it.alpha = 255
                }
                it.setBounds(x.toInt(), drawY.toInt(), (x + width).toInt(), (drawY + height).toInt())
                it.draw(canvas)
            } ?: run {
                val paint = Paint().apply { 
                    color = Color.parseColor("#795548")
                    alpha = if (breakTimer > 0) 150 else 255
                }
                canvas.drawRoundRect(x, drawY, x + width, drawY + height, 16f, 16f, paint)
            }
            drawPossessedAura(canvas, cameraY)
            drawCoin(canvas, cameraY)
        }
    }

    override fun onLandedOn(): Float {
        // Only trigger once
        if (breakTimer == -1 && !isBroken) {
            breakTimer = framesToBreak
        }
        return 1.0f
    }
}

class SpringPlatform(x: Float, y: Float, width: Float, height: Float, drawable: android.graphics.drawable.Drawable?) :
    BasePlatform(x, y, width, height, drawable) {

    private val springPaint = Paint().apply {
        color = Color.parseColor("#E65100") // Deep Orange
    }

    override fun update() {}

    override fun draw(canvas: Canvas, cameraY: Float) {
        if (!isBroken) {
            val drawY = y - cameraY
            drawable?.let {
                it.setBounds(x.toInt(), drawY.toInt(), (x + width).toInt(), (drawY + height).toInt())
                it.draw(canvas)
            } ?: run {
                val paint = Paint().apply { color = Color.parseColor("#FFC107") }
                canvas.drawRoundRect(x, drawY, x + width, drawY + height, 16f, 16f, paint)
            }
            // draw a small spring coil visual in the center
            canvas.drawRect(x + width/2 - 10f, drawY - 10f, x + width/2 + 10f, drawY, springPaint)
            drawPossessedAura(canvas, cameraY)
            drawCoin(canvas, cameraY)
        }
    }

    override fun onLandedOn(): Float = 1.8f // significant boost
}



