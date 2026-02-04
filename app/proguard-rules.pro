# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- Smart Xtream AI IPTV Rules ---

# --- 1. AĞ MODELLERİNİ KORU ---
# Paket ismin doğru, bunu tutuyoruz.
-keep class com.bybora.smartxtream.network.** { *; }

# --- 2. KOTLIN METADATA KORUMASI (KRİTİK EKSİK BU!) ---
# Moshi'nin KotlinJsonAdapterFactory kullanabilmesi için bu ŞARTTIR.
# Bu olmazsa 'isMinifyEnabled = true' iken uygulama çalışmaz.
-keep class kotlin.Metadata { *; }

# --- 3. MOSHI & RETROFIT İÇ YAPISI ---
# Kütüphanelerin kendi içindeki isimlerin değişmesini engeller.
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# --- 4. ATTRIBUTES (Gerekli Bilgiler) ---
# JSON eşleştirmesi için imzaların ve annotasyonların kalması lazım.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# --- 5. ROOM & GLIDE (Mevcut Kuralların) ---
-keep class com.bybora.smartxtream.database.** { *; }
-keep class com.bumptech.glide.** { *; }
-keep public class * implements com.bumptech.glide.module.AppGlideModule
-keep public class * implements com.bumptech.glide.module.LibraryGlideModule
-dontwarn androidx.room.paging.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**