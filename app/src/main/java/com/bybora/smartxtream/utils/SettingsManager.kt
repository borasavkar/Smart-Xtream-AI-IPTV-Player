package com.bybora.smartxtream.utils // Refactor sonrası yeni paket isminiz

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale // Sistem dilini çekmek için gerekli kütüphane

object SettingsManager {
    private const val PREF_NAME = "BoraPlayerSettings"

    // Anahtarlar
    private const val KEY_DATA_SAVER = "data_saver"
    private const val KEY_AUDIO_LANG = "audio_lang"
    private const val KEY_SUBTITLE_LANG = "subtitle_lang"
    private const val KEY_SELECTED_PROFILE_ID = "selected_profile_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- YARDIMCI FONKSİYON: SİSTEM DİLİNİ BUL ---
    // Telefonun dil kodunu (örneğin "en", "tr", "de") döndürür.
    private fun getSystemLanguage(): String {
        return Locale.getDefault().language ?: "tr"
    }

    // --- PROFİL HAFIZASI ---
    fun saveSelectedProfileId(context: Context, id: Int) {
        getPrefs(context).edit {
            putInt(KEY_SELECTED_PROFILE_ID, id)
        }
    }

    fun getSelectedProfileId(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_PROFILE_ID, -1)
    }

    // --- VERİ TASARRUFU (DATA SAVER) ---
    fun setDataSaver(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_DATA_SAVER, enabled)
        }
    }

    fun getDataSaver(context: Context): Boolean = getPrefs(context).getBoolean(KEY_DATA_SAVER, true)

    // --- DİL AYARLARI (GÜNCELLENDİ) ---
    fun setAudioLang(context: Context, langCode: String) {
        getPrefs(context).edit {
            putString(KEY_AUDIO_LANG, langCode)
        }
    }

    fun setSubtitleLang(context: Context, langCode: String) {
        getPrefs(context).edit {
            putString(KEY_SUBTITLE_LANG, langCode)
        }
    }

    // GÜNCELLEME: Varsayılan değer olarak artık sabit "tr" değil,
    // sistemin dili (getSystemLanguage) kullanılıyor.
    fun getAudioLang(context: Context): String {
        val defaultLang = getSystemLanguage()
        return getPrefs(context).getString(KEY_AUDIO_LANG, defaultLang) ?: defaultLang
    }

    fun getSubtitleLang(context: Context): String {
        val defaultLang = getSystemLanguage()
        return getPrefs(context).getString(KEY_SUBTITLE_LANG, defaultLang) ?: defaultLang
    }
    // --- PREMIUM KONTROLÜ ---
    private const val KEY_IS_PREMIUM = "is_premium_user"

    fun setPremiumStatus(context: Context, isPremium: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_IS_PREMIUM, isPremium) }
    }

    // Güvenlik Notu: Gerçek bir uygulamada bu kontrolü sunucu tarafında yapmak en güvenlisidir.
    fun isPremiumUser(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_PREMIUM, false)
    }
}