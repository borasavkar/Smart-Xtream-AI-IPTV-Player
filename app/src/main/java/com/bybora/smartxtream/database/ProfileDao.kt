package com.bybora.smartxtream.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy // Bunu eklemeyi unutma
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    // DEĞİŞİKLİK BURADA: (onConflict = OnConflictStrategy.REPLACE) eklendi.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Update
    suspend fun updateProfile(profile: Profile)

    @Query("SELECT * FROM profiles ORDER BY profile_name ASC")
    fun getAllProfiles(): Flow<List<Profile>>

    @Suppress("unused")
    @Query("SELECT * FROM profiles")
    fun getAllProfilesSync(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Int): Profile?
}