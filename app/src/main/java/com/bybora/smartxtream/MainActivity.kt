package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bybora.smartxtream.adapter.ChannelAdapter
import com.bybora.smartxtream.adapter.OnChannelClickListener
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.database.Profile
import com.bybora.smartxtream.network.ChannelWithEpg
import com.bybora.smartxtream.network.EpgListing
import com.bybora.smartxtream.network.LiveStream
import com.bybora.smartxtream.network.RetrofitClient
import com.bybora.smartxtream.network.VodStream
import com.bybora.smartxtream.network.SeriesStream
import com.bybora.smartxtream.utils.ContentCache
import com.bybora.smartxtream.utils.SettingsManager
import com.bybora.smartxtream.utils.TrialManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar
import android.util.Log

class MainActivity : BaseActivity(), OnChannelClickListener {

    private val db by lazy { AppDatabase.getInstance(this) }
    private val profileDao by lazy { db.profileDao() }
    private val favoriteDao by lazy { db.favoriteDao() }

    private val yearRegex = "(19|20)\\d{2}".toRegex()

    // UI
    private lateinit var buttonAddProfile: MaterialButton
    private lateinit var btnGlobalSettings: ImageButton
    private lateinit var btnFavorites: ImageButton
    private lateinit var textStatusProfileName: TextView
    private lateinit var textConnectionStatus: TextView
    private lateinit var textExpirationDate: TextView
    private lateinit var progressBar: ProgressBar
    // --- YENİ EKLENENLER ---
    private lateinit var textTrialCounter: TextView
    private lateinit var billingManager: com.bybora.smartxtream.utils.BillingManager
    // -----------------------

    // Kartlar
    private lateinit var cardTv: View
    private lateinit var cardFilms: View
    private lateinit var cardSeries: View

    // Listeler
    private lateinit var recyclerFavorites: RecyclerView
    private lateinit var titleFavorites: TextView
    private lateinit var recyclerMatches: RecyclerView
    private lateinit var titleMatches: TextView
    private lateinit var recyclerRecommendations: RecyclerView
    private lateinit var titleRecommendations: TextView
    private lateinit var recyclerLatest: RecyclerView
    private lateinit var titleLatest: TextView

    // Adapters
    private lateinit var favAdapter: ChannelAdapter
    private lateinit var matchAdapter: ChannelAdapter
    private lateinit var recAdapter: ChannelAdapter
    private lateinit var latestAdapter: ChannelAdapter

    private var activeProfile: Profile? = null
    private var allProfiles: List<Profile> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Önce Arayüzü Yükle (ki Loading gösterebilelim)
        setContentView(R.layout.activity_main)
        initViews()

        // 2. Yükleniyor işaretini aç
        progressBar.visibility = View.VISIBLE
        // --- YENİ EKLENEN: Deneme Sayacı Başlat ---
        setupTrialCounter()

        // 3. Lisans ve Deneme Kontrolünü Başlat
        checkLicenseAndStart()
    }
    // --- BU FONKSİYONU SINIFIN İÇİNE KOPYALA ---
    private fun setupTrialCounter() {
        // BillingManager'ı başlat
        billingManager = com.bybora.smartxtream.utils.BillingManager(this)
        billingManager.startConnection()

        lifecycleScope.launch {
            // Google Play'den Premium durumunu dinle
            billingManager.isPremium.collect { isPremium ->
                if (isPremium) {
                    // Kullanıcı SATIN ALMIŞSA (Aylık/Yıllık/Ömür Boyu) -> GİZLE
                    textTrialCounter.visibility = View.GONE
                } else {
                    // Kullanıcı PREMİUM DEĞİLSE -> TrialManager'a sor
                    TrialManager.checkTrialOnServer(this@MainActivity, object : TrialManager.TrialCheckListener {
                        override fun onCheckResult(isActive: Boolean, message: String) {
                            if (isActive) {
                                // Deneme devam ediyorsa göster (Örn: "Deneme: 5 Gün Kaldı")
                                textTrialCounter.text = message
                                textTrialCounter.visibility = View.VISIBLE
                            } else {
                                // Süre bittiyse gizle (Zaten giriş yapamayacak)
                                textTrialCounter.visibility = View.GONE
                            }
                        }
                    })
                }
            }
        }
    }
    // -------------------------------------------

    private fun checkLicenseAndStart() {
        // A. Premium Kontrolü (Hızlı, Yerel)
        val isPremium = SettingsManager.isPremiumUser(this)

        if (isPremium) {
            // Premium kullanıcı ise bekletmeden başlat
            initAppContent()
        } else {
            // B. Deneme Kontrolü (Sunucu Tabanlı, Asenkron)
            TrialManager.checkTrialOnServer(this, object : TrialManager.TrialCheckListener {
                override fun onCheckResult(isActive: Boolean, message: String) {
                    if (isActive) {
                        // Deneme süresi devam ediyor, başlat
                        initAppContent()
                    } else {
                        // Süre Bitmiş -> Abonelik Ekranına Yönlendir
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() // Mesaj TrialManager'dan (dil destekli) gelir
                        startActivity(Intent(this@MainActivity, SubscriptionActivity::class.java))
                        finish()
                    }
                }
            })
        }
    }

    // Lisans onayı alındıktan sonra çalışacak asıl kodlar
    private fun initAppContent() {
        setupRecyclers()
        setupDashboardCards()
        setupClickListeners()
        observeFavorites()
        observeProfiles()
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            profileDao.getAllProfiles().collectLatest { profiles ->
                allProfiles = profiles

                if (profiles.isEmpty()) {
                    activeProfile = null
                    ContentCache.clear()
                    clearBottomBar()
                    textStatusProfileName.setText(R.string.text_no_profile)
                    startActivity(Intent(this@MainActivity, AddProfileActivity::class.java))
                } else {
                    val savedId = SettingsManager.getSelectedProfileId(this@MainActivity)
                    val targetProfile = profiles.find { it.id == savedId } ?: profiles[0]

                    if (activeProfile?.id != targetProfile.id) {
                        selectProfile(targetProfile)
                    }
                }
            }
        }
    }

    private fun selectProfile(profile: Profile) {
        SettingsManager.saveSelectedProfileId(this, profile.id)
        activeProfile = profile
        textStatusProfileName.text = profile.profileName
        loadAllContent(profile)

        lifecycleScope.launch {
            try {
                // URL DÜZELTME (Çok Önemli): Retrofit sonu '/' ile bitmeyen URL'leri sevmez.
                var cleanUrl = profile.serverUrl.trim()
                if (!cleanUrl.endsWith("/")) {
                    cleanUrl += "/"
                }
                val apiService = RetrofitClient.createService(profile.serverUrl)
                val response = apiService.authenticate(profile.username, profile.password)
                if (response.isSuccessful && response.body()?.userInfo?.auth == 1) {
                    val expiry = response.body()?.userInfo?.expiryDate
                    textExpirationDate.text = if (expiry != null) formatTimestamp(expiry) else getString(R.string.status_unlimited)
                    textConnectionStatus.setText(R.string.status_connected)
                    textConnectionStatus.setTextColor(getColor(R.color.green_success))
                } else {
                    textConnectionStatus.setText(R.string.status_login_error)
                    textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                textConnectionStatus.setText(R.string.status_server_error)
                // HATA DETAYINI GÖRMEK İÇİN:
                val errorMsg = e.message ?: "Bilinmeyen Hata"
                Log.e("MainActivity", "Connection Error: $errorMsg")

                // Kullanıcıya hatanın sebebini gösterelim (Geçici olarak)
                textConnectionStatus.text = "Hata: $errorMsg"
                textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun deleteProfile(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_delete))
            .setMessage(getString(R.string.msg_confirm_delete))
            .setPositiveButton(getString(R.string.btn_yes)) { _, _ ->
                lifecycleScope.launch {
                    if (activeProfile?.id == profile.id) {
                        ContentCache.clear()
                        clearBottomBar()
                    }
                    profileDao.deleteProfile(profile)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun initViews() {
        buttonAddProfile = findViewById(R.id.button_add_profile)
        btnGlobalSettings = findViewById(R.id.btn_global_settings)
        btnFavorites = findViewById(R.id.btn_favorites)
        textStatusProfileName = findViewById(R.id.text_status_profile_name)
        textConnectionStatus = findViewById(R.id.text_connection_status)
        textExpirationDate = findViewById(R.id.text_expiration_date)
        progressBar = findViewById(R.id.home_loader)
        // --- YENİ EKLENEN ---
        textTrialCounter = findViewById(R.id.text_trial_counter)
        // --------------------

        cardTv = findViewById(R.id.card_tv)
        cardFilms = findViewById(R.id.card_films)
        cardSeries = findViewById(R.id.card_series)

        recyclerFavorites = findViewById(R.id.recycler_favorites_home)
        titleFavorites = findViewById(R.id.title_favorites)
        recyclerMatches = findViewById(R.id.recycler_matches)
        titleMatches = findViewById(R.id.title_matches)
        recyclerRecommendations = findViewById(R.id.recycler_recommendations)
        titleRecommendations = findViewById(R.id.title_recommendations)
        recyclerLatest = findViewById(R.id.recycler_latest)
        titleLatest = findViewById(R.id.title_latest)
    }

    private fun setupRecyclers() {
        favAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerFavorites.adapter = favAdapter

        matchAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerMatches.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerMatches.adapter = matchAdapter

        recAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerRecommendations.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerRecommendations.adapter = recAdapter

        latestAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerLatest.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerLatest.adapter = latestAdapter
    }

    private fun setupDashboardCards() {
        cardTv.findViewById<TextView>(R.id.card_title).setText(R.string.title_live)
        cardFilms.findViewById<TextView>(R.id.card_title).setText(R.string.title_movies)
        cardSeries.findViewById<TextView>(R.id.card_title).setText(R.string.title_series)

        val iconTv = cardTv.findViewById<ImageView>(R.id.card_icon)
        val iconFilms = cardFilms.findViewById<ImageView>(R.id.card_icon)
        val iconSeries = cardSeries.findViewById<ImageView>(R.id.card_icon)

        iconTv.setImageResource(R.drawable.ic_neon_live)
        iconFilms.setImageResource(R.drawable.ic_neon_movie_reel)
        iconSeries.setImageResource(R.drawable.ic_neon_series)

        iconTv.imageTintList = null
        iconFilms.imageTintList = null
        iconSeries.imageTintList = null
    }

    private fun setupClickListeners() {
        buttonAddProfile.setOnClickListener { showProfileSelectionDialog() }
        btnGlobalSettings.setOnClickListener { showGlobalSettingsDialog() }
        btnFavorites.setOnClickListener {
            if(activeProfile!=null) {
                val intent = Intent(this, FavoritesActivity::class.java)
                intent.putProfileExtras(activeProfile!!)
                startActivity(intent)
            } else {
                showProfileWarning()
            }
        }
        cardTv.setOnClickListener { openChannelList() }
        cardFilms.setOnClickListener { openFilmList() }
        cardSeries.setOnClickListener { openSeriesList() }
    }

    private fun showGlobalSettingsDialog() {
        val langOptions = arrayOf("Türkçe", "English", "Deutsch", "Français", "Russian", "Arabic","Español", "Português")
        val langCodes = arrayOf("tr", "en", "de", "fr", "ru", "ar","es", "pt")
        val audioCode = SettingsManager.getAudioLang(this)
        val subCode = SettingsManager.getSubtitleLang(this)
        val audioName = langCodes.indexOf(audioCode).let { if(it == -1) "Türkçe" else langOptions[it] }
        val subName = langCodes.indexOf(subCode).let { if(it == -1) "Türkçe" else langOptions[it] }

        val menuItems = arrayOf(
            "${getString(R.string.settings_default_audio)}: $audioName",
            "${getString(R.string.settings_default_subtitle)}: $subName",
            getString(R.string.menu_refresh)
        )

        AlertDialog.Builder(this).setTitle(getString(R.string.settings_title)).setItems(menuItems) { _, which ->
            when (which) {
                0 -> showSelectionDialog(getString(R.string.settings_audio_lang), langOptions) { idx -> SettingsManager.setAudioLang(this, langCodes[idx]) }
                1 -> showSelectionDialog(getString(R.string.settings_subtitle_lang), langOptions) { idx -> SettingsManager.setSubtitleLang(this, langCodes[idx]) }
                2 -> {
                    ContentCache.clear()
                    if(activeProfile!=null) loadAllContent(activeProfile!!)
                    Toast.makeText(this, getString(R.string.msg_refreshing), Toast.LENGTH_SHORT).show()
                }
            }
        }.setPositiveButton(getString(R.string.btn_ok), null).show()
    }

    private fun showSelectionDialog(title: String, items: Array<String>, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                onSelected(which)
                Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                showGlobalSettingsDialog()
            }
            .show()
    }

    private fun observeFavorites() {
        lifecycleScope.launch {
            favoriteDao.getAllFavorites().collectLatest { favList ->
                val mappedFavs = withContext(Dispatchers.Default) {
                    val safeFavList = favList.filter { isSafeContent(it.name) }
                    safeFavList.map { fav ->
                        val stream = LiveStream(fav.streamId, fav.name, fav.image, fav.categoryId)
                        val typeLabel = when(fav.streamType) {
                            "vod" -> getString(R.string.type_movie)
                            "series" -> getString(R.string.type_series)
                            else -> getString(R.string.type_live)
                        }
                        ChannelWithEpg(stream, EpgListing("0", "0", typeLabel, "", "", ""))
                    }
                }

                if (mappedFavs.isNotEmpty()) {
                    favAdapter.submitList(mappedFavs)
                    titleFavorites.visibility = View.VISIBLE
                    recyclerFavorites.visibility = View.VISIBLE
                } else {
                    titleFavorites.visibility = View.GONE
                    recyclerFavorites.visibility = View.GONE
                    favAdapter.submitList(emptyList())
                }
            }
        }
    }

// MainActivity.kt içindeki loadAllContent fonksiyonunu bununla değiştir:

    private fun loadAllContent(profile: Profile) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // 1. VERİ İNDİRME (Aynı kalıyor)
                val (channels, movies, series, epgList) = withContext(Dispatchers.IO) {
                    var ch: List<LiveStream> = emptyList()
                    var mv: List<VodStream> = emptyList()
                    var sr: List<SeriesStream> = emptyList()
                    var ep: List<EpgListing>? = null

                    if (ContentCache.hasDataFor(profile.id)) {
                        ch = ContentCache.cachedChannels
                        mv = ContentCache.cachedMovies
                        sr = ContentCache.cachedSeries
                        ep = ContentCache.cachedEpg
                    } else {
                        val apiService = RetrofitClient.createService(profile.serverUrl)
                        try { val r = apiService.getLiveStreams(profile.username, profile.password); if (r.isSuccessful) ch = r.body() ?: emptyList() } catch (e: Exception) {}
                        try { val r = apiService.getVodStreams(profile.username, profile.password); if (r.isSuccessful) mv = r.body() ?: emptyList() } catch (e: Exception) {}
                        try { val r = apiService.getSeries(profile.username, profile.password); if (r.isSuccessful) sr = r.body() ?: emptyList() } catch (e: Exception) {}
                        try { val r = apiService.getEpgTable(profile.username, profile.password); if (r.isSuccessful) ep = r.body()?.listings } catch (e: Exception) {}
                        ContentCache.update(profile.id, ch, mv, sr, ep)
                    }
                    Quadruple(ch, mv, sr, ep)
                }

                // 2. İŞLEME VE AI ALGORİTMASI (Burası Geliştirildi)
                val (todayMatches, latestItems, recommendedItems) = withContext(Dispatchers.Default) {
                    val safeChannels = channels.filter { isSafeContent(it.name) }
                    val safeMovies = movies.filter { isSafeContent(it.name) }
                    val safeSeries = series.filter { isSafeContent(it.name) }

                    // A. MAÇLAR (Aynı)
                    val matches = if (!epgList.isNullOrEmpty()) {
                        safeChannels.mapNotNull { ch ->
                            val ep = epgList.find { it.epgId == ch.streamId.toString() }
                            if (ep != null) {
                                val t = ep.title.lowercase(); val isMatch = t.contains(" vs ") || t.contains(" - ") || t.contains("maçı")
                                if (isMatch) ChannelWithEpg(ch, ep) else null
                            } else null
                        }.take(20)
                    } else emptyList()

                    // B. SON EKLENENLER (Aynı)
                    val newMovies = safeMovies.sortedByDescending { it.streamId }.take(10)
                    val newSeries = safeSeries.sortedByDescending { it.seriesId }.take(5)
                    val latest = mutableListOf<ChannelWithEpg>()
                    newMovies.forEach { m -> latest.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", getString(R.string.type_movie), "", "", ""))) }
                    newSeries.forEach { s -> latest.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0", "0", getString(R.string.type_series), "", "", ""))) }

                    // C. AI TABANLI ÖNERİLER (YENİ ALGORİTMA)
                    val recs = mutableListOf<ChannelWithEpg>()

                    // Adım 1: Veritabanından Kullanıcının "Favori Türünü" öğren
                    // NOT: UI thread dışında (Default dispatcher) olduğumuz için direkt DAO çağırabiliriz
                    val topMovieCatList = db.interactionDao().getTopCategoryForType("vod")
                    val topSeriesCatList = db.interactionDao().getTopCategoryForType("series")

                    val topMovieCatId = if (topMovieCatList.isNotEmpty()) topMovieCatList[0].categoryId else null
                    val topSeriesCatId = if (topSeriesCatList.isNotEmpty()) topSeriesCatList[0].categoryId else null

                    // Adım 2: Buna göre içerik seç
                    val smartMovies = if (topMovieCatId != null) {
                        // Kullanıcı en çok bu kategoriyi izlemiş, buradan seç
                        safeMovies.filter { it.categoryId == topMovieCatId }.shuffled().take(10)
                    } else {
                        // Veri yoksa rastgele al
                        safeMovies.shuffled().take(5)
                    }

                    val smartSeries = if (topSeriesCatId != null) {
                        safeSeries.filter { it.categoryId == topSeriesCatId }.shuffled().take(5)
                    } else {
                        safeSeries.shuffled().take(3)
                    }

                    // Adım 3: Listeyi oluştur
                    smartMovies.forEach { m -> recs.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", getString(R.string.type_movie), "", "", ""))) }
                    smartSeries.forEach { s -> recs.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0", "0", getString(R.string.type_series), "", "", ""))) }

                    // Eğer AI yeterince veri bulamadıysa listeyi popülerlerle tamamla
                    if (recs.size < 5) {
                        val trendingMovies = safeMovies.take(10)
                        trendingMovies.forEach { m -> recs.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", getString(R.string.type_movie), "", "", ""))) }
                    }

                    // EPG ile birleştir (Görsel düzenleme için)
                    val finalRecs = if (recs.isNotEmpty()) {
                        val combined = combineChannelsAndEpg(recs.map { it.channel }, epgList)
                        combined.mapIndexed { index, item ->
                            if (recs[index].epgNow?.title == getString(R.string.type_movie) || recs[index].epgNow?.title == getString(R.string.type_series)) item.copy(epgNow = recs[index].epgNow) else item
                        }
                    } else emptyList()

                    Triple(matches, latest, finalRecs)
                }

                // UI GÜNCELLEME (Aynı)
                if (todayMatches.isNotEmpty()) {
                    matchAdapter.submitList(todayMatches)
                    titleMatches.visibility = View.VISIBLE; recyclerMatches.visibility = View.VISIBLE
                } else {
                    titleMatches.visibility = View.GONE; recyclerMatches.visibility = View.GONE
                }

                if (latestItems.isNotEmpty()) {
                    latestAdapter.submitList(latestItems)
                    titleLatest.visibility = View.VISIBLE; recyclerLatest.visibility = View.VISIBLE
                } else {
                    titleLatest.visibility = View.GONE; recyclerLatest.visibility = View.GONE
                }

                if (recommendedItems.isNotEmpty()) {
                    // Başlığı dinamik yapabiliriz: "Sizin İçin Seçtiklerimiz"
                    titleRecommendations.setText(R.string.header_recommendations)
                    recAdapter.submitList(recommendedItems)
                    titleRecommendations.visibility = View.VISIBLE; recyclerRecommendations.visibility = View.VISIBLE
                } else {
                    titleRecommendations.visibility = View.GONE; recyclerRecommendations.visibility = View.GONE
                }

                progressBar.visibility = View.GONE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                textConnectionStatus.setText(R.string.text_server_error)
            }
        }
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun isSafeContent(name: String?): Boolean {
        if (name == null) return false
        val lowerName = name.lowercase(Locale.forLanguageTag("tr"))
        val blockedKeywords = listOf("18+", "+18", "adult", "xxx", "porn", "sex", "erotic")
        return !blockedKeywords.any { lowerName.contains(it) }
    }

    private fun combineChannelsAndEpg(channels: List<LiveStream>, epgData: List<EpgListing>?): List<ChannelWithEpg> {
        val epgMap = epgData?.groupBy { it.epgId }
        val currentTime = System.currentTimeMillis()
        return channels.map { channel ->
            val channelWithEpg = ChannelWithEpg(channel = channel, epgNow = null)
            val channelEpgs = epgMap?.get(channel.streamId.toString())
            if (!channelEpgs.isNullOrEmpty()) {
                val currentEpg = channelEpgs.find { epg ->
                    val start = parseEpgTime(epg.start); val end = parseEpgTime(epg.end)
                    start != null && end != null && currentTime in start..end
                }
                channelWithEpg.epgNow = currentEpg
            }
            channelWithEpg
        }
    }

    private fun parseEpgTime(timeString: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(timeString)?.time
        } catch (e: Exception) { null }
    }

    override fun onChannelClick(channelWithEpg: ChannelWithEpg) {
        val type = channelWithEpg.epgNow?.title
        val id = channelWithEpg.channel.streamId

        if (type == getString(R.string.type_series)) {
            val intent = Intent(this, SeriesDetailActivity::class.java).apply { putProfileExtras(activeProfile!!); putExtra("EXTRA_SERIES_ID", id) }
            startActivity(intent)
        } else if (type == getString(R.string.type_movie)) {
            val intent = Intent(this, FilmDetailActivity::class.java).apply { putProfileExtras(activeProfile!!); putExtra("EXTRA_STREAM_ID", id) }
            startActivity(intent)
        } else {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putProfileExtras(activeProfile!!)
                putExtra("EXTRA_STREAM_ID", id)
                putExtra("EXTRA_STREAM_TYPE", "live")
                if (channelWithEpg.channel.directSource != null) putExtra("EXTRA_DIRECT_URL", channelWithEpg.channel.directSource)
                putExtra("EXTRA_STREAM_NAME", channelWithEpg.channel.name)
                putExtra("EXTRA_STREAM_ICON", channelWithEpg.channel.streamIcon)
            }
            startActivity(intent)
        }
    }

    private fun Intent.putProfileExtras(profile: Profile) {
        putExtra("EXTRA_SERVER_URL", profile.serverUrl)
        putExtra("EXTRA_USERNAME", profile.username)
        putExtra("EXTRA_PASSWORD", profile.password)
        putExtra("EXTRA_IS_M3U", profile.isM3u)
    }

    private fun showProfileSelectionDialog() {
        if (allProfiles.isEmpty()) { startActivity(Intent(this, AddProfileActivity::class.java)); return }
        val profileNames = allProfiles.map { it.profileName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_add_profile))
            .setItems(profileNames) { dialog, which -> selectProfile(allProfiles[which]); dialog.dismiss() }
            .setPositiveButton(getString(R.string.btn_add_new)) { dialog, _ -> startActivity(Intent(this, AddProfileActivity::class.java)); dialog.dismiss() }
            .setNeutralButton(getString(R.string.btn_manage)) { dialog, _ -> showManagementDialog() }
            .show()
    }

    private fun showManagementDialog() {
        val profileNames = allProfiles.map { it.profileName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_manage))
            .setItems(profileNames) { _, which -> showActionDialog(allProfiles[which]) }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showActionDialog(profile: Profile) {
        val options = arrayOf(getString(R.string.btn_edit), getString(R.string.btn_delete))
        AlertDialog.Builder(this)
            .setTitle(profile.profileName)
            .setItems(options) { _, which -> when (which) { 0 -> editProfile(profile); 1 -> deleteProfile(profile) } }
            .show()
    }

    private fun editProfile(profile: Profile) { val intent = Intent(this, AddProfileActivity::class.java).apply { putExtra("EXTRA_EDIT_ID", profile.id); putExtra("EXTRA_PROFILE_NAME", profile.profileName); putExtra("EXTRA_USERNAME", profile.username); putExtra("EXTRA_PASSWORD", profile.password); putExtra("EXTRA_SERVER_URL", profile.serverUrl) }; startActivity(intent) }

    private fun clearBottomBar() {
        textConnectionStatus.setText(R.string.status_not_connected)
        textConnectionStatus.setTextColor(getColor(android.R.color.darker_gray))
        textStatusProfileName.setText(R.string.text_no_profile)
        textExpirationDate.text = "N/A"
        recyclerRecommendations.visibility = View.GONE; titleRecommendations.visibility = View.GONE
    }

    private fun openChannelList() { activeProfile?.let { val intent = Intent(this, LiveCategoryActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openFilmList() { activeProfile?.let { val intent = Intent(this, FilmsActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openSeriesList() { activeProfile?.let { val intent = Intent(this, SeriesListActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun showProfileWarning() { Toast.makeText(this, getString(R.string.msg_select_profile), Toast.LENGTH_SHORT).show(); showProfileSelectionDialog() }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val expiryLong = timestamp.toLong() * 1000
            val date = Date(expiryLong)
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(date)
        } catch (e: Exception) {
            getString(R.string.date_invalid)
        }
    }

    private fun getYearFromName(name: String?): Int {
        if (name.isNullOrEmpty()) return 0
        val lastMatch = yearRegex.findAll(name).lastOrNull()
        return lastMatch?.value?.toIntOrNull() ?: 0
    }
}