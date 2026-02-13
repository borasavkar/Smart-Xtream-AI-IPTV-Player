package com.bybora.smartxtream.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val type: String, // "GENRE" (Tür), "CAST" (Oyuncu), "DIRECTOR" (Yönetmen)
    val keyword: String, // Örn: "Aksiyon", "Tom Cruise", "Christopher Nolan"
    var score: Double, // Ne kadar seviyor? (Favori: +50, İzleme: +Süre)
    val lastUpdated: Long = System.currentTimeMillis()
)