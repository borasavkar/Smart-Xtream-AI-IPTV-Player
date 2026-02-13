package com.bybora.smartxtream.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {
    // Varsa puanını güncelle, yoksa ekle
    @Query("SELECT * FROM user_preferences WHERE profileId = :profileId AND type = :type AND keyword = :keyword LIMIT 1")
    suspend fun getPreference(profileId: Int, type: String, keyword: String): UserPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: UserPreference)

    @Query("UPDATE user_preferences SET score = score + :points, lastUpdated = :time WHERE id = :id")
    suspend fun addScore(id: Int, points: Double, time: Long)

    @Query("SELECT * FROM user_preferences WHERE profileId = :profileId ORDER BY score DESC")
    suspend fun getAllPreferences(profileId: Int): List<UserPreference>

    // Profili silerken bunları da sil
    @Query("DELETE FROM user_preferences WHERE profileId = :profileId")
    suspend fun clearPreferences(profileId: Int)
}