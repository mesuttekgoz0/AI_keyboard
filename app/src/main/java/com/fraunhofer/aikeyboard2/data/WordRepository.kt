package com.fraunhofer.aikeyboard2.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Yasaklı kelime deposu. applicationContext üzerinden SharedPreferences kullanır.
 * Her işlemde getWords() çağrısı yapmak yerine doğrudan set üzerinde çalışır.
 */
class WordRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("profanity_prefs", Context.MODE_PRIVATE)

    /** Mevcut kelime setini döner (copy-on-access — no list allocation). */
    fun getWordSet(): Set<String> {
        return prefs.getStringSet("banned_words", null) ?: run {
            val defaults = setOf("aba", "ab", "kufur", "kotu", "hakaret", "aptal", "salak")
            prefs.edit().putStringSet("banned_words", defaults).apply()
            defaults
        }
    }

    /** Eski API uyumluluğu için — dahili kullanım, mümkünse getWordSet() tercih edin. */
    fun getWords(): List<String> = getWordSet().toList()

    fun addWord(word: String) {
        val trimmed = word.trim().lowercase()
        if (trimmed.isEmpty()) return
        val current = prefs.getStringSet("banned_words", null)?.toMutableSet() ?: mutableSetOf()
        if (current.add(trimmed)) {
            prefs.edit().putStringSet("banned_words", current).apply()
        }
    }

    fun removeWord(word: String) {
        val trimmed = word.trim().lowercase()
        val current = prefs.getStringSet("banned_words", null)?.toMutableSet() ?: return
        if (current.remove(trimmed)) {
            prefs.edit().putStringSet("banned_words", current).apply()
        }
    }
}
