package com.bybora.smartxtream.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Moshi JSON Dönüştürücü
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // GÜÇLENDİRİLMİŞ İSTEMCİ (120 Saniye Timeout)
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS) // Bağlantı kurma süresi
        .readTimeout(120, TimeUnit.SECONDS)    // Veri okuma süresi (Büyük listeler için kritik)
        .writeTimeout(120, TimeUnit.SECONDS)   // Veri yazma süresi
        .retryOnConnectionFailure(true)        // Koparsa tekrar dene
        .build()

    // Servis Oluşturucu
    fun createService(baseUrl: String): ApiService {
        // URL sonuna "/" ekleme kontrolü
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient()) // Hatalı JSON'ları tolere et
            .build()

        return retrofit.create(ApiService::class.java)
    }
}