package com.fraunhofer.aikeyboard2.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Fn + harf kısayollarını SharedPreferences'da saklar.
 *
 * Her kısayol JSON olarak tutulur:
 *   { "action": "TYPE_TEXT", "text": "Merhaba, nasılsınız?" }
 *
 * Anahtar: tek karakter (lowercase), örn. "a", "b", "c"
 */
class ShortcutRepository(context: Context) {

    enum class ActionType(val label: String) {
        SELECT_ALL("Tümünü Seç"),
        COPY("Kopyala"),
        PASTE("Yapıştır"),
        TYPE_TEXT("Metin Yaz")
    }

    data class Shortcut(
        val key: Char,
        val action: ActionType,
        val text: String = ""
    ) {
        fun actionLabel(): String = when (action) {
            ActionType.TYPE_TEXT -> "\"$text\""
            else -> action.label
        }

        fun displayLabel(): String = "Fn + ${key.uppercaseChar()}  →  ${actionLabel()}"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("shortcut_prefs", Context.MODE_PRIVATE)

    /** Tüm kısayolları döner. */
    fun getAll(): Map<Char, Shortcut> {
        val all = prefs.all
        val result = mutableMapOf<Char, Shortcut>()
        for ((k, v) in all) {
            if (k.length != 1) continue
            val key = k[0]
            try {
                val json = JSONObject(v as String)
                val action = ActionType.valueOf(json.getString("action"))
                val text   = json.optString("text", "")
                result[key] = Shortcut(key, action, text)
            } catch (_: Exception) { /* bozuk kayıt yoksay */ }
        }
        return result
    }

    /** Tek kısayol döner. */
    fun get(key: Char): Shortcut? = getAll()[key.lowercaseChar()]

    /** Kısayolu kaydeder. Aynı harf varsa üzerine yazar. */
    fun save(shortcut: Shortcut) {
        val json = JSONObject().apply {
            put("action", shortcut.action.name)
            put("text",   shortcut.text)
        }
        prefs.edit().putString(shortcut.key.lowercaseChar().toString(), json.toString()).apply()
    }

    /** Kısayolu siler. */
    fun remove(key: Char) {
        prefs.edit().remove(key.lowercaseChar().toString()).apply()
    }
}
