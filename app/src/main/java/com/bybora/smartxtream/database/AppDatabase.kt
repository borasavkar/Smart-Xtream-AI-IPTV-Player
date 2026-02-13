package com.bybora.smartxtream.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// DÜZELTME 1: version = 9 yapıldı ve UserPreference::class eklendi
@Database(entities = [Profile::class, Interaction::class, Favorite::class, UserPreference::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun interactionDao(): InteractionDao
    abstract fun favoriteDao(): FavoriteDao

    // DÜZELTME 2: DAO eklendi
    abstract fun userPreferenceDao(): UserPreferenceDao

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
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}