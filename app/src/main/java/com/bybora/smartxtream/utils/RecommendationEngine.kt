package com.bybora.smartxtream.utils

import com.bybora.smartxtream.database.Favorite
import com.bybora.smartxtream.database.Interaction
import com.bybora.smartxtream.database.UserPreference // 👈 EKSİK OLAN IMPORT EKLENDİ
import com.bybora.smartxtream.network.SeriesStream
import com.bybora.smartxtream.network.VodStream
import java.util.Locale

object RecommendationEngine {

    private val ADULT_KEYWORDS = listOf("adult", "xxx", "porn", "+18", "erotic", "sex", "18+", "yetiskin")
    private val STOP_WORDS = setOf("the", "a", "an", "ve", "veya", "ile", "bir", "film", "dizi", "izle", "tr", "eng", "dublaj", "altyazı", "hd", "4k", "part", "bolum")

    /**
     * FİLM ÖNERİ ALGORİTMASI (Favori + Geçmiş + YAPAY ZEKA)
     */
    fun recommendMovies(
        allMovies: List<VodStream>,
        userHistory: List<Interaction>,
        userFavorites: List<Favorite>,
        userPreferences: List<UserPreference>, // 👈 YAPAY ZEKA VERİSİ
        topCategoryId: String?,
        excludedIds: Set<Int>,
        limit: Int = 15
    ): List<VodStream> {

        val candidates = allMovies.filter { movie ->
            if (movie.streamId in excludedIds) return@filter false
            if (userHistory.any { it.streamId == movie.streamId && it.streamType == "vod" }) return@filter false
            if (userFavorites.any { it.streamId == movie.streamId && it.streamType == "vod" }) return@filter false
            !isAdultContent(movie.name)
        }

        val historyKeywords = extractKeywordsFromHistory(userHistory, "vod")
        val favoriteKeywords = extractKeywordsFromFavorites(userFavorites, "vod")
        val allInterestKeywords = historyKeywords + favoriteKeywords

        val scoredMovies = candidates.map { movie ->
            var score = 0.0
            val movieName = movie.name?.lowercase() ?: ""

            // A. Kategori Uyumu
            if (movie.categoryId == topCategoryId) score += 40.0

            // B. Kelime Benzerliği
            val movieKeywords = tokenize(movie.name)
            val matchCount = movieKeywords.count { it in allInterestKeywords }
            score += (matchCount * 12.0)

            // C. Favori Kategorisi Bonusu
            if (userFavorites.any { it.categoryId == movie.categoryId }) {
                score += 20.0
            }

            // D. Kalite ve Yenilik
            score += (movie.rating) * 5.0
            val addedTime = movie.added?.toLongOrNull() ?: 0L
            if (addedTime > (System.currentTimeMillis() / 1000 - 2592000)) score += 20.0

            // E. YAPAY ZEKA (KİŞİSEL ZEVKLER) ENTEGRASYONU 🧠
            userPreferences.forEach { pref ->
                val keyword = pref.keyword.lowercase()
                if (movieName.contains(keyword)) {
                    score += (pref.score * 0.8)
                }
            }

            Pair(movie, score)
        }

        return scoredMovies.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /**
     * DİZİ ÖNERİ ALGORİTMASI (Favori + Geçmiş + YAPAY ZEKA)
     */
    fun recommendSeries(
        allSeries: List<SeriesStream>,
        userHistory: List<Interaction>,
        userFavorites: List<Favorite>,
        userPreferences: List<UserPreference>, // 👈 DİZİLER İÇİN DE EKLENDİ
        topCategoryId: String?,
        excludedIds: Set<Int>,
        limit: Int = 10
    ): List<SeriesStream> {

        val candidates = allSeries.filter { series ->
            if (series.seriesId in excludedIds) return@filter false
            if (isAdultContent(series.name)) return@filter false
            if (userFavorites.any { it.streamId == series.seriesId && it.streamType == "series" }) return@filter false
            true
        }

        val historyKeywords = extractKeywordsFromHistory(userHistory, "series")
        val favoriteKeywords = extractKeywordsFromFavorites(userFavorites, "series")
        val allInterestKeywords = historyKeywords + favoriteKeywords

        val scoredSeries = candidates.map { series ->
            var score = 0.0
            val seriesName = series.name?.lowercase() ?: ""

            if (series.categoryId == topCategoryId) score += 40.0

            val keywords = tokenize(series.name)
            score += (keywords.count { it in allInterestKeywords } * 12.0)

            if (userFavorites.any { it.categoryId == series.categoryId }) score += 20.0

            score += (series.rating) * 5.0

            // YAPAY ZEKA ENTEGRASYONU (Diziler için)
            userPreferences.forEach { pref ->
                val keyword = pref.keyword.lowercase()
                if (seriesName.contains(keyword)) {
                    score += (pref.score * 0.8)
                }
            }

            Pair(series, score)
        }

        return scoredSeries.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    // --- YARDIMCI METOTLAR ---

    fun isAdultContent(name: String?): Boolean {
        val safeName = name?.lowercase(Locale.ENGLISH) ?: ""
        return ADULT_KEYWORDS.any { safeName.contains(it) }
    }

    private fun extractKeywordsFromHistory(history: List<Interaction>, type: String): Set<String> {
        val keywords = mutableSetOf<String>()
        val cachedMoviesMap = ContentCache.cachedMovies.associateBy { it.streamId }
        val cachedSeriesMap = ContentCache.cachedSeries.associateBy { it.seriesId }

        history.filter { it.streamType == type }.forEach { interaction ->
            if (interaction.isFinished || interaction.durationSeconds > 300) {
                val name = if (type == "vod") cachedMoviesMap[interaction.streamId]?.name
                else cachedSeriesMap[interaction.streamId]?.name
                if (name != null) keywords.addAll(tokenize(name))
            }
        }
        return keywords
    }

    private fun extractKeywordsFromFavorites(favorites: List<Favorite>, type: String): Set<String> {
        val keywords = mutableSetOf<String>()
        favorites.filter { it.streamType == type }.forEach { fav ->
            keywords.addAll(tokenize(fav.name))
        }
        return keywords
    }

    private fun tokenize(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.lowercase(Locale.forLanguageTag("tr"))
            .replace(Regex("[^a-z0-9ğüşıöç ]"), " ")
            .split(" ")
            .filter { it.length > 2 && it !in STOP_WORDS && !ADULT_KEYWORDS.contains(it) }
    }
}