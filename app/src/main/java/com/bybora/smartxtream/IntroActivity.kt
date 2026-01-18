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

        // HATA BURADAYDI: Intro görüldüyse direkt 'AddProfileActivity'ye gidiyordu.
        // DÜZELTME: Artık 'MainActivity'ye gidecek. Kararı MainActivity verecek.
        if (isIntroSeen()) {
            startActivity(Intent(this, MainActivity::class.java)) // <-- DEĞİŞTİ
            finish()
            return
        }

        setContentView(R.layout.activity_intro)

        val btnStart = findViewById<Button>(R.id.btn_start)

        btnStart.setOnClickListener {
            saveIntroSeen()
            // BURASI DA DEĞİŞTİ: Butona basınca da MainActivity'ye gitmeli.
            startActivity(Intent(this, MainActivity::class.java)) // <-- DEĞİŞTİ
            finish() // Intro activity'yi kapatalım ki geri tuşuna basınca dönmesin
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