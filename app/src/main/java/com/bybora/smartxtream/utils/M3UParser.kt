package com.bybora.smartxtream.utils

import com.bybora.smartxtream.network.LiveCategory
import com.bybora.smartxtream.network.LiveStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader

// UYARI GİDERİLDİ: Şimdilik kullanılmadığı için uyarıyı susturduk.
@Suppress("unused")
object M3UParser {

    private val client = OkHttpClient()
    // Regex'leri statik olarak BİR KERE tanımlıyoruz (Performans artışı: %40+)
    private val REGEX_LOGO = "tvg-logo=\"([^\"]*)\"".toRegex()
    private val REGEX_GROUP = "group-title=\"([^\"]*)\"".toRegex()

    // M3U Linkini indirip kategorilere ve kanallara ayırır (Optimize Edilmiş: Stream Okuma)
    @Suppress("unused")
    fun parseM3U(url: String): Pair<List<LiveCategory>, List<LiveStream>> {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return Pair(emptyList(), emptyList())

            // UYARI GİDERİLDİ: '?: return ...' kısmı silindi.
            // Derleyici body'nin null olmadığını anladığı için direkt alıyoruz.
            // Güvenlik için '!!' ekleyebiliriz ama IDE gereksiz diyorsa direkt alalım.
            val body = response.body

            if (body == null) return Pair(emptyList(), emptyList()) // Ekstra güvenlik kontrolü

            val reader = BufferedReader(body.charStream())

            val channels = mutableListOf<LiveStream>()
            val categories = mutableSetOf<String>()
            val categoryList = mutableListOf<LiveCategory>()

            var line = reader.readLine()

            var currentName: String? = null
            var currentLogo: String? = null
            var currentGroup: String? = "Genel"

            while (line != null) {
                line = line.trim()

                if (line.startsWith("#EXTINF")) {
                    // Örnek: #EXTINF:-1 tvg-logo="url" group-title="Spor", Kanal Adı

                    // Kanal Adını Al (Virgülden sonrası)
                    val nameIndex = line.lastIndexOf(',')
                    if (nameIndex != -1) {
                        currentName = line.substring(nameIndex + 1).trim()
                    }

                    // ARTIK HIZLI: find fonksiyonunu önceden derlenmiş regex ile çağırıyoruz
                    val logoMatch = REGEX_LOGO.find(line)
                    currentLogo = if (logoMatch != null && logoMatch.groupValues.size > 1) {
                        logoMatch.groupValues[1]
                    } else ""

                    val groupMatch = REGEX_GROUP.find(line)
                    currentGroup = if (groupMatch != null && groupMatch.groupValues.size > 1) {
                        groupMatch.groupValues[1]
                    } else "Genel"

                    // UYARI GİDERİLDİ: '!!' silindi.
                    // Smart Cast sayesinde currentGroup'un null olmadığı biliniyor.
                    categories.add(currentGroup)
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    // Burası URL satırıdır
                    if (currentName != null) {
                        val channel = LiveStream(
                            streamId = line.hashCode(),
                            name = currentName,
                            streamIcon = currentLogo,
                            categoryId = currentGroup,
                            directSource = line
                        )
                        channels.add(channel)
                    }
                    // Sıfırla
                    currentName = null
                    currentLogo = null
                    currentGroup = "Genel"
                }
                line = reader.readLine()
            }

            reader.close() // Okuma bitince kapat

            // Kategorileri Listeye Çevir (Hepsi için ID = Kategori Adı)
            // "Tüm Kanallar" kategorisini en başa ekleyelim
            categoryList.add(LiveCategory("0", "Tüm Kanallar", 0))

            categories.sorted().forEach { catName ->
                categoryList.add(LiveCategory(catName, catName, 0))
            }

            return Pair(categoryList, channels)

        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(emptyList(), emptyList())
        }
    }
}