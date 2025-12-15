package com.bybora.smartxtream

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Eğer intro daha önce görüldüyse direkt geç ve Intro'yu kapat
        if (isIntroSeen()) {
            startActivity(Intent(this, AddProfileActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_intro)

        val btnStart = findViewById<Button>(R.id.btn_start)

        btnStart.setOnClickListener {
            saveIntroSeen()
            // Butona basınca geç ama Intro'yu kapatma (Geri dönüşe izin ver)
            startActivity(Intent(this, AddProfileActivity::class.java))
        }
    }

    private fun saveIntroSeen() {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit {
            putBoolean("IntroSeen", true)
        }
    }

    private fun isIntroSeen(): Boolean {
        return getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getBoolean("IntroSeen", false)
    }
}