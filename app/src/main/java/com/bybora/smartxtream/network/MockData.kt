package com.bybora.smartxtream.network

object MockData {
    // 1. GİRİŞ BAŞARILI CEVABI
    // DİKKAT: 'user_info' ve 'server_info' alanları Models.kt içindeki @Json annotasyonlarıyla birebir aynı olmalı.
    val LOGIN_SUCCESS = """
        {
            "user_info": {
                "username": "google_test",
                "password": "123456",
                "message": "Login Success",
                "auth": 1,
                "status": "Active",
                "exp_date": "1767225600", 
                "is_trial": "0",
                "active_cons": "0",
                "created_at": "1600000000",
                "max_connections": "1"
            },
            "server_info": {
                "url": "http://google.com",
                "port": "80",
                "https_port": "443",
                "server_protocol": "http",
                "rtmp_port": "8880",
                "timezone": "Europe/Istanbul",
                "timestamp_now": 1600000000,
                "time_now": "2024-01-01 12:00:00"
            }
        }
    """.trimIndent()

    // --- KATEGORİLER ---
    const val LIVE_CATEGORIES = """[{"category_id": "1", "category_name": "Demo Kanallar", "parent_id": 0}]"""
    const val VOD_CATEGORIES = """[{"category_id": "2", "category_name": "Demo Filmler", "parent_id": 0}]"""
    const val SERIES_CATEGORIES = """[{"category_id": "3", "category_name": "Demo Diziler", "parent_id": 0}]"""

    // --- 1. CANLI YAYINLAR (LIVE) ---
    // Models.kt içinde 'directSource' bekliyoruz, JSON'da 'direct_source' varsa mapper bazen kaçırabilir.
    // Garanti olsun diye Models yapına uygun key kullandım.
    val LIVE_STREAMS = """
        [
            {
                "num": 1,
                "name": "Big Buck Bunny (Canlı)",
                "stream_type": "live",
                "stream_id": 1001,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
                "epg_channel_id": "",
                "added": "1600000000",
                "category_id": "1",
                "custom_sid": "",
                "tv_archive": 0,
                "direct_source": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "tv_archive_duration": 0
            }
        ]
    """.trimIndent()

    // --- 2. FİLMLER (VOD) ---
    val VOD_STREAMS = """
        [
            {
                "num": 1,
                "name": "Sintel (Animasyon)",
                "stream_type": "movie",
                "stream_id": 2001,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/f/f3/Sintel_Poster_Paintover_clean.jpg",
                "rating": "7.5",
                "rating_5based": 3.8,
                "added": "1600000000",
                "category_id": "2",
                "container_extension": "mp4",
                "direct_source": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            }
        ]
    """.trimIndent()

    // --- 3. DİZİLER (SERIES) ---
    val SERIES_STREAMS = """
        [
            {
                "num": 1,
                "name": "Tears of Steel",
                "series_id": 3001,
                "cover": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                "plot": "Demo Series Plot",
                "cast": "Demo Cast",
                "director": "Demo Director",
                "genre": "Sci-Fi",
                "releaseDate": "2012-09-26",
                "last_modified": "1600000000",
                "rating": "8.0",
                "rating_5based": 4.0,
                "backdrop_path": [],
                "youtube_trailer": "",
                "episode_run_time": "12",
                "category_id": "3"
            }
        ]
    """.trimIndent()

    // --- 4. DİZİ DETAYI & BÖLÜMLERİ ---
    val SERIES_INFO = """
        {
            "seasons": [
                { "season_number": 1, "name": "Sezon 1" }
            ],
            "info": {
                "name": "Tears of Steel",
                "cover": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                "plot": "Sci-fi short film.",
                "genre": "Sci-Fi",
                "releaseDate": "2012",
                "rating": "8.0",
                "backdrop_path": []
            },
            "episodes": {
                "1": [
                    {
                        "id": "300101",
                        "episode_num": 1,
                        "title": "Bölüm 1",
                        "container_extension": "mp4",
                        "info": {
                            "movie_image": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                            "plot": "First episode.",
                            "duration": "12:00"
                        },
                        "direct_source": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                    }
                ]
            }
        }
    """.trimIndent()

    // --- 5. FİLM DETAYI ---
    val VOD_INFO = """
        {
            "info": {
                "movie_image": "https://upload.wikimedia.org/wikipedia/commons/f/f3/Sintel_Poster_Paintover_clean.jpg",
                "name": "Sintel",
                "plot": "Demo plot.",
                "cast": "Demo Cast",
                "director": "Demo Director",
                "genre": "Animation",
                "release_date": "2010-09-27",
                "rating": "7.5",
                "duration": "14:48",
                "youtube_trailer": ""
            },
            "movie_data": {
                "stream_id": 2001,
                "container_extension": "mp4",
                "name": "Sintel",
                "category_id": "2",
                "direct_source": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            }
        }
    """.trimIndent()
    // BURAYA EKLE:
    const val EMPTY_LIST = "[]"
}