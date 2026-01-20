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

        // 1. KULLANICI ADI KONTROLÜ
        // URL'de "username=google_test" parametresi geçiyorsa, bu bizim test kullanıcımızdır.
        val username = request.url.queryParameter("username")

        if (username == "google_test") {

            // A. VİDEO OYNATMA İSTEKLERİ (mp4, m3u8 vb.)
            // URL içinde google_test var ama 'player_api.php' YOKSA bu bir video isteğidir.
            if (!url.contains("player_api.php")) {
                var newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

                if (url.contains("/movie/") || url.contains("Sintel")) {
                    newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
                } else if (url.contains("/series/") || url.contains("Tears")) {
                    newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                }

                // Gerçek video adresine yönlendir (ExoPlayer hatasız oynatır)
                val newRequest = request.newBuilder().url(newUrl).build()
                return chain.proceed(newRequest)
            }

            // B. API İSTEKLERİ (Menü, Login, Liste)
            val action = request.url.queryParameter("action")
            val responseString = when (action) {
                null -> MockData.LOGIN_SUCCESS // Login isteği
                "get_live_categories" -> MockData.LIVE_CATEGORIES
                "get_live_streams" -> MockData.LIVE_STREAMS
                "get_vod_categories" -> MockData.VOD_CATEGORIES
                "get_vod_streams" -> MockData.VOD_STREAMS
                "get_vod_info" -> MockData.VOD_INFO
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

        // Normal kullanıcılar için internete çık
        return chain.proceed(request)
    }
}