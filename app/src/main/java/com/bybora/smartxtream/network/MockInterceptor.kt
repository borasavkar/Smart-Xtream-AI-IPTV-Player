package com.bybora.smartxtream.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // --- GÜVENLİK AĞI ---
        // Kullanıcı adını veya URL'yi kontrol et.
        // URL içinde "mock" geçiyorsa YA DA kullanıcı "google" ise yakala.
        val username = request.url.queryParameter("username")?.trim() ?: ""
        val isMockUrl = url.contains("mock", ignoreCase = true)
        val isGoogleUser = username.contains("google", ignoreCase = true)

        // HATA AYIKLAMA İÇİN LOG (Logcat'te "MOCK_SYSTEM" diye arat)
        Log.d("MOCK_SYSTEM", "İstek Geldi: $url")
        Log.d("MOCK_SYSTEM", "Kontrol: isMockUrl=$isMockUrl, isGoogleUser=$isGoogleUser")

        if (isMockUrl || isGoogleUser) {
            Log.d("MOCK_SYSTEM", ">>> Demo Modu DEVREYE GİRDİ! <<<")

            // A. VİDEO OYNATMA YÖNLENDİRMESİ
            // player_api.php çağırmıyorsa, bu bir medya dosyası (m3u8/mp4) isteğidir.
            if (!url.contains("player_api.php")) {
                var newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

                if (url.contains("Sintel") || url.contains("movie")) {
                    newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
                } else if (url.contains("Tears") || url.contains("series")) {
                    newUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                }

                Log.d("MOCK_SYSTEM", ">>> Video Yönlendiriliyor: $newUrl")
                // Video isteğini yeni URL ile tekrar oluştur
                val newRequest = request.newBuilder().url(newUrl).build()
                return chain.proceed(newRequest)
            }

            // B. API JSON YANITLARI
            val action = request.url.queryParameter("action")
            Log.d("MOCK_SYSTEM", ">>> API Aksiyonu: $action")

            val responseString = when (action) {
                null -> MockData.LOGIN_SUCCESS // Login isteğinde action parametresi yoktur
                "get_live_categories" -> MockData.LIVE_CATEGORIES
                "get_live_streams" -> MockData.LIVE_STREAMS
                "get_vod_categories" -> MockData.VOD_CATEGORIES
                "get_vod_streams" -> MockData.VOD_STREAMS
                "get_vod_info" -> MockData.VOD_INFO
                "get_series_categories" -> MockData.SERIES_CATEGORIES
                "get_series" -> MockData.SERIES_STREAMS
                "get_series_info" -> MockData.SERIES_INFO
                "get_simple_data_table" -> "{\"epg_listings\":[]}" // EPG Hatası almamak için boş JSON
                else -> "[]" // Bilinmeyen istekler için boş liste
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

        // Şartlar sağlanmadıysa GERÇEK internete çıkmaya çalış
        Log.d("MOCK_SYSTEM", ">>> Demo Modu Devre Dışı. Gerçek İnternete Gidiliyor...")
        return chain.proceed(request)
    }
}