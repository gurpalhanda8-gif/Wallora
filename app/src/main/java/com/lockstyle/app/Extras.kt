package com.lockstyle.app
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object Presets {
    fun names(c: Context) = Prefs.sp(c).getString("pre_names", "")!!.split("|").filter { it.isNotEmpty() }
    fun save(c: Context, raw: String) {
        val n = raw.replace("|", ""); val s = Prefs.sp(c)
        val skip = listOf("list", "pre_", "auto", "step")
        val m = s.all.filterKeys { k -> skip.none { k.startsWith(it) } }
        s.edit().putString("pre_$n", JSONObject(m).toString())
            .putString("pre_names", (names(c).filter { it != n } + n).joinToString("|")).apply()
    }
    fun load(c: Context, n: String) {
        val o = JSONObject(Prefs.sp(c).getString("pre_$n", "{}")!!); val e = Prefs.sp(c).edit()
        o.keys().forEach { k -> val v = o.get(k); if (v is Int) e.putInt(k, v) else e.putString(k, v.toString()) }
        e.apply()
    }
    fun auto(c: Context) {
        val s = Prefs.sp(c); if (s.getInt("autoOn", 0) != 1) return
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val n = s.getString(when { hr in 6..16 -> "autoM"; hr in 17..20 -> "autoE"; else -> "autoN" }, "") ?: ""
        if (n.isNotEmpty() && n != s.getString("autoLast", "")) { load(c, n); s.edit().putString("autoLast", n).apply() }
    }
}

object Info {
    @Volatile var music: String? = null
    fun saveSteps(s: SharedPreferences, raw: Float) {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val e = s.edit()
        if (s.getString("stepDay", "") != day) e.putString("stepDay", day).putFloat("stepBase", raw)
        e.putFloat("stepRaw", raw).apply()
    }
    fun steps(s: SharedPreferences): Int? {
        val r = s.getFloat("stepRaw", -1f); if (r < 0) return null
        return (r - s.getFloat("stepBase", r)).toInt().coerceAtLeast(0)
    }
    fun alarm(c: Context): String? {
        val t = c.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: return null
        return SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(t))
    }
}

class MediaListener : NotificationListenerService() {
    private fun upd() {
        Info.music = try {
            val c = getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(this, MediaListener::class.java)).firstOrNull()
            val md = c?.metadata
            if (c?.playbackState?.state == PlaybackState.STATE_PLAYING && md != null) {
                val t = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val a = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                (if (a != null) "$t - $a" else t).take(30)
            } else null
        } catch (e: Exception) { null }
    }
    override fun onListenerConnected() = upd()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = upd()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = upd()
}
