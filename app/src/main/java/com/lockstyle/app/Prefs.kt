package com.lockstyle.app
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import java.io.File
import kotlin.math.abs

class F(val name: String, val sys: String? = null, val files: Map<Int, String> = emptyMap(), val variable: String? = null)

object Prefs {
    val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF6B8A.toInt(),
        0xFF64D2FF.toInt(), 0xFFA6F5A0.toInt(), 0xFF111111.toInt(), 0, 0x99FFFFFF.toInt(), 0xCCBFD9F7.toInt())
    val colorNames = listOf("White", "Yellow", "Orange", "Pink", "Blue", "Green", "Black", "Gradient", "Glass", "Ice")

    val fonts = listOf(
        F("Outfit", files = mapOf(400 to "Outfit-Regular.ttf", 700 to "Outfit-Bold.ttf")),
        F("Bricolage", files = mapOf(400 to "BricolageGrotesque-Regular.ttf", 700 to "BricolageGrotesque-Bold.ttf")),
        F("Instrument", files = mapOf(400 to "InstrumentSans-Regular.ttf", 700 to "InstrumentSans-Bold.ttf")),
        F("Editorial Serif", files = mapOf(400 to "InstrumentSerif-Regular.ttf")),
        F("Tall Condensed", files = mapOf(400 to "BigShoulders-Regular.ttf", 700 to "BigShoulders-Bold.ttf")),
        F("Jura Tech", files = mapOf(300 to "Jura-Light.ttf", 500 to "Jura-Medium.ttf")),
        F("Poiret Thin", files = mapOf(400 to "PoiretOne-Regular.ttf")),
        F("Tektur", files = mapOf(400 to "Tektur-Regular.ttf", 500 to "Tektur-Medium.ttf")),
        F("Gloock", files = mapOf(400 to "Gloock-Regular.ttf")),
        F("Boldonse Wide", files = mapOf(400 to "Boldonse-Regular.ttf")),
        F("Roboto", sys = "sans-serif"),
        F("Roboto Condensed", sys = "sans-serif-condensed"),
        F("Times Serif", files = mapOf(400 to "LiberationSerif-Regular.ttf", 700 to "LiberationSerif-Bold.ttf")),
        F("Arial Sans", files = mapOf(400 to "LiberationSans-Regular.ttf", 700 to "LiberationSans-Bold.ttf")),
        F("Poppins", files = mapOf(300 to "Poppins-Light.ttf", 400 to "Poppins-Regular.ttf", 500 to "Poppins-Medium.ttf", 700 to "Poppins-Bold.ttf")),
        F("Lora", variable = "Lora-Variable.ttf"),
        F("Calibri-like", files = mapOf(400 to "Carlito-Regular.ttf", 700 to "Carlito-Bold.ttf")),
        F("Cambria-like", files = mapOf(400 to "Caladea-Regular.ttf", 700 to "Caladea-Bold.ttf")),
        F("Courier Mono", files = mapOf(400 to "LiberationMono-Regular.ttf", 700 to "LiberationMono-Bold.ttf")),
        F("Casual", sys = "casual"),
        F("Script", sys = "cursive"))
    val famNames = fonts.map { it.name }

    fun sp(c: Context) = c.getSharedPreferences("ls", Context.MODE_PRIVATE)
    fun customFonts(c: Context) = sp(c).getString("fonts", "")!!.split("|").filter { it.isNotEmpty() }

    private val cache = HashMap<String, Typeface>()
    fun face(c: Context, i: Int, w: Int): Typeface = cache.getOrPut("${c.packageName}:$i:$w:${customFonts(c).size}") {
        try {
            if (i >= fonts.size) {
                val custom = customFonts(c)
                Typeface.createFromFile(File(c.filesDir, custom.getOrNull(i - fonts.size) ?: return@getOrPut Typeface.DEFAULT))
            }
            else {
                val f = fonts[i]
                when {
                    f.sys != null -> {
                        val b = Typeface.create(f.sys, Typeface.NORMAL)
                        if (Build.VERSION.SDK_INT >= 28) Typeface.create(b, w, false)
                        else Typeface.create(b, if (w >= 700) Typeface.BOLD else Typeface.NORMAL)
                    }
                    f.variable != null -> Typeface.Builder(c.assets, "fonts/${f.variable}")
                        .setFontVariationSettings("'wght' ${w.coerceIn(400, 700)}").build()
                    else -> Typeface.createFromAsset(c.assets, "fonts/" + f.files.entries.minByOrNull { abs(it.key - w) }!!.value)
                }
            }
        } catch (e: Throwable) { Typeface.DEFAULT }
    }
    fun baseFace(c: Context, i: Int) = face(c, i, 400)

    fun typeface(c: Context, s: SharedPreferences): Typeface =
        face(c, s.getInt("font", 0), intArrayOf(100, 300, 400, 500, 700, 900)[s.getInt("weight", 4).coerceIn(0, 5)])
}
