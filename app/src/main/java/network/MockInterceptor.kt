package com.bybora.smartxtream.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // ---------------------------------------------------------
        // 1. DURUM: API İSTEKLERİ (Menüler, Listeler vb.)
        // ---------------------------------------------------------
        if (url.contains("player_api.php")) {
            val username = request.url.queryParameter("username")

            if (username == "google_test") {
                val action = request.url.queryParameter("action")

                val responseString = when (action) {
                    null -> MockData.LOGIN_SUCCESS

                    // Canlı
                    "get_live_categories" -> MockData.LIVE_CATEGORIES
                    "get_live_streams" -> MockData.LIVE_STREAMS

                    // Filmler
                    "get_vod_categories" -> MockData.VOD_CATEGORIES
                    "get_vod_streams" -> MockData.VOD_STREAMS
                    "get_vod_info" -> MockData.VOD_INFO

                    // Diziler
                    "get_series_categories" -> MockData.SERIES_CATEGORIES
                    "get_series" -> MockData.SERIES_STREAMS
                    "get_series_info" -> MockData.SERIES_INFO

                    else -> MockData.EMPTY_LIST
                }

                return Response.Builder()
                    .code(200)
                    .message("OK")
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .body(responseString.toResponseBody("application/json".toMediaTypeOrNull()))
                    .addHeader("content-type", "application/json")
                    .build()
            }
        }

        // ---------------------------------------------------------
        // 2. DURUM: VİDEO OYNATMA İSTEKLERİ (Stream)
        // ---------------------------------------------------------
        // Eğer URL "google_test" içeriyor ama "player_api.php" değilse, bu bir video isteğidir.
        else if (url.contains("/google_test/")) {

            // Gerçek bir video URL'si belirle
            var newUrl = ""

            if (url.contains("/movie/") || url.contains("Sintel")) {
                // Film isteği -> Sintel'e yönlendir
                newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            } else if (url.contains("/series/") || url.contains("Tears")) {
                // Dizi isteği -> Tears of Steel'e yönlendir
                newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
            } else {
                // Canlı yayın veya diğerleri -> Big Buck Bunny
                newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            }

            // İsteği yeni URL ile tekrar oluştur
            val newRequest = request.newBuilder()
                .url(newUrl)
                .build()

            // Yeni adrese git (ExoPlayer fark etmeden gerçek videoyu oynatacak)
            return chain.proceed(newRequest)
        }

        // Diğer tüm durumlar (Normal kullanıcılar)
        return chain.proceed(request)
    }
}