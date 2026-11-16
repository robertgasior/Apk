package pl.loginguard.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Detector {
    private val terms = Regex("(?i)(kod.{0,25}(logowan|weryfik|potwierdz|jednoraz)|verification code|sign.in code|login code|security code|two.factor|2fa|otp|logowani|zalogowan|nowe urządzenie|new device|reset.{0,12}hasł|password reset|potwierdź.{0,12}konto|confirm.{0,12}account)")
    private val providers = listOf("Google", "Microsoft", "Facebook", "Instagram", "WhatsApp", "Telegram", "Allegro", "Amazon", "Apple", "PayPal", "OLX", "TikTok", "LinkedIn", "Discord", "Xiaomi", "BLIK", "mBank", "PKO", "Santander", "ING", "Revolut")
    fun identify(text: String, source: String): String? {
        if (!terms.containsMatchIn(text)) return null
        return providers.firstOrNull { text.contains(it, ignoreCase = true) } ?: source.take(55).ifBlank { "Nieznany serwis" }
    }
    @Synchronized fun add(context: Context, source: String, text: String, time: Long = System.currentTimeMillis()): Boolean {
        val provider = identify(text, source) ?: return false
        val prefs = context.getSharedPreferences("events", Context.MODE_PRIVATE)
        val events = JSONArray(prefs.getString("list", "[]"))
        val item = JSONObject().put("provider", provider).put("source", source.take(65)).put("time", time)
        for (i in 0 until events.length()) {
            val old = events.getJSONObject(i)
            if (old.optString("provider") == provider && old.optString("source") == source && kotlin.math.abs(old.optLong("time") - time) < 60000) return false
        }
        events.put(item)
        val trimmed = JSONArray()
        for (i in maxOf(0, events.length() - 1500) until events.length()) trimmed.put(events.getJSONObject(i))
        prefs.edit().putString("list", trimmed.toString()).apply()
        return true
    }
    fun report(context: Context): String {
        val events = JSONArray(context.getSharedPreferences("events", Context.MODE_PRIVATE).getString("list", "[]"))
        if (events.length() == 0) return "Brak wykrytych zdarzeń. Zaimportuj historię lub włącz monitoring powiadomień."
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return (events.length() - 1 downTo 0).joinToString("\n\n") { i ->
            val e = events.getJSONObject(i)
            "${fmt.format(Date(e.optLong("time")))}  •  ${e.optString("provider")}\nŹródło: ${e.optString("source")}"
        }
    }
    fun clear(context: Context) { context.getSharedPreferences("events", Context.MODE_PRIVATE).edit().clear().apply() }
}