package com.bybora.smartxtream.network

object MockData {
    // 1. GİRİŞ BAŞARILI CEVABI
    val LOGIN_SUCCESS = """
        {
            "user_info": {
                "username": "google_test",
                "password": "123456",
                "message": "Login Success",
                "auth": 1,
                "status": "Active",
                "exp_date": "1700000000",
                "is_trial": "0"
            },
            "server_info": {
                "url": "http://google.com",
                "port": "80",
                "https_port": "443",
                "server_protocol": "http",
                "timezone": "Europe/Istanbul"
            }
        }
    """.trimIndent()

    // --- KATEGORİLER ---
    const val LIVE_CATEGORIES = """[{"category_id": "1", "category_name": "Test Channels", "parent_id": 0}]"""
    const val VOD_CATEGORIES = """[{"category_id": "2", "category_name": "Test Movies", "parent_id": 0}]"""
    const val SERIES_CATEGORIES = """[{"category_id": "3", "category_name": "Test Series", "parent_id": 0}]"""

    // --- 1. CANLI YAYINLAR (LIVE) ---
    // Big Buck Bunny
    val LIVE_STREAMS = """
        [
            {
                "num": 1,
                "name": "Big Buck Bunny 24/7 TV",
                "stream_type": "live",
                "stream_id": 1001,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
                "category_id": "1",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            }
        ]
    """.trimIndent()

    // --- 2. FİLMLER (VOD) ---
    // Sintel (Senin bulduğun link)
    val VOD_STREAMS = """
        [
            {
                "num": 1,
                "name": "Sintel (Movie)",
                "stream_type": "movie",
                "stream_id": 2001,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/f/f3/Sintel_Poster_Paintover_clean.jpg",
                "category_id": "2",
                "container_extension": "mp4",
                "rating": 8.5,
                "added": "1699999999",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            }
        ]
    """.trimIndent()

    // --- 3. DİZİLER (SERIES) ---
    // Tears of Steel (Senin bulduğun yeni link)
    val SERIES_STREAMS = """
        [
            {
                "num": 1,
                "name": "Tears of Steel (Series)",
                "series_id": 3001,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                "cover": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                "category_id": "3",
                "rating": 7.9,
                "last_modified": "1699999999"
            }
        ]
    """.trimIndent()

    // --- 4. DİZİ DETAYI ---
    val SERIES_INFO = """
        {
            "seasons": [{"season_number": 1, "name": "Season 1"}],
            "info": {
                "name": "Tears of Steel",
                "cover": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                "plot": "In a dystopian future, a group of soldiers and scientists fights to save the world.",
                "genre": "Sci-Fi",
                "releaseDate": "2012",
                "rating": "7.9"
            },
            "episodes": {
                "1": [
                    {
                        "id": "300101",
                        "episode_num": 1,
                        "title": "Chapter 1: The Beginning",
                        "container_extension": "mp4",
                        "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        "info": {
                            "movie_image": "https://upload.wikimedia.org/wikipedia/commons/7/70/Tos-poster.png",
                            "plot": "First episode of the test series.",
                            "duration": "12:00"
                        }
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
                "plot": "A lonely young woman, Sintel, helps and befriends a dragon, whom she calls Scales.",
                "cast": "Halina Reijn, Thom Hoffman",
                "director": "Colin Levy",
                "genre": "Animation, Fantasy",
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
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            }
        }
    """.trimIndent()

    //val EMPTY_LIST = "[]"
    const val EMPTY_LIST = "[]"
}