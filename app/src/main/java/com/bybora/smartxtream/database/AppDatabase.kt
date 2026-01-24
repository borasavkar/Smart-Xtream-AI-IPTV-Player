package com.bybora.smartxtream.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Versiyonu 8 yaptık
@Database(entities = [Profile::class, Interaction::class, Favorite::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun interactionDao(): InteractionDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bora_iptv_database"
                )
                    .setJournalMode(JournalMode.TRUNCATE)
                    // --- GERİ EKLİYORUZ ---
                    // Neden? Çünkü eski sürümden gelen kullanıcıların uygulaması çökmesin.
                    // Veritabanı yapısı değişince eski veriyi silip yenisini kurar.
                    .fallbackToDestructiveMigration(true)
                    // ----------------------
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}