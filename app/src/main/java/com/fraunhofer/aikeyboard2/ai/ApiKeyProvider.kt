package com.fraunhofer.aikeyboard2.ai

import android.content.Context
import com.fraunhofer.aikeyboard2.BuildConfig

/**
 * NVIDIA NIM API key kaynağı — öncelik sırası:
 *  1. Kullanıcının ayarlar ekranından girdiği kendi key'i (SharedPreferences)
 *  2. local.properties'teki test key'i (yalnızca geliştirme, BuildConfig ile gömülür)
 *
 * Asıl hedef "bring your own key": kullanıcı kendi key'ini girdiğinde her zaman o kullanılır.
 */
object ApiKeyProvider {

    private const val PREFS_NAME = "keyboard_settings"
    private const val KEY_USER_API_KEY = "nvidia_api_key"

    fun getUserKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_API_KEY, "") ?: ""

    fun setUserKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_API_KEY, key.trim()).apply()
    }

    fun clearUserKey(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_USER_API_KEY).apply()
    }

    fun isUsingOwnKey(context: Context): Boolean = getUserKey(context).isNotBlank()

    fun isUsingTestKey(context: Context): Boolean =
        !isUsingOwnKey(context) && BuildConfig.NVIDIA_NIM_TEST_API_KEY.isNotBlank()

    /** Kullanılacak fiili key — kullanıcı key'i boşsa test key'e düşer, o da boşsa "" döner. */
    fun resolveApiKey(context: Context): String {
        val userKey = getUserKey(context)
        return userKey.ifBlank { BuildConfig.NVIDIA_NIM_TEST_API_KEY }
    }

    fun hasAnyKey(context: Context): Boolean = resolveApiKey(context).isNotBlank()
}
