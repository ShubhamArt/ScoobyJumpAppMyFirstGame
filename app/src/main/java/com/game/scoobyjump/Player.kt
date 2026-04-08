package com.game.scoobyjump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class Player(
    private val screenWidth: Float, 
    private val screenHeight: Float, 
    context: Context, 
    private val skinColor: String = "#FF9800"
) {
    var x = screenWidth / 2f - 40f
    var y = screenHeight - 300f
    
    val width = 80f
    val height = 80f

    var velocityX = 0f
    var velocityY = 0f
    
    var gravity = 1.0f
    var jumpForceMultiplier = 1.0f
    var glowIntensity = 0f
    
    private val maxFallSpeed = 30f
    private val moveSpeed = 16f
    
    // Animation states
    private var scaleX = 1.0f
    private var scaleY = 1.0f

    // Powerup states
    var isShieldActive = false
    private var shieldTimer = 0
    var hasDoubleJump = false
    var jetpackActive = false
    private var jetpackTimer = 0
    
    var isAntiGravityActive = false
    private var antiGravityTimer = 0
    private var floatAngle = 0f
    
    var isGhostSprintActive = false
    private var ghostTimer = 0
    
    var isStomping = false
    
    var isMagnetActive = false
    private var magnetTimer = 0
    
    var isMultiplierActive = false
    private var multiplierTimer = 0
    
    private val positionHistory = java.util.LinkedList<android.graphics.PointF>()
    
    private val playerDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.bg_player_preview)?.mutate()?.apply {
        // Set tint exactly to the player's skin color!
        DrawableCompat.setTintMode(this, android.graphics.PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTint(this, Color.parseColor(skinColor))
    }
    
    private val shadowPaint = Paint().apply {
        color = Color.parseColor("#40000000") // 25% Black
        isAntiAlias = true
    }
    
    private val eyeWhitePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    
    private val eyePupilPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
    }

    fun update() {

        if (isGhostSprintActive) {
            velocityY = -45f
            ghostTimer--
            if (ghostTimer <= 0) isGhostSprintActive = false
        } else if (jetpackActive) {
            velocityY = -35f
            jetpackTimer--
            if (jetpackTimer <= 0) jetpackActive = false
        } else {
            if (isAntiGravityActive) {
                antiGravityTimer--
                if (antiGravityTimer <= 0) {
                    gravity += 0.02f
                    if (gravity >= 1.0f) {
                        gravity = 1.0f
                        isAntiGravityActive = false
                    }
                }
                floatAngle += 0.1f
                x += kotlin.math.sin(floatAngle) * 3f
            }
            if (isStomping) {
                velocityY = 60f // Radical downward spike
            } else {
                velocityY += gravity
                // Velocity Limit constraint
                if (velocityY < -45f) {
                    velocityY = -45f
                }
                
                if (velocityY > maxFallSpeed) velocityY = maxFallSpeed
            }
        }

        y += velocityY
        x += velocityX

        if (x < -width) x = screenWidth
        if (x > screenWidth) x = -width

        if (isShieldActive) {
            shieldTimer--
            if (shieldTimer <= 0) isShieldActive = false
        }
        
        // Recover scale back to 1.0
        scaleX += (1.0f - scaleX) * 0.15f
        scaleY += (1.0f - scaleY) * 0.15f
        
        if (isMagnetActive) {
            magnetTimer--
            if (magnetTimer <= 0) isMagnetActive = false
        }
        
        if (isMultiplierActive) {
            multiplierTimer--
            if (multiplierTimer <= 0) isMultiplierActive = false
        }
        
        if (isGhostSprintActive || isMultiplierActive) {
            positionHistory.add(0, android.graphics.PointF(x, y))
            if (positionHistory.size > 5) {
                positionHistory.removeLast()
            }
        } else {
            if (positionHistory.isNotEmpty()) {
                positionHistory.removeLast()
            }
        }
    }

    fun jump(forceMultiplier: Float = 1f) {
        velocityY = -30f * forceMultiplier * jumpForceMultiplier
        isStomping = false // Reset stomp on jump
        // Trigger squash and stretch animation
        scaleX = 0.7f
        scaleY = 1.3f
    }

    fun draw(canvas: Canvas, cameraY: Float) {
        val drawY = y - cameraY
        
        // Ghost / Multiplier Trails Drawer
        if (positionHistory.isNotEmpty()) {
            val isGold = isMultiplierActive && !isGhostSprintActive
            
            playerDrawable?.let {
                val originalAlpha = it.alpha
                for ((index, pos) in positionHistory.withIndex()) {
                    val trailY = pos.y - cameraY
                    it.alpha = (100 - (index * 20)).coerceIn(0, 255)
                    if (isGold) {
                        DrawableCompat.setTint(it, Color.parseColor("#FFD700"))
                    }
                    it.setBounds(pos.x.toInt(), trailY.toInt(), (pos.x + width).toInt(), (trailY + height).toInt())
                    it.draw(canvas)
                }
                it.alpha = originalAlpha
                // Restore original skin color
                DrawableCompat.setTint(it, Color.parseColor(skinColor))
            } ?: run {
                for ((index, pos) in positionHistory.withIndex()) {
                    val trailY = pos.y - cameraY
                    val trailPaint = Paint().apply { 
                        color = if (isGold) Color.parseColor("#FFD700") else Color.parseColor(skinColor)
                        alpha = (100 - (index * 20)).coerceIn(0, 255) 
                    }
                    canvas.drawRoundRect(pos.x, trailY, pos.x + width, trailY + height, 24f, 24f, trailPaint)
                }
            }
        }
        
        // Magnet Aura
        if (isMagnetActive) {
            val pulseRadius = width * 1.5f + (kotlin.math.sin(System.currentTimeMillis() / 100.0) * 15f).toFloat()
            val magnetAura = Paint().apply {
                color = Color.parseColor("#00BFFF")
                alpha = 90
                style = Paint.Style.STROKE
                strokeWidth = 6f
                setShadowLayer(15f, 0f, 0f, Color.CYAN)
            }
            canvas.drawCircle(x + width / 2, drawY + height / 2, pulseRadius, magnetAura)
        }

        // Global Drop Shadow (draw underneath everything, doesn't bounce with scale)
        canvas.drawOval(x + width * 0.1f, drawY + height - 5f, x + width * 0.9f, drawY + height + 10f, shadowPaint)

        canvas.save()
        // Anchor scale to the bottom center of the player
        canvas.scale(scaleX, scaleY, x + width / 2f, drawY + height)
        
        // 1. Draw Shield
        if (isShieldActive && !isAntiGravityActive) {
            val shieldPaint = Paint().apply { color = Color.CYAN; alpha = 100 }
            canvas.drawCircle(x + width/2, drawY + height/2, width, shieldPaint)
        }
        
        // 1.5 Draw Anti-Gravity Hover Bubble
        if (isAntiGravityActive) {
            val auraY = drawY + height/2 + kotlin.math.sin(floatAngle)*10f
            val auraPaint = Paint().apply { color = Color.parseColor("#E040FB"); alpha = 120 }
            canvas.drawCircle(x + width/2, auraY, width * 1.2f, auraPaint)
        }

        // 2. Draw Main Body (XML Drawable)
        playerDrawable?.let {
            val originalAlpha = it.alpha
            if (isGhostSprintActive) it.alpha = 120
            
            // Render HDR Glow based on tracking HighScore
            if (glowIntensity > 0f) {
                val auraPaint = Paint().apply {
                    color = Color.parseColor("#FFD700")
                    alpha = (glowIntensity * 150f).toInt().coerceIn(0, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 10f * glowIntensity
                    setShadowLayer(40f * glowIntensity, 0f, 0f, Color.parseColor("#FFEA00"))
                }
                canvas.drawRoundRect(x - 5f, drawY - 5f, x + width + 5f, drawY + height + 5f, 30f, 30f, auraPaint)
            }
            
            it.setBounds(x.toInt(), drawY.toInt(), (x + width).toInt(), (drawY + height).toInt())
            it.draw(canvas)
            it.alpha = originalAlpha
        } ?: run {
            val fallbackPaint = Paint().apply { 
                color = Color.parseColor(skinColor) 
                if (isGhostSprintActive) alpha = 120
                if (glowIntensity > 0f) {
                    setShadowLayer(50f * glowIntensity, 0f, 0f, Color.YELLOW)
                }
            }
            canvas.drawRoundRect(x, drawY, x + width, drawY + height, 24f, 24f, fallbackPaint)
        }
        
        // 3. Calculate eye tracking
        val eyeOffset = if (velocityX > 0) 5f else if (velocityX < 0) -5f else 0f
        
        // Left Eye White
        canvas.drawOval(x + 15f + eyeOffset, drawY + 20f, x + 35f + eyeOffset, drawY + 45f, eyeWhitePaint)
        // Right Eye White
        canvas.drawOval(x + 45f + eyeOffset, drawY + 20f, x + 65f + eyeOffset, drawY + 45f, eyeWhitePaint)
        
        // Pupils pointing based on trajectory
        val pupilYOffset = if (velocityY < 0) -2f else 5f
        canvas.drawCircle(x + 28f + eyeOffset * 1.5f, drawY + 35f + pupilYOffset, 6f, eyePupilPaint)
        canvas.drawCircle(x + 52f + eyeOffset * 1.5f, drawY + 35f + pupilYOffset, 6f, eyePupilPaint)
        
        canvas.restore()
    }

    fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                velocityX = if (event.x > screenWidth / 2) moveSpeed else -moveSpeed
            }
            MotionEvent.ACTION_UP -> {
                velocityX = 0f
            }
        }
    }

    fun activateJetpack(durationFrames: Int = 180) {
        jetpackActive = true
        jetpackTimer = durationFrames
        isShieldActive = true
        shieldTimer = durationFrames
    }

    fun activateShield(duration: Int = 300) {
        isShieldActive = true
        shieldTimer = duration
    }

    fun gainDoubleJump() {
        hasDoubleJump = true
    }

    fun activateAntiGravity(durationFrames: Int = 300) {
        isAntiGravityActive = true
        antiGravityTimer = durationFrames
        gravity = 0.2f
        isShieldActive = true
        shieldTimer = durationFrames + 60 // Keeps shield while reverting gravity
    }

    fun activateGhostSprint(durationFrames: Int = 600) {
        isGhostSprintActive = true
        ghostTimer = durationFrames
    }
    
    fun activateMagnet(durationFrames: Int = 600) {
        isMagnetActive = true
        magnetTimer = durationFrames
    }
    
    fun activateMultiplier(durationFrames: Int = 900) {
        isMultiplierActive = true
        multiplierTimer = durationFrames
    }

    fun activateStomp() {
        isStomping = true
        velocityY = 60f
    }
}
