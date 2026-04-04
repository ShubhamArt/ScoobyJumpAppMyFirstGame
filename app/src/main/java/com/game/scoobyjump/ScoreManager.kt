package com.game.scoobyjump

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class ScoreManager {
    private var baseScore = 0
    private var bonusScore = 0
    var multiplier = 1

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }

    fun updateCameraScore(cameraY: Float) {
        val currentHeightScore = (-cameraY / 10).toInt()
        val diff = currentHeightScore - baseScore
        if (diff > 0) {
            baseScore = currentHeightScore
            bonusScore += diff * (multiplier - 1)
        }
    }

    fun addBonus(points: Int) {
        bonusScore += (points * multiplier)
    }

    fun getTotalScore(): Int {
        return baseScore + bonusScore
    }

    fun draw(canvas: Canvas) {
        canvas.drawText("Score: \${getTotalScore()}", 50f, 100f, textPaint)
    }
    
    fun reset() {
        baseScore = 0
        bonusScore = 0
        multiplier = 1
    }
}
