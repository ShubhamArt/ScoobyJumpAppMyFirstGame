package com.game.scoobyjump

import kotlin.random.Random

class CameraController(private val screenHeight: Float) {
    var cameraY = 0f
        private set
        
    private var shakeDuration = 0
    private var shakeIntensity = 0f

    fun update(playerY: Float) {
        val targetY = playerY - screenHeight * 0.4f
        
        // Keep camera moving strictly upward
        if (targetY < cameraY) {
            cameraY += (targetY - cameraY) * 0.1f 
        }
        
        // Apply jitter for screen shake effect (if triggered)
        if (shakeDuration > 0) {
            val offsetY = (Random.nextFloat() - 0.5f) * shakeIntensity
            cameraY += offsetY
            shakeDuration--
        }
    }
    
    fun triggerShake(intensity: Float, duration: Int) {
        shakeIntensity = intensity
        shakeDuration = duration
    }
}
