package com.bybora.smartxtream

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Android 15+ için Uçtan Uca Mod
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // --- SİLİNEN KISIM ---
        // if (packageManager.hasSystemFeature...) { requestedOrientation = ... }
        // Bu bloğu sildik. Artık kodla yön dayatmıyoruz.

        // 2. Tam Ekran Ayarları (Bunlar kalabilir, dikeyde de tam ekran güzel durur)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        // Eğer dikey modda üstteki saat/pil görünsün istersen alttaki satırı silebilirsin.
        // Ama "Sinematik" bir hava için gizli kalması iyidir:
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}