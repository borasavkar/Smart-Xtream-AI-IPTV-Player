package com.bybora.smartxtream.network

import com.squareup.moshi.Json

// --- GENEL YANITLAR ---
data class XtreamResponse(
    @Json(name = "user_info") val userInfo: UserInfo?,
    @Json(name = "server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String,
    val password: String,
    val message: String?,
    val auth: Int,
    val status: String?,
    @Json(name = "exp_date") val expiryDate: String?,
    @Json(name = "is_trial") val isTrial: String?
)

data class ServerInfo(
    val url: String,
    val port: String,
    val https_port: String,
    @Json(name = "server_protocol") val protocol: String,
    val timezone: String
)

// --- CANLI YAYIN ---
data class LiveStream(
    @Json(name = "stream_id") val streamId: Int,
    val name: String?,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "category_id") val categoryId: String?,
    val directSource: String? = null
)

// LiveCategory (Ortak)
data class LiveCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int? = 0
)

// --- FİLMLER (VOD) ---
data class VodStream(
    @Json(name = "stream_id") val streamId: Int,
    val name: String?,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "container_extension") val fileExtension: String?,

    // DÜZELTME: Veri tipi 'Any?' yapıldı (Sayı, String veya Null gelebilir)
    @Json(name = "rating") private val _rating: Any? = null,
    @Json(name = "rating_5based") private val _rating5: Any? = null,

    @Json(name = "added") val added: String? = ""
) {
    // Güvenli Dönüştürücü: Ne gelirse gelsin Double'a çevirir, hata vermez.
    val rating: Double
        get() {
            return when (_rating) {
                is Number -> _rating.toDouble()
                is String -> _rating.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
}

// Film Kategorileri
data class VodCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String
)

// Film Detayları
data class VodInfoResponse(
    val info: VodInfoData?,
    @Json(name = "movie_data") val movieData: VodMovieData?
)

data class VodInfoData(
    @Json(name = "movie_image") val image: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "plot") val plot: String?,
    @Json(name = "cast") val cast: String?,
    @Json(name = "director") val director: String?,
    @Json(name = "genre") val genre: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "rating") val rating: String?, // Detayda genelde String gelir
    @Json(name = "duration") val duration: String?,
    @Json(name = "youtube_trailer") val youtubeTrailer: String?
)

data class VodMovieData(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "container_extension") val extension: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "category_id") val categoryId: String?
)

// --- DİZİLER (SERIES) ---
data class SeriesStream(
    @Json(name = "series_id") val seriesId: Int,
    val name: String?,
    @Json(name = "stream_icon") val streamIcon: String?,
    val cover: String?,
    @Json(name = "category_id") val categoryId: String?,

    // DÜZELTME: Diziler için de aynı koruma
    @Json(name = "rating") private val _rating: Any? = null,
    @Json(name = "rating_5based") private val _rating5: Any? = null
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

// Dizi Detayları
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
    val backdrop_path: List<String>?
)

data class Season(
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String?
)

data class Episode(
    val id: String,
    val title: String?,
    @Json(name = "container_extension") val fileExtension: String?,
    val info: EpisodeInfo?,
    @Json(name = "season") val seasonNumber: Int? = 0,
    @Json(name = "episode_num") val episodeNumber: Int? = 0
)

data class EpisodeInfo(
    @Json(name = "movie_image") val cover: String?,
    val duration: String?,
    val plot: String?
)

// --- EPG (YAYIN AKIŞI) ---
data class EpgResponse(
    @Json(name = "epg_listings") val listings: List<EpgListing>
)

data class EpgListing(
    val id: String,
    @Json(name = "epg_id") val epgId: String,
    val title: String,
    val start: String,
    val end: String,
    val description: String?
)

// --- UI YARDIMCI MODELLER ---
data class ChannelWithEpg(
    val channel: LiveStream,
    var epgNow: EpgListing?
)