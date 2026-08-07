package com.fraunhofer.aikeyboard2

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Uygulama ana ekranı.
 * Kullanıcıya klavyeyi sistem ayarlarından etkinleştirmesi için yönlendirme yapar.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable  = findViewById<Button>(R.id.btn_enable_keyboard)
        val btnSelect  = findViewById<Button>(R.id.btn_select_keyboard)
        val tvStatus   = findViewById<TextView>(R.id.tv_status)

        // Klavyenin etkin olup olmadığını kontrol et
        updateStatus(tvStatus)

        // Dil/Giriş Yöntemi Ayarları'na git
        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // Klavye seçici dialog'u aç
        btnSelect.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    override fun onResume() {
        super.onResume()
        // Ayarlardan dönünce durumu yenile
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        updateStatus(tvStatus)
    }

    private fun updateStatus(tvStatus: TextView) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any {
            it.packageName == packageName
        }
        tvStatus.text = if (enabled) "✅ AI Klavye etkin" else "⚠️ AI Klavye henüz etkinleştirilmedi"
    }
}