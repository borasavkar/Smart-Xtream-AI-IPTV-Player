package com.bybora.smartxtream.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object RetrofitClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // 1. CASUS LOGLAYICI (Durumu görmeye devam edelim)
    private val loggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d("API_LOG", "--> GİDEN İSTEK: ${request.url}")

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            Log.e("API_LOG", "<-- BAĞLANTI HATASI: ${e.message}")
            throw e
        }

        Log.d("API_LOG", "<-- GELEN CEVAP: ${response.code}")

        // Veriyi logla (Hata ayıklama için)
        try {
            val responseBody = response.peekBody(Long.MAX_VALUE).string()
            if (responseBody.length > 500) {
                Log.d("API_LOG", "<-- DATA (Kısmi): ${responseBody.substring(0, 500)}...")
            } else {
                Log.d("API_LOG", "<-- DATA: $responseBody")
            }
        } catch (e: Exception) {
            Log.e("API_LOG", "Data okunamadı: ${e.message}")
        }

        response
    }

    // 2. HEADER KORUMASI (VLC TAKLİDİ)
    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            // BURASI KRİTİK: VLC Media Player kimliği kullanıyoruz.
            // Çoğu sunucu ve ISP bu kimliğe güvenir ve engellemez.
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .header("Accept", "*/*") // Her şeyi kabul et
            .header("Connection", "Keep-Alive") // Bağlantıyı canlı tut
            .build()
        chain.proceed(request)
    }

    // GÜVENSİZ SSL (Sertifika bypass)
    private val unsafeTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, arrayOf(unsafeTrustManager), SecureRandom())
    }

    val okHttpClient = OkHttpClient.Builder()
        // Zaman aşımlarını biraz kısalttık, çok bekletmesin
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

        // PROTOKOL: HTTP 1.1 (Şaşmaz standart)
        .protocols(listOf(Protocol.HTTP_1_1))

        // BAĞLANTI: Standart
        .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
        .sslSocketFactory(sslContext.socketFactory, unsafeTrustManager)
        .hostnameVerifier { _, _ -> true }
        .retryOnConnectionFailure(true)

        .addInterceptor(loggingInterceptor) // Önce log
        .addInterceptor(headerInterceptor)  // Sonra VLC kimliği

        // Demo Modu
        .addInterceptor { chain ->
            val url = chain.request().url.toString()
            if (url.contains("username=google_test") || url.contains("/google_test/")) {
                MockInterceptor().intercept(chain)
            } else {
                chain.proceed(chain.request())
            }
        }
        .build()

    private var retrofit: Retrofit? = null

    fun createService(baseUrl: String): ApiService {
        var cleanUrl = baseUrl.trim()
        if (!cleanUrl.startsWith("http")) cleanUrl = "http://$cleanUrl"
        if (!cleanUrl.endsWith("/")) cleanUrl += "/"

        if (retrofit == null || retrofit?.baseUrl().toString() != cleanUrl) {
            retrofit = Retrofit.Builder()
                .baseUrl(cleanUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}