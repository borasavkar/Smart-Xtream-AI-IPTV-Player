package com.bybora.smartxtream.utils

import com.bybora.smartxtream.database.Favorite
import com.bybora.smartxtream.database.Interaction
import com.bybora.smartxtream.network.SeriesStream
import com.bybora.smartxtream.network.VodStream
import java.util.Locale

object RecommendationEngine {

    private val ADULT_KEYWORDS = listOf("adult", "xxx", "porn", "+18", "erotic", "sex", "18+", "yetiskin")
    private val STOP_WORDS = setOf("the", "a", "an", "ve", "veya", "ile", "bir", "film", "dizi", "izle", "tr", "eng", "dublaj", "altyazı", "hd", "4k", "part", "bolum")

    /**
     * FİLM ÖNERİ ALGORİTMASI (Favori + Geçmiş + Puan)
     */
    fun recommendMovies(
        allMovies: List<VodStream>,
        userHistory: List<Interaction>,
        userFavorites: List<Favorite>, // --- YENİ PARAMETRE ---
        topCategoryId: String?,
        excludedIds: Set<Int>,
        limit: Int = 15
    ): List<VodStream> {

        val candidates = allMovies.filter { movie ->
            if (movie.streamId in excludedIds) return@filter false
            if (userHistory.any { it.streamId == movie.streamId && it.streamType == "vod" }) return@filter false
            // --- 3. YENİ: Zaten FAVORİLERDE varsa önerme ---
            if (userFavorites.any { it.streamId == movie.streamId && it.streamType == "vod" }) return@filter false
            !isAdultContent(movie.name)
        }

        // --- ANALİZ ---
        // 1. İzlenenlerden kelime çıkar
        val historyKeywords = extractKeywordsFromHistory(userHistory, "vod")
        // 2. Favorilerden kelime çıkar (YENİ GÜÇ)
        val favoriteKeywords = extractKeywordsFromFavorites(userFavorites, "vod")

        // Birleşik Havuz (Favorilerin etkisi x2 olsun diye ayrı tutabiliriz ama birleştirmek yeterli)
        val allInterestKeywords = historyKeywords + favoriteKeywords

        val scoredMovies = candidates.map { movie ->
            var score = 0.0

            // A. Kategori Uyumu (40 Puan)
            if (movie.categoryId == topCategoryId) score += 40.0

            // B. Kelime Benzerliği (İsimden Yakala)
            val movieKeywords = tokenize(movie.name)
            val matchCount = movieKeywords.count { it in allInterestKeywords }
            score += (matchCount * 12.0) // Kelime başı puanı artırdık

            // C. Favori Kategorisi Bonusu (YENİ)
            // Eğer bu film, favorilerimdeki bir filmle aynı kategorideyse +20 Puan
            if (userFavorites.any { it.categoryId == movie.categoryId }) {
                score += 20.0
            }

            // D. Kalite ve Yenilik
            score += (movie.rating) * 5.0
            val addedTime = movie.added?.toLongOrNull() ?: 0L
            if (addedTime > (System.currentTimeMillis() / 1000 - 2592000)) score += 20.0

            Pair(movie, score)
        }

        return scoredMovies.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /**
     * DİZİ ÖNERİ ALGORİTMASI
     */
    fun recommendSeries(
        allSeries: List<SeriesStream>,
        userHistory: List<Interaction>,
        userFavorites: List<Favorite>, // --- YENİ PARAMETRE ---
        topCategoryId: String?,
        excludedIds: Set<Int>,
        limit: Int = 10
    ): List<SeriesStream> {

        val candidates = allSeries.filter { series ->
            // 1. Zaten ekranda varsa önerme
            if (series.seriesId in excludedIds) return@filter false

            // 2. Adult ise önerme
            if (isAdultContent(series.name)) return@filter false

            // --- 3. YENİ: Zaten FAVORİLERDE varsa önerme ---
            if (userFavorites.any { it.streamId == series.seriesId && it.streamType == "series" }) return@filter false
            // ----------------------------------------------

            true
        }

        val historyKeywords = extractKeywordsFromHistory(userHistory, "series")
        val favoriteKeywords = extractKeywordsFromFavorites(userFavorites, "series")
        val allInterestKeywords = historyKeywords + favoriteKeywords

        val scoredSeries = candidates.map { series ->
            var score = 0.0

            if (series.categoryId == topCategoryId) score += 40.0

            val keywords = tokenize(series.name)
            score += (keywords.count { it in allInterestKeywords } * 12.0)

            if (userFavorites.any { it.categoryId == series.categoryId }) score += 20.0 // Favori Kategori Bonusu

            score += (series.rating) * 5.0

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

    // YENİ: Favorilerden kelime çıkarma
    private fun extractKeywordsFromFavorites(favorites: List<Favorite>, type: String): Set<String> {
        val keywords = mutableSetOf<String>()
        favorites.filter { it.streamType == type }.forEach { fav ->
            keywords.addAll(tokenize(fav.name))
        }
        return keywords
    }

    private fun tokenize(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()

        // HATA DÜZELTİLDİ: Locale.TURKISH yerine Locale.forLanguageTag("tr")
        return text.lowercase(Locale.forLanguageTag("tr"))
            .replace(Regex("[^a-z0-9ğüşıöç ]"), " ")
            .split(" ")
            .filter { it.length > 2 && it !in STOP_WORDS && !ADULT_KEYWORDS.contains(it) }
    }
}