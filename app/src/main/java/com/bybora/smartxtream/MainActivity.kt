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
import com.bybora.smartxtream.utils.M3UParser
import com.bybora.smartxtream.utils.SettingsManager
import com.bybora.smartxtream.utils.TrialManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar

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

        val isPremium = SettingsManager.isPremiumUser(this)
        if (!isPremium) {
            val isTrialActive = TrialManager.checkTrialStatus(this)
            if (!isTrialActive) {
                Toast.makeText(this, "Deneme süreniz bitti. Lütfen abone olun.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SubscriptionActivity::class.java))
                finish()
                return
            } else {
                val daysLeft = TrialManager.getRemainingDays(this)
                if (daysLeft < 4) {
                    Toast.makeText(this, "Deneme Sürümü: Son $daysLeft gün!", Toast.LENGTH_LONG).show()
                }
            }
        }

        setContentView(R.layout.activity_main)
        initViews()
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
                    val isSavedProfileValid = profiles.any { it.id == savedId }

                    if (!isSavedProfileValid) {
                        selectProfile(profiles[0])
                    } else {
                        if (activeProfile == null || activeProfile?.id != savedId) {
                            val targetProfile = profiles.find { it.id == savedId }
                            if (targetProfile != null) {
                                selectProfile(targetProfile)
                            }
                        }
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

        if (profile.isM3u) {
            textExpirationDate.setText(R.string.status_unlimited)
            textConnectionStatus.setText(R.string.status_connected)
            textConnectionStatus.setTextColor(getColor(R.color.green_success))
        } else {
            lifecycleScope.launch {
                try {
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
                }
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
                        activeProfile = null
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
        cardFilms.setOnClickListener {
            if(activeProfile?.isM3u == false) openFilmList() else showM3uAlert()
        }
        cardSeries.setOnClickListener {
            if(activeProfile?.isM3u == false) openSeriesList() else showM3uAlert()
        }
    }

    private fun showM3uAlert() {
        AlertDialog.Builder(this)
            .setMessage("Film/Dizi sadece Xtream hesaplarında çalışır.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showGlobalSettingsDialog() {
        val langOptions = arrayOf("Türkçe", "English", "Deutsch", "Français", "Russian", "Arabic")
        val langCodes = arrayOf("tr", "en", "de", "fr", "ru", "ar")
        val audioCode = SettingsManager.getAudioLang(this)
        val subCode = SettingsManager.getSubtitleLang(this)

        val audioName = langCodes.indexOf(audioCode).let { if(it == -1) "Türkçe" else langOptions[it] }
        val subName = langCodes.indexOf(subCode).let { if(it == -1) "Türkçe" else langOptions[it] }

        val menuItems = arrayOf(
            "${getString(R.string.settings_default_audio)}: $audioName",
            "${getString(R.string.settings_default_subtitle)}: $subName"
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_title))
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> showSelectionDialog(getString(R.string.settings_audio_lang), langOptions) { idx -> SettingsManager.setAudioLang(this, langCodes[idx]) }
                    1 -> showSelectionDialog(getString(R.string.settings_subtitle_lang), langOptions) { idx -> SettingsManager.setSubtitleLang(this, langCodes[idx]) }
                }
            }
            .setPositiveButton(getString(R.string.btn_ok), null)
            .show()
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
                        val typeLabel = when(fav.streamType) { "vod"->"Film"; "series"->"Dizi"; else->"TV" }
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

    // --- ÖNEMLİ DEĞİŞİKLİK: VERİLERİ BAĞIMSIZ İNDİR ---
    private fun loadAllContent(profile: Profile) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // 1. VERİ İNDİRME
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
                        if (profile.isM3u) {
                            val result = M3UParser.parseM3U(profile.serverUrl)
                            ch = result.second
                        } else {
                            val apiService = RetrofitClient.createService(profile.serverUrl)

                            // HER BİRİNİ AYRI AYRI TRY-CATCH İÇİNE ALIYORUZ
                            // Böylece biri hata verse bile diğerleri yüklenir
                            try {
                                val resp = apiService.getLiveStreams(profile.username, profile.password)
                                if (resp.isSuccessful) ch = resp.body() ?: emptyList()
                            } catch (e: Exception) { e.printStackTrace() }

                            try {
                                val resp = apiService.getVodStreams(profile.username, profile.password)
                                if (resp.isSuccessful) mv = resp.body() ?: emptyList()
                            } catch (e: Exception) { e.printStackTrace() }

                            try {
                                val resp = apiService.getSeries(profile.username, profile.password)
                                if (resp.isSuccessful) sr = resp.body() ?: emptyList()
                            } catch (e: Exception) { e.printStackTrace() }

                            try {
                                val resp = apiService.getEpgTable(profile.username, profile.password)
                                if (resp.isSuccessful) ep = resp.body()?.listings
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        // Ne indirildiyse önbelleğe al
                        ContentCache.update(profile.id, ch, mv, sr, ep)
                    }
                    Quadruple(ch, mv, sr, ep)
                }

                // 2. İŞLEME (Default Thread)
                val (todayMatches, latestItems, recommendedItems) = withContext(Dispatchers.Default) {
                    val safeChannels = channels.filter { isSafeContent(it.name) }
                    val safeMovies = movies.filter { isSafeContent(it.name) }
                    val safeSeries = series.filter { isSafeContent(it.name) }

                    // MAÇLAR
                    val matches = if (!epgList.isNullOrEmpty()) {
                        val now = System.currentTimeMillis()
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        val endOfDay = calendar.timeInMillis

                        safeChannels.mapNotNull { channel ->
                            val channelEpgs = epgList.filter { it.epgId == channel.streamId.toString() }
                            val matchEvent = channelEpgs.find { epg ->
                                val start = parseEpgTime(epg.start) ?: 0L
                                val end = parseEpgTime(epg.end) ?: 0L
                                val isTimeValid = (end > now) && (start < endOfDay)
                                if (!isTimeValid) return@find false
                                val title = epg.title.lowercase(Locale.forLanguageTag("tr"))
                                (title.contains(" vs ") || title.contains(" - ") || title.contains(" v ") || title.contains("karşılaşması") || title.contains("maçı")) &&
                                        !(title.contains("özet") || title.contains("tekrar") || title.contains("highlight"))
                            }
                            if (matchEvent != null) ChannelWithEpg(channel, matchEvent) else null
                        }.take(20)
                    } else emptyList()

                    // SON EKLENENLER
                    val newMovies = safeMovies.sortedWith(compareByDescending<VodStream> { getYearFromName(it.name) }.thenByDescending { it.streamId }).take(10)
                    val newSeries = safeSeries.sortedWith(compareByDescending<SeriesStream> { getYearFromName(it.name) }.thenByDescending { it.seriesId }).take(5)

                    val latest = mutableListOf<ChannelWithEpg>()
                    newMovies.forEach { m -> latest.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", "Film", "", "", ""))) }
                    newSeries.forEach { s -> latest.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0", "0", "Dizi", "", "", ""))) }

                    // ÖNERİLER
                    val recs = mutableListOf<ChannelWithEpg>()
                    if (recs.isEmpty()) {
                        val trendingMovies = safeMovies.sortedWith(compareByDescending<VodStream> { getYearFromName(it.name) }.thenByDescending { it.streamId }).take(10)
                        val trendingSeries = safeSeries.sortedWith(compareByDescending<SeriesStream> { getYearFromName(it.name) }.thenByDescending { it.seriesId }).take(5)
                        trendingMovies.forEach { m -> recs.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", "Film", "", "", ""))) }
                        trendingSeries.forEach { s -> recs.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0", "0", "Dizi", "", "", ""))) }
                    }

                    val finalRecs = if (recs.isNotEmpty()) {
                        val combined = combineChannelsAndEpg(recs.map { it.channel }, epgList)
                        combined.mapIndexed { index, item ->
                            if (recs[index].epgNow?.title == "Film" || recs[index].epgNow?.title == "Dizi") item.copy(epgNow = recs[index].epgNow) else item
                        }
                    } else emptyList()

                    Triple(matches, latest, finalRecs)
                }

                // UI Güncelleme (Main Thread)
                if (todayMatches.isNotEmpty()) {
                    matchAdapter.submitList(todayMatches)
                    titleMatches.visibility = View.VISIBLE
                    recyclerMatches.visibility = View.VISIBLE
                } else {
                    titleMatches.visibility = View.GONE
                    recyclerMatches.visibility = View.GONE
                }

                if (latestItems.isNotEmpty()) {
                    latestAdapter.submitList(latestItems)
                    titleLatest.visibility = View.VISIBLE
                    recyclerLatest.visibility = View.VISIBLE
                } else {
                    titleLatest.visibility = View.GONE
                    recyclerLatest.visibility = View.GONE
                }

                if (recommendedItems.isNotEmpty()) {
                    titleRecommendations.setText(R.string.header_recommendations)
                    recAdapter.submitList(recommendedItems)
                    titleRecommendations.visibility = View.VISIBLE
                    recyclerRecommendations.visibility = View.VISIBLE
                } else {
                    titleRecommendations.visibility = View.GONE
                    recyclerRecommendations.visibility = View.GONE
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
                    val start = parseEpgTime(epg.start)
                    val end = parseEpgTime(epg.end)
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

        if (type == "Dizi") {
            val intent = Intent(this, SeriesDetailActivity::class.java).apply { putProfileExtras(activeProfile!!); putExtra("EXTRA_SERIES_ID", id) }
            startActivity(intent)
        } else if (type == "Film") {
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

    private fun showProfileSelectionDialog() { if (allProfiles.isEmpty()) { startActivity(Intent(this, AddProfileActivity::class.java)); return }; val profileNames = allProfiles.map { it.profileName }.toTypedArray(); AlertDialog.Builder(this).setTitle(getString(R.string.title_add_profile)).setItems(profileNames) { dialog, which -> selectProfile(allProfiles[which]); dialog.dismiss() }.setPositiveButton(getString(R.string.btn_add_new)) { dialog, _ -> startActivity(Intent(this, AddProfileActivity::class.java)); dialog.dismiss() }.setNeutralButton(getString(R.string.btn_manage)) { dialog, _ -> showManagementDialog() }.show() }
    private fun showManagementDialog() { val profileNames = allProfiles.map { it.profileName }.toTypedArray(); AlertDialog.Builder(this).setTitle(getString(R.string.btn_manage)).setItems(profileNames) { _, which -> showActionDialog(allProfiles[which]) }.setNegativeButton(getString(R.string.btn_cancel), null).show() }
    private fun showActionDialog(profile: Profile) { val options = arrayOf(getString(R.string.btn_edit), getString(R.string.btn_delete)); AlertDialog.Builder(this).setTitle(profile.profileName).setItems(options) { _, which -> when (which) { 0 -> editProfile(profile); 1 -> deleteProfile(profile) } }.show() }
    private fun editProfile(profile: Profile) { val intent = Intent(this, AddProfileActivity::class.java).apply { putExtra("EXTRA_EDIT_ID", profile.id); putExtra("EXTRA_PROFILE_NAME", profile.profileName); putExtra("EXTRA_USERNAME", profile.username); putExtra("EXTRA_PASSWORD", profile.password); putExtra("EXTRA_SERVER_URL", profile.serverUrl) }; startActivity(intent) }
    private fun clearBottomBar() { textConnectionStatus.setText(R.string.text_not_connected); textConnectionStatus.setTextColor(getColor(android.R.color.darker_gray)); textStatusProfileName.setText(R.string.text_no_profile); textExpirationDate.text = "N/A"; recyclerRecommendations.visibility = View.GONE; titleRecommendations.visibility = View.GONE }
    private fun openChannelList() { activeProfile?.let { val intent = Intent(this, LiveCategoryActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openFilmList() { activeProfile?.let { val intent = Intent(this, FilmsActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openSeriesList() { activeProfile?.let { val intent = Intent(this, SeriesListActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun showProfileWarning() { Toast.makeText(this, getString(R.string.msg_select_profile), Toast.LENGTH_SHORT).show(); showProfileSelectionDialog() }
    private fun formatTimestamp(timestamp: String): String { return try { val expiryLong = timestamp.toLong() * 1000; val date = Date(expiryLong); val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")); sdf.timeZone = TimeZone.getDefault(); sdf.format(date) } catch (e: Exception) { "Geçersiz Tarih" } }

    private fun getYearFromName(name: String?): Int {
        if (name.isNullOrEmpty()) return 0
        val lastMatch = yearRegex.findAll(name).lastOrNull()
        return lastMatch?.value?.toIntOrNull() ?: 0
    }
}