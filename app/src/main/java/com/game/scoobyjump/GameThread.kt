package com.game.scoobyjump

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(private val surfaceHolder: SurfaceHolder, private val gameView: GameView) : Thread() {
    private var running = false
    private var suspended = false
    private val syncObject = Object()
    
    private val targetFPS = 60
    private val targetTime = (1000 / targetFPS).toLong()

    fun setRunning(isRunning: Boolean) {
        running = isRunning
    }

    fun suspendMainLoop() {
        suspended = true
    }

    fun resumeMainLoop() {
        suspended = false
        synchronized(syncObject) {
            syncObject.notifyAll()
        }
    }

    override fun run() {
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long

        while (running) {
            if (suspended) {
                synchronized(syncObject) {
                    try { syncObject.wait(100) } catch (e: Exception) {}
                }
                continue
            }
            
            startTime = System.nanoTime()
            var canvas: Canvas? = null

            try {
                canvas = surfaceHolder.lockCanvas()
                synchronized(surfaceHolder) {
                    gameView.update()
                    if (canvas != null) {
                        gameView.draw(canvas)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            timeMillis = (System.nanoTime() - startTime) / 1000000
            waitTime = targetTime - timeMillis

            if (waitTime > 0) {
                try {
                    sleep(waitTime)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
