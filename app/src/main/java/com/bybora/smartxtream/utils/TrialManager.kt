package com.bybora.smartxtream.utils

import android.content.Context
import android.content.SharedPreferences

object TrialManager {
    private const val PREFS_NAME = "TrialPrefs"
    private const val KEY_FIRST_RUN_TIME = "first_run_time"

    // 14 Gün (Milisaniye cinsinden: 14 * 24 * 60 * 60 * 1000)
    private const val TRIAL_DURATION_MS = 14L * 24 * 60 * 60 * 1000

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun checkTrialStatus(context: Context): Boolean {
        val prefs = getPrefs(context)
        var firstRunTime = prefs.getLong(KEY_FIRST_RUN_TIME, 0L)

        // Eğer ilk açılış tarihi yoksa (0), şu anı kaydet
        if (firstRunTime == 0L) {
            firstRunTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_RUN_TIME, firstRunTime).apply()
        }

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - firstRunTime

        // Geçen süre 14 günden azsa TRUE (Deneme Devam Ediyor)
        // Fazlaysa FALSE (Deneme Bitti)
        return elapsedTime < TRIAL_DURATION_MS
    }

    fun getRemainingDays(context: Context): Long {
        val prefs = getPrefs(context)
        val firstRunTime = prefs.getLong(KEY_FIRST_RUN_TIME, System.currentTimeMillis())
        val elapsedTime = System.currentTimeMillis() - firstRunTime
        val remaining = TRIAL_DURATION_MS - elapsedTime
        return if (remaining > 0) remaining / (24 * 60 * 60 * 1000) else 0
    }
}