plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.bybora.smartxtream"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bybora.smartxtream"
        minSdk = 24
        targetSdk = 36
        versionCode = 33
        versionName = "1.1.33"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Kodları karart ve küçült (AÇIK)
            isMinifyEnabled = true
            // Kullanılmayan kaynakları (resim/xml) sil (AÇIK)
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // --- BURAYI EKLE ---
            ndk {
                debugSymbolLevel = "FULL"
            }
            // ------------------
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // --- OkHttp ---
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.dnsoverhttps)

    // --- AndroidX Temel ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- Smart Xtream Kütüphaneleri ---

    // 1. Video Oynatıcı: Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)

    // 2. API İstemcisi: Retrofit
    implementation(libs.squareup.retrofit)

    // 3. JSON İşleyicisi: Moshi
    implementation(libs.squareup.retrofit.converter.moshi)
    implementation(libs.squareup.moshi.kotlin)

    // 4. Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 5. Veritabanı: Room (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 6. Resim Yükleme: Glide (KSP)
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    // 7. Google Play Ödeme
    implementation(libs.billing)
    // 8. Animation
    implementation(libs.lottie)
    // Firebase (En üste plugin bloğuna da ekleme yapman gerekebilir, aşağıya bak)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)

}