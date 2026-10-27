package com.lockstyle.app
import android.content.*
import android.graphics.*
import android.os.BatteryManager
import java.io.File
import java.util.*

object Renderer {
    private val cache = HashMap<String, Bitmap?>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    @Volatile var edit = false
    @Volatile var editing = false
    var box = RectF()
    val hits = ArrayList<Pair<Int, RectF>>()
    var clockRect = RectF()
    var handle = RectF()
    var pivot = 0f
    @Volatile var tx = 0f
    @Volatile var ty = 0f

    fun list(c: Context) = Prefs.sp(c).getString("list", "")!!.split("|").filter { it.isNotEmpty() }
    fun current(c: Context): String? {
        val l = list(c); if (l.isEmpty()) return null
        return File(c.filesDir, l[Prefs.sp(c).getInt("idx", 0).mod(l.size)]).path
    }
    private fun load(path: String): Bitmap? {
        // All renderer cache access happens under draw()'s @Synchronized lock.
        // Recycle evicted bitmaps instead of only dropping references, which can
        // otherwise retain large native pixel buffers until GC.
        if (cache.size > 8) {
            cache.values.filterNotNull().distinct().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            cache.clear()
        }
        if (!File(path).exists()) return null
        return cache.getOrPut(path) { try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null } }
    }
    private fun cm(m: ColorMatrix) = ColorMatrixColorFilter(m)
    private fun filter(f: Int): ColorFilter? = when (f) {
        1 -> cm(ColorMatrix().apply { setSaturation(0f) })
        2 -> cm(ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,8f, 0f,1f,0f,0f,0f, 0f,0f,.85f,0f,0f, 0f,0f,0f,1f,0f)))
        3 -> cm(ColorMatrix(floatArrayOf(.88f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1.12f,0f,8f, 0f,0f,0f,1f,0f)))
        4 -> cm(ColorMatrix().apply { setSaturation(1.7f) })
        else -> null
    }

    @Synchronized
    fun draw(cv: Canvas, w: Int, h: Int, ctx: Context) {
        val s = Prefs.sp(ctx)
        Presets.auto(ctx)
        cv.drawColor(0xFF1C1C1E.toInt())
        val path = current(ctx)
        val bmp = path?.let { load(it) }
        var dest: RectF? = null
        val par = s.getInt("para", 0)
        val fl = filter(s.getInt("filter", 0))
        if (bmp != null && path != null) {
            val sc = maxOf(w / bmp.width.toFloat(), h / bmp.height.toFloat()) * (1f + s.getInt("zoom", 0) / 100f) * (1f + 0.1f * par)
            val dw = bmp.width * sc; val dh = bmp.height * sc
            val l = (w - dw) * s.getInt("px", 50) / 100f + tx * w * .025f * par; val t = (h - dh) * s.getInt("py", 50) / 100f + ty * h * .02f * par
            dest = RectF(l, t, l + dw, t + dh)
            val b = s.getInt("blur", 0)
            val src = if (b > 0) cache.getOrPut("$path#$b") {
                val f = 1 + b / 5
                Bitmap.createScaledBitmap(bmp, maxOf(2, bmp.width / f), maxOf(2, bmp.height / f), true)
            }!! else bmp
            p.reset(); p.isFilterBitmap = true; p.colorFilter = fl
            cv.drawBitmap(src, null, dest, p)
        }
        cv.drawColor(Color.argb(s.getInt("dim", 0) * 255 / 100, 0, 0, 0))
        clock(cv, w, h, ctx, s)
        val dd = dest
        if (s.getInt("depth", 0) == 1 && dd != null && path != null) {
            load(path.replace(".jpg", "_cut.png"))?.let {
                p.reset(); p.isFilterBitmap = true; p.colorFilter = fl
                val d2 = RectF(dd); d2.offset(tx * w * .02f * par, ty * h * .016f * par); cv.drawBitmap(it, null, d2, p)
            }
        }
    }

    private fun battery(ctx: Context): Int =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

    private fun widgets(cv: Canvas, w: Int, h: Int, ctx: Context, s: SharedPreferences, y: Float) {
        val items = (0..3).filter { s.getInt("wt$it", 0) != 0 }.toMutableList()
        if (edit && editing && items.size < 4) items.add(-1)
        hits.clear()
        if (items.isEmpty()) return
        val sc = 0.7f + 0.45f * s.getInt("wscale", 50) / 100f
        val gap = w * 0.02f
        val cell = (w * 0.84f - gap * 3) / 4f * sc
        val x0 = (w - (4 * cell + 3 * gap)) / 2f
        var col = 0; var row = 0
        val rects = ArrayList<Pair<Int, RectF>>()
        for (i in items) {
            val sp = intArrayOf(1, 2, 4)[s.getInt("ws$i", 0).coerceIn(0, 2)]
            if (col + sp > 4) { col = 0; row++ }
            val l = x0 + col * (cell + gap); val t = y + row * (cell + gap)
            rects.add(i to RectF(l, t, l + sp * cell + (sp - 1) * gap, t + cell))
            col += sp; if (col >= 4) { col = 0; row++ }
        }
        val bottom = rects.maxOf { it.second.bottom }
        val dy = if (bottom > h * 0.95f) h * 0.95f - bottom else 0f
        val ci = s.getInt("color", 0).coerceIn(Prefs.colors.indices)
        val tc = if (ci == 7 || ci == 8) Color.WHITE else Prefs.colors[ci]
        for ((i, r) in rects) {
            r.offset(0f, dy); hits.add(i to RectF(r))
            if (i < 0) {
                val d = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xBBFFFFFF.toInt(); strokeWidth = 3f
                    pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f); textAlign = Paint.Align.CENTER; textSize = r.height() * .55f }
                cv.drawRoundRect(r, r.height() * .28f, r.height() * .28f, d)
                d.style = Paint.Style.FILL; d.pathEffect = null; cv.drawText("+", r.centerX(), r.centerY() + r.height() * .19f, d)
                continue
            }
            val q = Paint(Paint.ANTI_ALIAS_FLAG)
            q.color = 0x33FFFFFF; cv.drawRoundRect(r, r.height() * .28f, r.height() * .28f, q)
            var big = ""; var small = ""; var prog = -1f
            when (s.getInt("wt$i", 0)) {
                1 -> { val b = battery(ctx); big = if (b >= 0) "$b%" else "--"; small = "Battery"; prog = b / 100f }
                2 -> { big = Info.steps(s)?.toString() ?: "--"; small = "Steps" }
                3 -> { big = Info.alarm(ctx)?.substringAfter(' ') ?: "--"; small = "Alarm" }
                4 -> { big = Info.music ?: "No music"; small = "Now playing" }
                5 -> { val c = Calendar.getInstance(); big = c.get(Calendar.DAY_OF_MONTH).toString()
                       small = java.text.SimpleDateFormat("EEE MMM", Locale.getDefault()).format(c.time) }
                else -> { big = s.getString("txt", "")?.ifBlank { "Your text" } ?: "Your text"; small = "Note" }
            }
            q.color = tc; q.textAlign = Paint.Align.CENTER
            q.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); q.textSize = r.height() * (if (prog >= 0 && r.width() < r.height() * 1.5f) .24f else .34f)
            while (q.measureText(big) > r.width() * .86f && q.textSize > 10f) q.textSize *= .93f
            cv.drawText(big, r.centerX(), r.top + r.height() * .52f, q)
            q.alpha = 200; q.typeface = Typeface.DEFAULT; q.textSize = r.height() * .15f
            cv.drawText(small, r.centerX(), r.top + r.height() * .8f, q)
            if (prog >= 0) {
                q.alpha = 230; q.style = Paint.Style.STROKE; q.strokeWidth = r.height() * .05f; q.strokeCap = Paint.Cap.ROUND
                if (r.width() < r.height() * 1.5f) cv.drawArc(RectF(r.centerX() - r.height() * .42f, r.centerY() - r.height() * .42f, r.centerX() + r.height() * .42f, r.centerY() + r.height() * .42f), -90f, 360f * prog, false, q)
                else cv.drawLine(r.left + r.width() * .1f, r.bottom - r.height() * .08f, r.left + r.width() * (.1f + .8f * prog), r.bottom - r.height() * .08f, q)
            }
        }
    }

    private fun clock(cv: Canvas, w: Int, h: Int, ctx: Context, s: SharedPreferences) {
        val st = when (s.getInt("layout", 0)) { 1 -> 4; 2 -> 5; else -> 0 }; val ci = s.getInt("color", 0).coerceIn(Prefs.colors.indices)
        val base = w * (0.20f + 0.35f * s.getInt("size", 50) / 100f)
        val cyv = s.getInt("cy", -1)
        val top = if (cyv >= 0) h * cyv / 1000f else h * (0.10f + 0.26f * s.getInt("pos", 0))
        val left = s.getInt("align", 0) == 1
        val x = if (left) w * 0.08f else w / 2f
        val ds = w * 0.045f
        p.reset(); p.isAntiAlias = true
        p.textAlign = if (left) Paint.Align.LEFT else Paint.Align.CENTER
        val col = Prefs.colors[ci]
        fun tint(y: Float) { if (s.getInt("anim", 0) == 1 && ci != 7 && ci != 8) { val sh = (System.currentTimeMillis() % 3000) / 3000f * w * 2.2f; p.shader = LinearGradient(sh - w * .6f, 0f, sh, 0f, intArrayOf(col, 0xFFFFFFFF.toInt(), col), null, Shader.TileMode.CLAMP) } else if (ci == 7) p.shader = LinearGradient(0f, y - base, 0f, y + base * 0.3f, 0xFFFF6B8A.toInt(), 0xFFFF9F0A.toInt(), Shader.TileMode.CLAMP) else { p.shader = null; p.color = col } }
        // date
        p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); p.textSize = ds
        p.style = Paint.Style.FILL; tint(top); p.alpha = 225
        cv.drawText(java.text.SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), x, top + ds, p)
        // time
        val cal = Calendar.getInstance()
        val hr = (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }).toString()
        val mn = "%02d".format(cal.get(Calendar.MINUTE))
        p.typeface = Prefs.typeface(ctx, s)
        p.textSize = if (st == 4) base * 0.85f else base
        if (st == 5) { p.style = Paint.Style.STROKE; p.strokeWidth = 3f + base * 0.015f } else p.style = Paint.Style.FILL
        val y1 = top + ds * 1.6f + p.textSize * 0.85f
        tint(y1); if (ci == 8) p.alpha = 150 else if (ci == 9) p.alpha = 205 else p.alpha = 255
        var last = y1
        val sy = 0.5f + 0.05f * s.getInt("cst", 10)
        val sx = 0.3f + 0.014f * s.getInt("csw", 50)
        val pyv = y1 - p.textSize * 0.75f
        cv.save(); cv.scale(sx, sy, x, pyv)
        if (st == 4) { cv.drawText(hr, x, y1, p); last = y1 + p.textSize * 0.82f; cv.drawText(mn, x, last, p) }
        else cv.drawText("$hr:$mn", x, y1, p)
        cv.restore()
        last = pyv + (last - pyv) * sy
        pivot = pyv
        val tw = (if (st == 4) maxOf(p.measureText(hr), p.measureText(mn)) else p.measureText("$hr:$mn")) * sx
        val bl = if (left) x else x - tw / 2f
        box = RectF(bl, pyv, bl + tw, last)
        if (edit && editing) {
            val rr = w * .05f
            handle = RectF(box.right - rr, box.bottom - rr, box.right + rr, box.bottom + rr)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xCCFFFFFF.toInt()
                pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f) }
            cv.drawRoundRect(box.left - 10f, box.top - 10f, box.right + 10f, box.bottom + 10f, 24f, 24f, bp)
            bp.pathEffect = null; bp.strokeWidth = 5f; bp.color = Color.WHITE
            cv.drawCircle(handle.centerX(), handle.centerY(), rr, bp)
            bp.style = Paint.Style.FILL; bp.alpha = 70; cv.drawCircle(handle.centerX(), handle.centerY(), rr, bp)
        } else handle = RectF()
        // widgets line
        val parts = ArrayList<String>()
        if (s.getInt("wbat", 0) == 1) {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lv = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (lv >= 0) parts.add("Battery $lv%")
        }
        if (s.getInt("wstep", 0) == 1) Info.steps(s)?.let { parts.add("$it steps") }
        if (s.getInt("walarm", 0) == 1) Info.alarm(ctx)?.let { parts.add("Alarm $it") }
        if (s.getInt("wmusic", 0) == 1) Info.music?.let { parts.add(it) }
        s.getString("txt", "")?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (parts.isNotEmpty()) {
            p.reset(); p.isAntiAlias = true; p.textAlign = if (left) Paint.Align.LEFT else Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); p.textSize = ds * 0.95f
            p.color = if (ci == 7 || ci == 8) Color.WHITE else col; p.alpha = 220
            val line = parts.joinToString("   •   ")
            while (p.measureText(line) > w * 0.9f && p.textSize > ds * .5f) p.textSize *= 0.93f
            cv.drawText(line, x, last + ds * 2.2f, p)
        }
        clockRect = RectF(w * .05f, top, w * .95f, last + ds * .8f)
        widgets(cv, w, h, ctx, s, last + ds * (if (parts.isEmpty()) 2.4f else 3.6f))
    }
}
