package com.bybora.smartxtream.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.bybora.smartxtream.R // R dosyasını import etmeyi unutma
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object TrialManager {

    // 7 Gün (Milisaniye)
    private const val TRIAL_DURATION_MS = 7L * 24 * 60 * 60 * 1000


    interface TrialCheckListener {
        fun onCheckResult(isActive: Boolean, message: String)
    }

    @SuppressLint("HardwareIds")
    fun checkTrialOnServer(context: Context, listener: TrialCheckListener) {
        val db = FirebaseFirestore.getInstance()

        // Bu "unknown_device" sadece veritabanı ID'sidir, kullanıcı görmez.
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"

        val docRef = db.collection("trials").document(deviceId)

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // --- CİHAZ KAYITLI ---
                val firstRunTime = document.getLong("firstRunTime") ?: 0L
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - firstRunTime

                if (elapsedTime < TRIAL_DURATION_MS) {
                    // Deneme Devam Ediyor
                    val remainingDays = (TRIAL_DURATION_MS - elapsedTime) / (24 * 60 * 60 * 1000)
                    // ÇOK DİLLİ MESAJ:
                    val msg = context.getString(R.string.msg_trial_active, remainingDays)
                    listener.onCheckResult(true, msg)
                } else {
                    // Süre Bitmiş
                    listener.onCheckResult(false, context.getString(R.string.msg_trial_expired))
                }

            } else {
                // --- İLK KEZ ---
                val data = hashMapOf(
                    "firstRunTime" to System.currentTimeMillis(),
                    "deviceModel" to android.os.Build.MODEL
                )

                docRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        listener.onCheckResult(true, context.getString(R.string.msg_trial_started))
                    }
                    .addOnFailureListener {
                        listener.onCheckResult(true, context.getString(R.string.msg_conn_error_temp))
                    }
            }
        }.addOnFailureListener {
            listener.onCheckResult(true, context.getString(R.string.msg_server_error_offline))
        }
    }
}