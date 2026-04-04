package com.game.scoobyjump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class BubbleTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val strokePaint = Paint()
    private val gradientPaint = Paint()
    private val shadowPaint = Paint()
    private val highlightPaint = Paint()

    private var gradientShader: LinearGradient? = null

    init {
        // We will do all the drawing manually, so remove native shadow
        setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            // Gradient: #FFEB3B (Top) to #FB8C00 (Bottom)
            gradientShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#FFEB3B"),
                Color.parseColor("#FB8C00"),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val strokeWidthPx = 3 * resources.displayMetrics.density

        // Setup base paint attributes matching current TextView state
        fun syncPaint(p: Paint) {
            p.typeface = typeface
            p.textSize = textSize
            p.isAntiAlias = true
            p.textAlign = Paint.Align.LEFT
        }

        // 1. Drop Shadow: standard bottom-right shadow with 0.5 alpha black
        syncPaint(shadowPaint)
        shadowPaint.style = Paint.Style.FILL
        shadowPaint.color = Color.parseColor("#80000000") // 0.5 alpha black
        shadowPaint.setShadowLayer(4f, 6f, 8f, Color.parseColor("#80000000"))
        
        // Use standard text drawing coordinates
        val x = paddingLeft.toFloat()
        val y = paddingTop + baseline.toFloat()
        
        canvas.drawText(text.toString(), x, y, shadowPaint)

        // 2. Outline: 3dp thick stroke using color #4A148C
        syncPaint(strokePaint)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeWidthPx * 2 // Stroke expands from center, so 2x to get 3dp outside
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = Color.parseColor("#4A148C")
        
        canvas.drawText(text.toString(), x, y, strokePaint)

        // 3. Inner Glow / Top Highlight: White shadow with negative Y offset
        syncPaint(highlightPaint)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.WHITE
        // We use a slight negative Y offset to create a "top-down" highlight. 
        // A shadow layer is drawn BEFORE the text. So if the text is transparent, we only see the shadow? No.
        // Easiest is to draw white text slightly shifted UP, then draw the main gradient text over it slightly shifted DOWN.
        // The prompt asks for a "shadow layer with a very small radius (1dp) and a slight negative Y-offset".
        highlightPaint.setShadowLayer(displayMetricsToPx(1f), 0f, displayMetricsToPx(-3f), Color.WHITE)
        canvas.drawText(text.toString(), x, y, highlightPaint)

        // 4. Fill Gradient 
        syncPaint(gradientPaint)
        gradientPaint.style = Paint.Style.FILL
        gradientPaint.shader = gradientShader
        canvas.drawText(text.toString(), x, y, gradientPaint)
    }

    private fun displayMetricsToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
