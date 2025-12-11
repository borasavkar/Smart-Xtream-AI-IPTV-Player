package com.bybora.smartxtream.network

object MockData {
    // 1. GİRİŞ BAŞARILI CEVABI
    val LOGIN_SUCCESS = """
        {
            "user_info": {
                "username": "google_test",
                "password": "password",
                "message": "Login Success",
                "auth": 1,
                "status": "Active",
                "exp_date": "1700000000",
                "is_trial": "0"
            },
            "server_info": {
                "url": "http://mock-server",
                "port": "80",
                "https_port": "443",
                "server_protocol": "http",
                "timezone": "Europe/Istanbul"
            }
        }
    """.trimIndent()

    // 2. KATEGORİ LİSTESİ
    val LIVE_CATEGORIES = """
        [
            {
                "category_id": "1",
                "category_name": "Google Play Review Streams",
                "parent_id": 0
            }
        ]
    """.trimIndent()

    // 3. CANLI YAYIN LİSTESİ (Güncellenmiş, Güvenli ve Hızlı Linkler)
    // DİKKAT: "directSource" anahtarı senin Models.kt dosyanla birebir uyumlu yapıldı.
// 3. CANLI YAYIN LİSTESİ (Güncellenmiş Linkler)
    val LIVE_STREAMS = """
        [
            {
                "num": 1,
                "name": "Sintel (MP4 - Google Server)",
                "stream_type": "live",
                "stream_id": 1001,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/8/82/Sintel_poster.jpg",
                "category_id": "1",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            },
            {
                "num": 2,
                "name": "Big Buck Bunny (MP4 - Google Server)",
                "stream_type": "live",
                "stream_id": 1002,
                "stream_icon": "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
                "category_id": "1",
                "directSource": "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            },
            {
                "num": 3,
                "name": "Tears of Steel (4K HLS)",
                "stream_type": "live",
                "stream_id": 1003,
                "stream_icon": "https://mango.blender.org/wp-content/uploads/2012/09/poster_half_res.png",
                "category_id": "1",
                "directSource": "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
            }
        ]
    """.trimIndent()

    // Diğer boş listeler
    val EMPTY_LIST = "[]"
}