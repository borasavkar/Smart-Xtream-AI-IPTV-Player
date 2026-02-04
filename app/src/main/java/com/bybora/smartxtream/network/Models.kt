package com.bybora.smartxtream.network

import com.squareup.moshi.Json

// --- GENEL YANITLAR ---
data class XtreamResponse(
    @param:Json(name = "user_info") val userInfo: UserInfo?,
    @param:Json(name = "server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String?,
    val password: String?,
    val message: String?,
    // DÜZELTME: auth bazen "1" (String) bazen 1 (Int) gelir. Patlamaması için Any? yaptık.
    @param:Json(name = "auth") private val _auth: Any? = null,
    val status: String?,
    @param:Json(name = "exp_date") val expiryDate: String?,
    @param:Json(name = "is_trial") val isTrial: String?
) {
    // Güvenli Auth Kontrolü
    val auth: Int
        get() {
            return when (_auth) {
                is Number -> _auth.toInt()
                is String -> _auth.toIntOrNull() ?: 0
                else -> 0
            }
        }
}

data class ServerInfo(
    val url: String?,
    val port: String?,
    @param:Json(name = "https_port") val httpsport: String?,
    @param:Json(name = "server_protocol") val protocol: String?,
    val timezone: String?
)

// --- CANLI YAYIN ---
data class LiveStream(
    @param:Json(name = "stream_id") val streamId: Int,
    val name: String?,
    @param:Json(name = "stream_icon") val streamIcon: String?,
    @param:Json(name = "category_id") val categoryId: String?,
    val directSource: String? = null
)

// LiveCategory (Ortak)
data class LiveCategory(
    @param:Json(name = "category_id") val categoryId: String,
    @param:Json(name = "category_name") val categoryName: String,
    @param:Json(name = "parent_id") val parentId: Int? = 0
)

// --- FİLMLER (VOD) ---
data class VodStream(
    @param:Json(name = "stream_id") val streamId: Int,
    val name: String?,
    @param:Json(name = "stream_icon") val streamIcon: String?,
    @param:Json(name = "category_id") val categoryId: String?,
    @param:Json(name = "container_extension") val fileExtension: String?,
    @param:Json(name = "rating") private val _rating: Any? = null,
    @param:Json(name = "rating_5based") private val _rating5: Any? = null,
    @param:Json(name = "added") val added: String? = "",
    val directSource: String? = null
) {
    val rating: Double
        get() {
            return when (_rating) {
                is Number -> _rating.toDouble()
                is String -> _rating.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
}

data class VodCategory(
    @param:Json(name = "category_id") val categoryId: String,
    @param:Json(name = "category_name") val categoryName: String
)

data class VodInfoResponse(
    val info: VodInfoData?,
    @param:Json(name = "movie_data") val movieData: VodMovieData?
)

data class VodInfoData(
    @param:Json(name = "movie_image") val image: String?,
    @param:Json(name = "name") val name: String?,
    @param:Json(name = "plot") val plot: String?,
    @param:Json(name = "cast") val cast: String?,
    @param:Json(name = "director") val director: String?,
    @param:Json(name = "genre") val genre: String?,
    @param:Json(name = "release_date") val releaseDate: String?,
    @param:Json(name = "rating") val rating: String?,
    @param:Json(name = "duration") val duration: String?,
    @param:Json(name = "youtube_trailer") val youtubeTrailer: String?
)

data class VodMovieData(
    @param:Json(name = "stream_id") val streamId: Int,
    @param:Json(name = "container_extension") val extension: String?,
    @param:Json(name = "name") val name: String?,
    @param:Json(name = "category_id") val categoryId: String?,
    val directSource: String? = null
)

// --- DİZİLER (SERIES) ---
data class SeriesStream(
    @param:Json(name = "series_id") val seriesId: Int,
    val name: String?,
    @param:Json(name = "stream_icon") val streamIcon: String?,
    val cover: String?,
    @param:Json(name = "category_id") val categoryId: String?,
    @param:Json(name = "rating") private val _rating: Any? = null,
    @param:Json(name = "rating_5based") private val _rating5: Any? = null,
    @param:Json(name = "last_modified") val lastModified: String? = null
) {
    val rating: Double
        get() {
            return when (_rating) {
                is Number -> _rating.toDouble()
                is String -> _rating.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
}

data class SeriesInfoResponse(
    val seasons: List<Season>?,
    val info: SeriesInfoData?,
    val episodes: Map<String, List<Episode>>?
)

data class SeriesInfoData(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    @param:Json(name = "backdrop_path") val backdropPath: List<String>?
)

data class Season(
    @param:Json(name = "season_number") val seasonNumber: Int,
    val name: String?
)

data class Episode(
    val id: String,
    val title: String?,
    @param:Json(name = "container_extension") val fileExtension: String?,
    val info: EpisodeInfo?,
    @param:Json(name = "season") val seasonNumber: Int? = 0,
    @param:Json(name = "episode_num") val episodeNumber: Int? = 0,
    val directSource: String? = null
)

data class EpisodeInfo(
    @param:Json(name = "movie_image") val cover: String?,
    val duration: String?,
    val plot: String?
)

// --- EPG ---
data class EpgResponse(
    @param:Json(name = "epg_listings") val listings: List<EpgListing>
)

data class EpgListing(
    val id: String,
    @param:Json(name = "epg_id") val epgId: String,
    val title: String,
    val start: String,
    val end: String,
    val description: String?
)

// UI Helper
data class ChannelWithEpg(
    val channel: LiveStream,
    var epgNow: EpgListing?
)