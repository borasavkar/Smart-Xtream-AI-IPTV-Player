package com.bybora.smartxtream.utils

import android.content.Context
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.database.UserPreference
import com.bybora.smartxtream.network.VodStream
import com.bybora.smartxtream.network.SeriesStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PreferenceManager {

    // Puanlama Kuralları
    const val SCORE_FAVORITE = 50.0       // Favoriye ekleyince 50 puan
    const val SCORE_WATCH_PER_MIN = 0.5   // Her dakika izleme için 0.5 puan

    suspend fun analyzeAndStore(context: Context, profileId: Int, metaData: MetaDataContainer, points: Double) {
        val db = AppDatabase.getInstance(context).userPreferenceDao()

        // 1. Türleri (Genre) Ayıkla
        val genres = splitAndClean(metaData.genre)
        saveTags(db, profileId, "GENRE", genres, points)

        // 2. Oyuncuları (Cast) Ayıkla
        val cast = splitAndClean(metaData.cast)
        saveTags(db, profileId, "CAST", cast, points)

        // 3. Yönetmeni Ayıkla
        val director = splitAndClean(metaData.director)
        saveTags(db, profileId, "DIRECTOR", director, points)
    }

    private suspend fun saveTags(dao: com.bybora.smartxtream.database.UserPreferenceDao, profileId: Int, type: String, tags: List<String>, points: Double) {
        tags.forEach { tag ->
            if (tag.length > 2) { // "A", "Ve" gibi saçma kelimeleri ele
                val exist = dao.getPreference(profileId, type, tag)
                if (exist != null) {
                    dao.addScore(exist.id, points, System.currentTimeMillis())
                } else {
                    dao.insert(UserPreference(profileId = profileId, type = type, keyword = tag, score = points))
                }
            }
        }
    }

    private fun splitAndClean(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        // Virgül, Tire veya '|' ile ayrılmış olabilir
        return raw.split(",", "|", "-").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // Veri taşımak için basit sınıf
    data class MetaDataContainer(val genre: String?, val cast: String?, val director: String?)
}