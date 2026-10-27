package com.lockstyle.app
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class LockService : Service() {
    private val r = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Settings.canDrawOverlays(c)) {
                // Android may block activity launches from background receivers.
                // Never let that platform restriction crash the foreground service.
                try {
                    c.startActivity(Intent(c, LockActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    ))
                } catch (_: RuntimeException) {
                    // User can still open the lock-screen preview from the app.
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lock", "LockStyle", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "lock").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("LockStyle is active").build())
        registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onDestroy() { try { unregisterReceiver(r) } catch (e: Exception) {}; super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs.sp(c).getInt("lockOn", 0) == 1) c.startForegroundService(Intent(c, LockService::class.java))
    }
}

class LockActivity : Activity() {
    private lateinit var v: View
    private val h = Handler(Looper.getMainLooper())
    private val rt = object : Runnable {
        override fun run() { v.invalidate(); h.postDelayed(this, if (Prefs.sp(this@LockActivity).getInt("anim", 0) == 1) 40L else 5000L) }
    }
    private val gone = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { finish() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 190; textAlign = Paint.Align.CENTER }
        var y0 = 0f
        v = object : View(this) {
            override fun onDraw(c: Canvas) {
                Renderer.draw(c, width, height, context)
                p.textSize = width * 0.036f
                c.drawText("Swipe up to unlock", width / 2f, height * 0.945f, p)
                p.strokeWidth = 8f; c.drawLine(width * .38f, height * .97f, width * .62f, height * .97f, p)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> y0 = e.y
                    MotionEvent.ACTION_UP -> if (y0 - e.y > height * 0.12f) unlock()
                }
                return true
            }
        }
        setContentView(v)
        registerReceiver(gone, IntentFilter(Intent.ACTION_USER_PRESENT))
    }
    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finish() }
        }) else finish()
    }
    override fun onResume() { super.onResume(); h.post(rt) }
    override fun onPause() { h.removeCallbacks(rt); super.onPause() }
    override fun onDestroy() { try { unregisterReceiver(gone) } catch (e: Exception) {}; super.onDestroy() }
    @Deprecated("no back") override fun onBackPressed() {}
}
