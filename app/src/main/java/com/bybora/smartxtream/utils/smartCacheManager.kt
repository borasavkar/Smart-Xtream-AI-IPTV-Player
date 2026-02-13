package com.bybora.smartxtream.utils

import android.content.Context
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object SmartCacheManager {

    // Cache boyutunu hesapla (MB cinsinden)
    fun getCacheSize(context: Context): String {
        return try {
            val cacheDir = context.cacheDir
            val sizeBytes = getDirSize(cacheDir)
            formatSize(sizeBytes)
        } catch (e: Exception) {
            "0 MB"
        }
    }

    // SADECE GEREKSİZLERİ SİL (Profiller ve Veritabanı GÜVENDE)
    fun clearCache(context: Context, onComplete: () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 1. Video Cache Klasörünü Sil (ExoPlayer)
                val videoCache = File(context.cacheDir, "media")
                if (videoCache.exists()) {
                    deleteDir(videoCache)
                }

                // 2. Glide (Resim) Cache Sil
                Glide.get(context).clearDiskCache()

                // 3. Genel Cache Temizliği (Veritabanı hariç)
                deleteDir(context.cacheDir)

                // 4. Bellek (RAM) Temizliği (Ana thread'de olmalı)
                withContext(Dispatchers.Main) {
                    Glide.get(context).clearMemory()
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    // Klasör boyutunu hesaplayan yardımcı fonksiyon
    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    // Dosyaları silen yardımcı fonksiyon (Database dosyalarına dokunmaz çünkü onlar cacheDir'de değil)
    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val children = dir.list() ?: return false
        for (i in children.indices) {
            val success = deleteDir(File(dir, children[i]))
            if (!success) {
                File(dir, children[i]).delete()
            }
        }
        return dir.delete()
    }

    private fun formatSize(size: Long): String {
        val mb = size.toDouble() / (1024 * 1024)
        return String.format("%.1f MB", mb)
    }
}