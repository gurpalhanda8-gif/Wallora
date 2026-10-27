package com.lockstyle.app
import android.hardware.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService

class ClockWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()
    inner class E : Engine() {
        private val h = Handler(Looper.getMainLooper())
        private val sm by lazy { getSystemService(SensorManager::class.java) }
        private var last = 0L
        private val ls = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val sp = Prefs.sp(applicationContext)
                if (e.sensor.type == Sensor.TYPE_STEP_COUNTER) { Info.saveSteps(sp, e.values[0]); return }
                Renderer.tx += ((-e.values[0] / 5f).coerceIn(-1f, 1f) - Renderer.tx) * .12f
                Renderer.ty += (((e.values[1] - 7f) / 5f).coerceIn(-1f, 1f) - Renderer.ty) * .12f
                val n = System.currentTimeMillis()
                if (n - last > 40) { last = n; draw() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        private fun reg() {
            val sp = Prefs.sp(applicationContext)
            if (sp.getInt("para", 0) == 1) sm.registerListener(ls, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
            if (sp.getInt("wstep", 0) == 1 || (0..3).any { sp.getInt("wt$it", 0) == 2 }) sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sm.registerListener(ls, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        private val tick = object : Runnable { override fun run() { draw(); h.postDelayed(this, if (Prefs.sp(applicationContext).getInt("anim", 0) == 1) 40L else 15000L) } }
        override fun onVisibilityChanged(v: Boolean) {
            h.removeCallbacks(tick); sm.unregisterListener(ls)
            if (v) { reg()
                val sp = Prefs.sp(applicationContext)
                if (sp.getInt("shuffle", 0) == 1) sp.edit().putInt("idx", sp.getInt("idx", 0) + 1).apply()
                tick.run()
            }
        }
        override fun onSurfaceChanged(hd: android.view.SurfaceHolder, f: Int, w: Int, ht: Int) = draw()
        override fun onDestroy() { h.removeCallbacks(tick); sm.unregisterListener(ls) }
        private fun draw() {
            val hd = surfaceHolder
            val c = try { hd.lockCanvas() } catch (e: Exception) { null } ?: return
            try { Renderer.draw(c, c.width, c.height, applicationContext) } finally { hd.unlockCanvasAndPost(c) }
        }
    }
}
