package pl.loginguard.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var output: TextView
    private val importCode = 101
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 36, 30, 30) }
        scroll.addView(layout)
        fun label(text: String, size: Float = 16f) { layout.addView(TextView(this).apply { this.text = text; textSize = size; setPadding(0, 8, 0, 16) }) }
        fun action(text: String, onClick: () -> Unit) { layout.addView(Button(this).apply { this.text = text; setOnClickListener { onClick() } }) }
        label("LoginGuard", 28f)
        label("Monitorowanie wyłącznie podczas otwarcia aplikacji. Wykrycie kodu nie oznacza udanego logowania.")
        action("Zezwól na odczyt powiadomień (tylko gdy aplikacja otwarta)") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        action("Importuj historię SMS (TXT/CSV)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, importCode)
        }
        action("Odśwież raport") { output.text = Detector.report(this) }
        action("Usuń całą historię") {
            AlertDialog.Builder(this).setTitle("Usunąć historię?").setMessage("Wszystkie zapisane zdarzenia zostaną usunięte z urządzenia.")
                .setNegativeButton("Anuluj", null).setPositiveButton("Usuń") { _, _ -> Detector.clear(this); output.text = Detector.report(this) }.show()
        }
        label("Wykryte zdarzenia", 20f)
        output = TextView(this).apply { textSize = 15f; text = Detector.report(this@MainActivity) }
        layout.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
    }
    override fun onResume() {
        super.onResume()
        MonitoringSession.isOpen = true
        if (::output.isInitialized) output.text = Detector.report(this)
    }
    override fun onPause() {
        MonitoringSession.isOpen = false
        super.onPause()
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            if (raw.length > 8_000_000) { Toast.makeText(this, "Plik zbyt duży (maks. 8 MB)", Toast.LENGTH_LONG).show(); return }
            var count = 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { isLenient = false }
            raw.lineSequence().take(20000).forEach { line ->
                val parts = line.split('|', limit = 3)
                val time = if (parts.size == 3) runCatching { dateFormat.parse(parts[0].trim())?.time }.getOrNull() else null
                val source = if (parts.size == 3) parts[1].trim() else "Import"
                val body = if (parts.size == 3) parts[2] else line
                if (Detector.add(this, "Import: $source", body, time ?: System.currentTimeMillis())) count++
            }
            output.text = Detector.report(this)
            Toast.makeText(this, "Dodano $count zdarzeń", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Nie udało się odczytać pliku", Toast.LENGTH_LONG).show() }
    }
}