package com.bybora.smartxtream.utils

import android.content.Context
import android.content.SharedPreferences

object TrialManager {
    private const val PREFS_NAME = "TrialPrefs"
    private const val KEY_FIRST_RUN_TIME = "first_run_time"

    // 14 Gün (Milisaniye cinsinden hesaplama)
    // 14 gün * 24 saat * 60 dakika * 60 saniye * 1000 milisaniye
    private const val TRIAL_DURATION_MS = 14L * 24 * 60 * 60 * 1000

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun checkTrialStatus(context: Context): Boolean {
        val prefs = getPrefs(context)
        var firstRunTime = prefs.getLong(KEY_FIRST_RUN_TIME, 0L)

        // Uygulama ilk kez açılıyorsa şu anki zamanı kaydet
        if (firstRunTime == 0L) {
            firstRunTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_RUN_TIME, firstRunTime).apply()
        }

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - firstRunTime

        // Geçen süre 14 günden AZ ise TRUE döndür (Deneme Devam Ediyor)
        return elapsedTime < TRIAL_DURATION_MS
    }

    fun getRemainingDays(context: Context): Long {
        val prefs = getPrefs(context)
        val firstRunTime = prefs.getLong(KEY_FIRST_RUN_TIME, System.currentTimeMillis())
        val elapsedTime = System.currentTimeMillis() - firstRunTime
        val remaining = TRIAL_DURATION_MS - elapsedTime

        // Kalan milisaniyeyi güne çevir
        return if (remaining > 0) remaining / (24 * 60 * 60 * 1000) else 0
    }
}