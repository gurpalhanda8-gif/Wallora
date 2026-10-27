package com.lockstyle.app
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var pv: View
    private lateinit var sp: android.content.SharedPreferences
    private val col by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(32)) } }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val imageWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "LockStyle-image-worker").apply { isDaemon = true } }
    private fun ref() = pv.invalidate()
    private fun put(k: String, v: Int) { sp.edit().putInt(k, v).apply(); ref() }

    private val pick = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { l -> l.forEach { add(it) } }

    private val pickFont = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val n = "font_${System.nanoTime()}.ttf"; val f = File(filesDir, n)
        try {
            val copied = contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { output -> input.copyTo(output) } } ?: 0L
            require(copied in 1..(20L * 1024L * 1024L)) { "Unsupported font size" }
            Typeface.createFromFile(f)
        } catch (e: Exception) {
            f.delete(); Toast.makeText(this, "Couldn't load this font. Choose a valid TTF/OTF file.", Toast.LENGTH_LONG).show(); return@registerForActivityResult
        }
        val l = Prefs.customFonts(this) + n
        sp.edit().putString("fonts", l.joinToString("|")).putInt("font", Prefs.famNames.size + l.size - 1).apply(); recreate()
    }

    private fun fontChips() = chips("Clock font", "font", Prefs.famNames + Prefs.customFonts(this).indices.map { "Custom ${it + 1}" },
        face = { Prefs.baseFace(this, it) }, extra = btn("+ Add font") { pickFont.launch("*/*") })

    private fun add(uri: Uri) {
        val name = "wp_${System.nanoTime()}.jpg"
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
        var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        if (bmp == null) { Toast.makeText(this, "Couldn't open this image", Toast.LENGTH_SHORT).show(); return }
        val rot = try { contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 } catch (e: Exception) { 0 }
        if (rot != 0) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        try {
            FileOutputStream(File(filesDir, name)).use { out -> check(bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) }
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save this wallpaper", Toast.LENGTH_LONG).show(); return
        }
        val l = Renderer.list(this) + name
        sp.edit().putString("list", l.joinToString("|")).putInt("idx", l.size - 1).apply(); ref()
        cutout(bmp, name)
    }

    private fun cutout(bmp: Bitmap, name: String) {
        Toast.makeText(this, "Preparing depth layer…", Toast.LENGTH_SHORT).show()
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        val segmenter = Segmentation.getClient(options)
        try {
            segmenter.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { mask ->
                try {
                    imageWorker.execute {
                    try {
                        val w = mask.width
                        val h = mask.height
                        val buffer = mask.buffer.apply { rewind(); order(ByteOrder.nativeOrder()) }
                        val pixels = IntArray(w * h)
                        Bitmap.createScaledBitmap(bmp, w, h, true).useBitmap { scaled ->
                            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                        }
                        for (i in pixels.indices) {
                            val confidence = buffer.getFloat().coerceIn(0f, 1f)
                            val alpha = when {
                                confidence >= .92f -> 255
                                confidence <= .18f -> 0
                                else -> (((confidence - .18f) / .74f) * 255f).toInt().coerceIn(0, 255)
                            }
                            pixels[i] = (pixels[i] and 0x00FFFFFF) or (alpha shl 24)
                        }
                        val out = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                        FileOutputStream(File(filesDir, name.replace(".jpg", "_cut.png"))).use {
                            check(out.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        out.recycle()
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                ref(); Toast.makeText(this, "Depth layer ready", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) Toast.makeText(this, "Wallpaper added; depth layer couldn't be created", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        segmenter.close()
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    segmenter.close()
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
            .addOnFailureListener {
                segmenter.close()
                if (!bmp.isRecycled) bmp.recycle()
                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Wallpaper added; subject cutout isn't available for this image", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            segmenter.close()
            if (!bmp.isRecycled) bmp.recycle()
            if (!isFinishing && !isDestroyed) Toast.makeText(this, "Wallpaper added; depth processing couldn't start", Toast.LENGTH_LONG).show()
        }
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }

    private val ACC = 0xFF0A84FF.toInt(); private val CARD = 0xFF1C1C1E.toInt(); private val CHIP = 0xFF2C2C2E.toInt(); private val MUT = 0xFF8E8E93.toInt()
    private fun pill(c: Int, r: Int = 22) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(c) }
    private fun cardLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    private fun card(v: View) = LinearLayout(this).apply { background = pill(CARD, 18); setPadding(dp(14), dp(10), dp(14), dp(10)); addView(v, LinearLayout.LayoutParams(-1, -2)) }

    private fun title(t: String) = col.addView(TextView(this).apply {
        text = t.uppercase(); textSize = 12f; letterSpacing = .08f; setTextColor(MUT); setTypeface(null, Typeface.BOLD); setPadding(dp(4), dp(20), 0, dp(8)) })
    private fun btn(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = pill(CHIP)
        setPadding(dp(16), dp(10), dp(16), dp(10)); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
        setOnClickListener { f() }
    }
    private fun big(t: String, f: () -> Unit) = btn(t, f).apply {
        textSize = 17f; background = pill(ACC, 28); setPadding(dp(16), dp(15), dp(16), dp(15)); layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    private fun chips(t: String, key: String, names: List<String>, face: ((Int) -> Typeface)? = null, extra: View? = null) {
        title(t)
        val def = if (key == "weight") 4 else 0
        val r = LinearLayout(this); val bs = ArrayList<View>()
        fun refresh() {
            val cur = sp.getInt(key, def)
            bs.forEachIndexed { i, b ->
                if (key == "color") (b.background as GradientDrawable).setStroke(dp(if (i == cur) 3 else 1), if (i == cur) Color.WHITE else 0xFF636366.toInt())
                else b.background = pill(if (i == cur) ACC else CHIP)
            }
        }
        names.forEachIndexed { i, n ->
            val b: View = if (key == "color") View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (i == 7) setColors(intArrayOf(0xFFFF6B8A.toInt(), 0xFFFF9F0A.toInt())) else setColor(Prefs.colors[i])
                }
                contentDescription = n
            } else btn(n) {}.apply { face?.let { typeface = it(i) } }
            b.setOnClickListener { put(key, i); refresh() }
            bs.add(b); r.addView(b)
        }
        extra?.let { r.addView(it) }
        refresh()
        col.addView(card(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(r) }), cardLp())
    }
    private fun seek(t: String, key: String, def: Int, max: Int) {
        title(t)
        col.addView(card(SeekBar(this).apply {
            this.max = max; progress = sp.getInt(key, def)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, v: Int, u: Boolean) { if (u) put(key, v) }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }), cardLp())
    }
    private fun sw(t: String, key: String, def: Int) = col.addView(card(Switch(this).apply {
        text = t; textSize = 15f; isChecked = sp.getInt(key, def) == 1
        setOnCheckedChangeListener { _, c -> put(key, if (c) 1 else 0) }
    }), cardLp())

    private fun toggleLock() {
        if (sp.getInt("lockOn", 0) == 1) {
            stopService(Intent(this, LockService::class.java)); sp.edit().putInt("lockOn", 0).apply(); recreate(); return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps', then tap Enable again", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2)
        startForegroundService(Intent(this, LockService::class.java)); sp.edit().putInt("lockOn", 1).apply(); recreate()
    }

    private fun pickWidget(slot0: Int) {
        val slot = if (slot0 >= 0) slot0 else (0..3).firstOrNull { sp.getInt("wt$it", 0) == 0 } ?: return
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Widget ${slot + 1}")
            .setItems(arrayOf("Battery", "Steps", "Alarm", "Music", "Date", "Text", "Remove")) { _, w ->
                if (w == 6) { put("wt$slot", 0); recreate(); return@setItems }
                put("wt$slot", w + 1)
                if (w == 1 && android.os.Build.VERSION.SDK_INT >= 29) requestPermissions(arrayOf("android.permission.ACTIVITY_RECOGNITION"), 1)
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Size")
                    .setItems(arrayOf("Small", "Wide", "Full")) { _, z -> put("ws$slot", z); recreate() }.show()
            }.show()
    }

    private fun presetRow(names: List<String>, f: (String) -> Unit) {
        val r = LinearLayout(this); names.forEach { n -> r.addView(btn(n) { f(n) }) }
        col.addView(HorizontalScrollView(this).apply { addView(r) })
    }

    override fun onDestroy() {
        // No new background work should be queued after the activity is destroyed.
        imageWorker.shutdown()
        super.onDestroy()
    }

    override fun onCreate(s: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(s)
        sp = Prefs.sp(this)
        var y0 = 0f; var cy0 = 0f; var drag = false; var moved = false; var stretch = false; var sy0 = 1f; var h0 = 1f; var sx0 = 1f; var w0 = 1f; var x0 = 0f
        val sg = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val b = (0.20f + 0.35f * sp.getInt("size", 50) / 100f) * d.scaleFactor
                put("size", ((b - 0.20f) / 0.35f * 100f).toInt().coerceIn(0, 100)); moved = true; return true
            }
        })
        pv = object : View(this) {
            override fun onDraw(c: Canvas) { Renderer.edit = true; Renderer.draw(c, width, height, context); Renderer.edit = false }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                sg.onTouchEvent(e)
                if (sg.isInProgress || e.pointerCount > 1) { drag = false; moved = true; return true }
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        y0 = e.y; x0 = e.x; moved = false; val cr = Renderer.clockRect
                        val hd = RectF(Renderer.handle); hd.inset(-dp(24).toFloat(), -dp(24).toFloat())
                        stretch = Renderer.editing && !Renderer.handle.isEmpty && hd.contains(e.x, e.y)
                        if (stretch) {
                            sy0 = 0.5f + 0.05f * sp.getInt("cst", 10); sx0 = 0.3f + 0.014f * sp.getInt("csw", 50)
                            h0 = maxOf(40f, Renderer.box.height()); w0 = maxOf(40f, Renderer.box.width())
                        }
                        drag = Renderer.editing && !stretch && e.y >= cr.top - dp(14) && e.y <= cr.bottom + dp(14); cy0 = cr.top / height * 1000f
                    }
                    MotionEvent.ACTION_MOVE -> if (stretch) {
                        moved = true
                        val ny = sy0 * (h0 + (e.y - y0)) / h0
                        val nx = sx0 * (w0 + (e.x - x0) * (if (sp.getInt("align", 0) == 1) 1f else 2f)) / w0
                        sp.edit().putInt("cst", ((ny - 0.5f) / 0.05f).toInt().coerceIn(0, 100))
                            .putInt("csw", ((nx - 0.3f) / 0.014f).toInt().coerceIn(0, 100)).apply(); ref()
                    } else if (drag) {
                        if (kotlin.math.abs(e.y - y0) > dp(6)) moved = true
                        if (moved) put("cy", (cy0 + (e.y - y0) / height * 1000f).toInt().coerceIn(0, 800))
                    }
                    MotionEvent.ACTION_UP -> if (!moved) {
                        if (!Renderer.editing) { Renderer.editing = true; ref() }
                        else {
                            val hit = Renderer.hits.firstOrNull { it.second.contains(e.x, e.y) }
                            if (hit != null) pickWidget(hit.first)
                            else if (!Renderer.clockRect.contains(e.x, e.y)) { Renderer.editing = false; ref() }
                        }
                    }
                }
                return true
            }
        }
        pv.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(26).toFloat()) } }
        pv.clipToOutline = true
        val ph = (resources.displayMetrics.heightPixels * 0.40f).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(FrameLayout(this).apply { setPadding(0, dp(28), 0, dp(10)); addView(pv, FrameLayout.LayoutParams(ph * 9 / 19, ph, Gravity.CENTER_HORIZONTAL)) },
            LinearLayout.LayoutParams(-1, ph + dp(40)))

        col.addView(TextView(this).apply { text = "Preview par tap karo = edit mode. Ring ko khicho = clock lamba aur chauda, clock khicho = hilao, 2 ungli = bada/chhota, widgets jodo. Khali jagah tap karo = edit band (ring gayab). Dotted + par tap karke widget jodo."; textSize = 12f; setTextColor(MUT); setPadding(dp(4), dp(10), 0, 0) })
        title("Lock screen mode")
        col.addView(big(if (sp.getInt("lockOn", 0) == 1) "LockStyle lock screen is ON (tap to turn off)" else "Enable LockStyle lock screen") { toggleLock() })
        col.addView(btn("Test it now") { startActivity(Intent(this, LockActivity::class.java)) })
        col.addView(btn("Allow background running") { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) })
        col.addView(btn("Phone lock settings") { startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) })
        col.addView(TextView(this).apply { text = "Needs: Display over other apps + background running. On Realme also allow Auto-launch for LockStyle in phone settings."; textSize = 12f; setTextColor(MUT); setPadding(dp(4), dp(8), 0, 0) })
        title("Wallpapers")
        val r = LinearLayout(this)
        r.addView(btn("+ Add") { pick.launch("image/*") })
        r.addView(btn("‹") { put("idx", sp.getInt("idx", 0) - 1) })
        r.addView(btn("›") { put("idx", sp.getInt("idx", 0) + 1) })
        r.addView(btn("Delete") {
            val l = Renderer.list(this).toMutableList(); if (l.isEmpty()) return@btn
            val i = sp.getInt("idx", 0).mod(l.size); val n = l.removeAt(i)
            File(filesDir, n).delete(); File(filesDir, n.replace(".jpg", "_cut.png")).delete()
            sp.edit().putString("list", l.joinToString("|")).apply(); ref()
        })
        col.addView(HorizontalScrollView(this).apply { addView(r) })
        sw("Shuffle on every unlock", "shuffle", 0)
        sw("Depth effect (clock behind person)", "depth", 0)

        title("Quick look")
        col.addView(btn("iOS tall look (1 tap)") {
            sp.edit().putInt("font", 10).putInt("weight", 0).putInt("layout", 0).putInt("color", 9).putInt("cst", 58)
                .putInt("csw", 50).putInt("size", 55).putInt("cy", -1).putInt("pos", 0).apply(); recreate()
        })
        fontChips()
        chips("Font weight", "weight", listOf("Thin", "Light", "Regular", "Medium", "Bold", "Black"))
        chips("Clock layout", "layout", listOf("Normal", "Stacked", "Outline"))
        chips("Clock color", "color", Prefs.colorNames)
        seek("Clock size", "size", 50, 100)
        seek("Clock stretch (lamba / chhota)", "cst", 10, 100)
        seek("Clock width", "csw", 50, 100)
        chips("Position", "pos", listOf("Top", "Middle", "Lower"))
        col.addView(btn("Reset dragged position") { put("cy", -1) })
        chips("Align", "align", listOf("Center", "Left"))

        chips("Filter", "filter", listOf("None", "B&W", "Warm", "Cool", "Vivid"))
        seek("Blur", "blur", 0, 100)
        seek("Dim", "dim", 0, 60)
        seek("Zoom", "zoom", 0, 100)
        seek("Move left / right", "px", 50, 100)
        seek("Move up / down", "py", 50, 100)

        title("Widgets")
        sw("Show battery text", "wbat", 0)
        col.addView(EditText(this).apply {
            background = pill(CARD, 14); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Color.WHITE); setHintTextColor(MUT); hint = "Your own text / quote"; setText(sp.getString("txt", "")); setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { sp.edit().putString("txt", e.toString()).apply(); ref() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        })
        sw("Show steps today", "wstep", 0)
        sw("Show next alarm", "walarm", 0)
        sw("Show music playing", "wmusic", 0)
        col.addView(btn("Allow step counting") { if (android.os.Build.VERSION.SDK_INT >= 29) requestPermissions(arrayOf("android.permission.ACTIVITY_RECOGNITION"), 1) })
        col.addView(btn("Allow music access (one time)") { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        for (i in 0..3) {
            chips("Widget ${i + 1}", "wt$i", listOf("None", "Battery", "Steps", "Alarm", "Music", "Date", "Text"))
            chips("Widget ${i + 1} size", "ws$i", listOf("Small", "Wide", "Full"))
        }
        seek("Widget scale (smaller / bigger)", "wscale", 50, 100)
        title("Effects")
        sw("Parallax (tilt phone)", "para", 0)
        sw("Shimmer clock (uses more battery)", "anim", 0)
        title("Presets")
        val ne = EditText(this).apply { background = pill(CARD, 14); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Color.WHITE); setHintTextColor(MUT); hint = "Preset name"; setSingleLine() }
        col.addView(ne)
        col.addView(btn("Save current look") { val n = ne.text.toString().trim(); if (n.isNotEmpty()) { Presets.save(this, n); recreate() } })
        val names = Presets.names(this)
        if (names.isNotEmpty()) {
            title("Load preset"); presetRow(names) { Presets.load(this, it); recreate() }
            sw("Auto change by time of day", "autoOn", 0)
            title("Morning (6am-5pm) preset"); presetRow(names) { sp.edit().putString("autoM", it).apply() }
            title("Evening (5pm-9pm) preset"); presetRow(names) { sp.edit().putString("autoE", it).apply() }
            title("Night (9pm-6am) preset"); presetRow(names) { sp.edit().putString("autoN", it).apply() }
        }
        col.addView(Space(this).apply { minimumHeight = dp(20) })
        col.addView(big("Apply to lock screen") {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, ClockWallpaperService::class.java)))
        })
        col.addView(TextView(this).apply { text = "Then tap Set wallpaper → Lock screen. Changes made later update automatically."; textSize = 12f; setPadding(0, dp(8), 0, 0) })
        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.setBackgroundColor(0xFF000000.toInt()); window.statusBarColor = Color.BLACK
        setContentView(root)
    }
}
