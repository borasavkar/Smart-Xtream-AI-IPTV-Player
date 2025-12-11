package com.bybora.smartxtream.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        // URL parametrelerinden kullanıcı adını yakala
        val username = url.queryParameter("username")

        // SADECE "google_test" kullanıcısı için devreye gir
        if (username == "google_test") {
            val action = url.queryParameter("action")

            // Hangi veri istendiyse ona göre cevap hazırla
            val responseString = when (action) {
                // null ise genelde Login isteğidir (AddProfileActivity'deki authenticate metodu)
                null -> MockData.LOGIN_SUCCESS
                "get_live_categories" -> MockData.LIVE_CATEGORIES
                "get_live_streams" -> MockData.LIVE_STREAMS
                "get_vod_categories", "get_vod_streams",
                "get_series", "get_series_categories" -> MockData.EMPTY_LIST
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

        // Diğer tüm kullanıcılar için normal internete çık
        return chain.proceed(request)
    }
}