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
import android.util.Log
import kotlinx.coroutines.async
import com.bybora.smartxtream.database.Favorite
import com.bybora.smartxtream.database.Interaction
import com.bybora.smartxtream.database.UserPreference
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : BaseActivity(), OnChannelClickListener {

    private val db by lazy { AppDatabase.getInstance(this) }
    private val profileDao by lazy { db.profileDao() }

    // UI
    private lateinit var buttonAddProfile: MaterialButton
    private lateinit var btnGlobalSettings: ImageButton
    private lateinit var btnFavorites: ImageButton
    private lateinit var textStatusProfileName: TextView
    private lateinit var textConnectionStatus: TextView
    private lateinit var textExpirationDate: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var textSubStatus: TextView
    private lateinit var textTrialCounter: TextView
    private lateinit var billingManager: com.bybora.smartxtream.utils.BillingManager

    // Kartlar
    private lateinit var cardTv: View
    private lateinit var cardFilms: View
    private lateinit var cardSeries: View

    // Listeler
    private lateinit var recyclerFavorites: RecyclerView
    private lateinit var titleFavorites: TextView
    private lateinit var favoritesAdapter: ChannelAdapter

    private lateinit var recyclerRecMovies: RecyclerView
    private lateinit var titleRecMovies: TextView
    private lateinit var recMoviesAdapter: ChannelAdapter

    private lateinit var recyclerRecSeries: RecyclerView
    private lateinit var titleRecSeries: TextView
    private lateinit var recSeriesAdapter: ChannelAdapter

    private lateinit var recyclerLatest: RecyclerView
    private lateinit var titleLatest: TextView
    private lateinit var latestAdapter: ChannelAdapter

    private var activeProfile: Profile? = null
    private var allProfiles: List<Profile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupFocusAnimations()

        progressBar.visibility = View.VISIBLE
        setupSubscriptionStatus()
        checkLicenseAndStart()
    }

    override fun onResume() {
        super.onResume()
        activeProfile?.let { profile ->
            lifecycleScope.launch(Dispatchers.IO) {
                val userFavs = try { db.favoriteDao().getAllFavoritesSync() } catch (e: Exception) { emptyList<Favorite>() }
                val favList = ArrayList<ChannelWithEpg>()

                userFavs.reversed().take(15).forEach { fav ->
                    val titleType = when (fav.streamType) {
                        "series" -> getString(R.string.type_series)
                        "vod", "movie" -> getString(R.string.type_movie)
                        else -> ""
                    }
                    favList.add(ChannelWithEpg(
                        LiveStream(fav.streamId, fav.name, fav.image, ""),
                        if (titleType.isNotEmpty()) EpgListing("0", "0", titleType, "", "", "") else null
                    ))
                }

                withContext(Dispatchers.Main) {
                    if (favList.isNotEmpty()) {
                        favoritesAdapter.submitList(favList)
                        titleFavorites.visibility = View.VISIBLE
                        recyclerFavorites.visibility = View.VISIBLE
                    } else {
                        titleFavorites.visibility = View.GONE
                        recyclerFavorites.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupFocusAnimations() {
        val cardFocusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
                v.elevation = 12f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.elevation = 0f
            }
        }

        val buttonFocusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
                v.elevation = 12f
                v.setBackgroundResource(R.drawable.bg_card_selector)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.elevation = 0f
                v.setBackgroundResource(android.R.color.transparent)
            }
        }

        btnFavorites.isFocusable = true
        btnGlobalSettings.isFocusable = true
        textStatusProfileName.isFocusable = true

        btnFavorites.onFocusChangeListener = buttonFocusListener
        btnGlobalSettings.onFocusChangeListener = buttonFocusListener
        textStatusProfileName.onFocusChangeListener = buttonFocusListener

        buttonAddProfile.isFocusable = true
        buttonAddProfile.onFocusChangeListener = cardFocusListener

        cardTv.isFocusable = true
        cardFilms.isFocusable = true
        cardSeries.isFocusable = true

        cardTv.onFocusChangeListener = cardFocusListener
        cardFilms.onFocusChangeListener = cardFocusListener
        cardSeries.onFocusChangeListener = cardFocusListener
    }

    private fun setupSubscriptionStatus() {
        if (!::billingManager.isInitialized) {
            billingManager = com.bybora.smartxtream.utils.BillingManager(this)
            billingManager.startConnection()
        }

        lifecycleScope.launch {
            billingManager.isPremium.collect { isPremium ->
                runOnUiThread {
                    if (isPremium) {
                        textSubStatus.text = getString(R.string.status_premium)
                        textSubStatus.setTextColor(android.graphics.Color.parseColor("#FF1744"))
                        textSubStatus.setShadowLayer(10f, 0f, 0f, android.graphics.Color.RED)
                        textSubStatus.visibility = View.VISIBLE
                    } else {
                        checkTrialStatus()
                    }
                }
            }
        }
    }

    private fun checkTrialStatus() {
        TrialManager.checkTrialOnServer(this, object : TrialManager.TrialCheckListener {
            override fun onCheckResult(isActive: Boolean, message: String) {
                runOnUiThread {
                    if (isActive) {
                        textSubStatus.text = message
                        textSubStatus.setTextColor(android.graphics.Color.parseColor("#FFEB3B"))
                        textSubStatus.setShadowLayer(0f, 0f, 0f, 0)
                        textSubStatus.visibility = View.VISIBLE
                    } else {
                        textSubStatus.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun checkLicenseAndStart() {
        val isPremium = SettingsManager.isPremiumUser(this)

        if (isPremium) {
            initAppContent()
        } else {
            TrialManager.checkTrialOnServer(this, object : TrialManager.TrialCheckListener {
                override fun onCheckResult(isActive: Boolean, message: String) {
                    if (isActive) {
                        initAppContent()
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@MainActivity, SubscriptionActivity::class.java))
                        finish()
                    }
                }
            })
        }
    }

    private fun initAppContent() {
        setupRecyclers()
        setupDashboardCards()
        setupClickListeners()
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
                    showAddProfileDialog()
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
                var cleanUrl = profile.serverUrl.trim()
                if (!cleanUrl.endsWith("/")) cleanUrl += "/"

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
                val errorMsg = e.message ?: getString(R.string.error_unknown)
                textConnectionStatus.text = getString(R.string.error_generic_prefix, errorMsg)
                textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun initViews() {
        buttonAddProfile = findViewById(R.id.button_add_profile)
        btnGlobalSettings = findViewById(R.id.btn_global_settings)
        btnFavorites = findViewById(R.id.btn_favorites)
        textStatusProfileName = findViewById(R.id.text_status_profile_name)
        textConnectionStatus = findViewById(R.id.text_connection_status)
        textExpirationDate = findViewById(R.id.text_expiration_date)
        progressBar = findViewById(R.id.home_loader)
        textTrialCounter = findViewById(R.id.text_trial_counter)
        textSubStatus = findViewById(R.id.text_sub_status)

        cardTv = findViewById(R.id.card_tv)
        cardFilms = findViewById(R.id.card_films)
        cardSeries = findViewById(R.id.card_series)

        recyclerFavorites = findViewById(R.id.recycler_favorites_home)
        titleFavorites = findViewById(R.id.title_favorites)

        recyclerLatest = findViewById(R.id.recycler_latest)
        titleLatest = findViewById(R.id.title_latest)
        recyclerRecMovies = findViewById(R.id.recycler_recommended_movies)
        titleRecMovies = findViewById(R.id.title_recommended_movies)
        recyclerRecSeries = findViewById(R.id.recycler_recommended_series)
        titleRecSeries = findViewById(R.id.title_recommended_series)
    }

    private fun setupRecyclers() {
        favoritesAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerFavorites.adapter = favoritesAdapter

        recMoviesAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerRecMovies.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerRecMovies.adapter = recMoviesAdapter

        recSeriesAdapter = ChannelAdapter(this, R.layout.item_movie_card)
        recyclerRecSeries.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerRecSeries.adapter = recSeriesAdapter

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
        textStatusProfileName.setOnClickListener { showAddProfileDialog() }
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

        val currentCacheSize = com.bybora.smartxtream.utils.SmartCacheManager.getCacheSize(this)

        val menuItems = arrayOf(
            "${getString(R.string.settings_default_audio)}: $audioName",
            "${getString(R.string.settings_default_subtitle)}: $subName",
            getString(R.string.menu_refresh),
            "Önbelleği Temizle ($currentCacheSize)"
        )

        showCustomGlassMenu(getString(R.string.settings_title), menuItems) { which ->
            when (which) {
                0 -> showSelectionDialog(getString(R.string.settings_audio_lang), langOptions) { idx -> SettingsManager.setAudioLang(this, langCodes[idx]) }
                1 -> showSelectionDialog(getString(R.string.settings_subtitle_lang), langOptions) { idx -> SettingsManager.setSubtitleLang(this, langCodes[idx]) }
                2 -> {
                    ContentCache.clear()
                    if(activeProfile!=null) loadAllContent(activeProfile!!)
                    Toast.makeText(this, getString(R.string.msg_refreshing), Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    Toast.makeText(this, getString(R.string.msg_clearing_cache), Toast.LENGTH_SHORT).show()
                    com.bybora.smartxtream.utils.SmartCacheManager.clearCache(this) {
                        Toast.makeText(this, getString(R.string.msg_cache_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showSelectionDialog(title: String, items: Array<String>, onSelected: (Int) -> Unit) {
        showCustomGlassMenu(title, items) { which ->
            onSelected(which)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            showGlobalSettingsDialog()
        }
    }

    // YENİ FONKSİYON: Ayarlar menüsünü Liquid Glass (Cam) tasarımında ve animasyonlu çizer
    private fun showCustomGlassMenu(title: String, items: Array<String>, onItemClick: (Int) -> Unit) {
        val context = this
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = android.widget.TextView(context).apply {
            text = title
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        container.addView(titleView)

        val scrollView = android.widget.ScrollView(context)
        val listContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 24)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(container)
            .setCancelable(true)
            .create()

        // Arka planı şeffaf ve %85 karanlık yapıyoruz
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)

        items.forEachIndexed { index, itemText ->
            val btn = android.widget.Button(context).apply {
                text = itemText
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                isFocusable = true
                isAllCaps = false // Metinlerin tamamının BÜYÜK HARF olmasını engeller
                setBackgroundResource(R.drawable.selector_glass_button_green) // Profil ekranındaki cam tasarımı!
                backgroundTintList = null
                this.layoutParams = layoutParams

                // Kumanda ile üstüne gelince Netflix tarzı büyüme efekti
                onFocusChangeListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
                        v.elevation = 12f
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                        v.elevation = 0f
                    }
                }

                setOnClickListener {
                    dialog.dismiss()
                    onItemClick(index)
                }
            }
            listContainer.addView(btn)
        }

        scrollView.addView(listContainer)
        container.addView(scrollView)

        dialog.show()
    }

    private fun loadAllContent(profile: Profile) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val dataPackage = withContext(Dispatchers.IO) {
                    var ch: List<LiveStream> = emptyList()
                    var mv: List<VodStream> = emptyList()
                    var sr: List<SeriesStream> = emptyList()
                    var ep: List<EpgListing>? = null

                    val userHist = try { db.interactionDao().getAllInteractions() } catch (e: Exception) { emptyList<Interaction>() }
                    val userFavs = try { db.favoriteDao().getAllFavoritesSync() } catch (e: Exception) { emptyList<Favorite>() }
                    val userPrefs = try { db.userPreferenceDao().getAllPreferences(profile.id) } catch (e: Exception) { emptyList<UserPreference>() }

                    if (ContentCache.hasDataFor(profile.id)) {
                        ch = ContentCache.cachedChannels
                        mv = ContentCache.cachedMovies
                        sr = ContentCache.cachedSeries
                        ep = ContentCache.cachedEpg
                    } else {
                        val apiService = RetrofitClient.createService(profile.serverUrl)
                        val chJob = async { try { apiService.getLiveStreams(profile.username, profile.password).body() ?: emptyList() } catch (e: Exception) { emptyList() } }
                        val mvJob = async { try { apiService.getVodStreams(profile.username, profile.password).body() ?: emptyList() } catch (e: Exception) { emptyList() } }
                        val srJob = async { try { apiService.getSeries(profile.username, profile.password).body() ?: emptyList() } catch (e: Exception) { emptyList() } }
                        val epJob = async { try { apiService.getEpgTable(profile.username, profile.password).body()?.listings } catch (e: Exception) { null } }

                        ch = chJob.await()
                        mv = mvJob.await()
                        sr = srJob.await()
                        ep = epJob.await()

                        ContentCache.update(profile.id, ch, mv, sr, ep)
                    }
                    DataPackage(ch, mv, sr, ep, userHist, userFavs, userPrefs)
                }

                val channels = dataPackage.channels
                val movies = dataPackage.movies
                val series = dataPackage.series
                val history = dataPackage.history
                val favorites = dataPackage.favorites
                val userPrefs = dataPackage.userPrefs

                val resultList = withContext(Dispatchers.Default) {
                    val favList = ArrayList<ChannelWithEpg>()
                    favorites.reversed().take(15).forEach { fav ->
                        val titleType = when (fav.streamType) {
                            "series" -> getString(R.string.type_series)
                            "vod", "movie" -> getString(R.string.type_movie)
                            else -> ""
                        }
                        favList.add(ChannelWithEpg(
                            LiveStream(fav.streamId, fav.name, fav.image, ""),
                            if (titleType.isNotEmpty()) EpgListing("0", "0", titleType, "", "", "") else null
                        ))
                    }

                    val safeMovies = movies.filter { !com.bybora.smartxtream.utils.RecommendationEngine.isAdultContent(it.name) }
                    val safeSeries = series.filter { !com.bybora.smartxtream.utils.RecommendationEngine.isAdultContent(it.name) }

                    val sortedMovies = safeMovies.sortedByDescending { it.added?.toLongOrNull() ?: 0L }.take(10)
                    val sortedSeries = safeSeries.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }.take(5)

                    val latest = ArrayList<ChannelWithEpg>()
                    sortedMovies.forEach { m -> latest.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", getString(R.string.type_movie), "", "", ""))) }
                    sortedSeries.forEach { s -> latest.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0", "0", getString(R.string.type_series), "", "", ""))) }

                    val excludedIds = (sortedMovies.map { it.streamId } + sortedSeries.map { it.seriesId }).toSet()
                    val topMovieCat = try { db.interactionDao().getTopCategoryForType("vod").firstOrNull()?.categoryId } catch (e: Exception) { null }
                    val topSeriesCat = try { db.interactionDao().getTopCategoryForType("series").firstOrNull()?.categoryId } catch (e: Exception) { null }

                    val smartMoviesRaw = com.bybora.smartxtream.utils.RecommendationEngine.recommendMovies(safeMovies, history, favorites, userPrefs, topMovieCat, excludedIds)
                    val smartSeriesRaw = com.bybora.smartxtream.utils.RecommendationEngine.recommendSeries(safeSeries, history, favorites, userPrefs, topSeriesCat, excludedIds)

                    val finalMovies = ArrayList<ChannelWithEpg>()
                    val finalSeries = ArrayList<ChannelWithEpg>()

                    smartMoviesRaw.forEach { m -> finalMovies.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), EpgListing("0", "0", getString(R.string.type_movie), "", "", ""))) }
                    smartSeriesRaw.forEach { s -> finalSeries.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), EpgListing("0", "0", getString(R.string.type_series), "", "", ""))) }

                    if (finalMovies.isEmpty()) safeMovies.shuffled().take(8).filter { it.streamId !in excludedIds }.forEach { m -> finalMovies.add(ChannelWithEpg(LiveStream(m.streamId, m.name, m.streamIcon, m.categoryId), null)) }
                    if (finalSeries.isEmpty()) safeSeries.shuffled().take(8).filter { it.seriesId !in excludedIds }.forEach { s -> finalSeries.add(ChannelWithEpg(LiveStream(s.seriesId, s.name, s.cover ?: s.streamIcon, s.categoryId), null)) }

                    listOf(favList, latest, finalMovies, finalSeries)
                }

                val favoriteItems = resultList[0]
                val latestItems = resultList[1]
                val recommendedMovies = resultList[2]
                val recommendedSeries = resultList[3]

                if (favoriteItems.isNotEmpty()) { favoritesAdapter.submitList(favoriteItems); titleFavorites.visibility = View.VISIBLE; recyclerFavorites.visibility = View.VISIBLE } else { titleFavorites.visibility = View.GONE; recyclerFavorites.visibility = View.GONE }
                if (latestItems.isNotEmpty()) { latestAdapter.submitList(latestItems); titleLatest.visibility = View.VISIBLE; recyclerLatest.visibility = View.VISIBLE } else { titleLatest.visibility = View.GONE; recyclerLatest.visibility = View.GONE }
                if (recommendedMovies.isNotEmpty()) { recMoviesAdapter.submitList(recommendedMovies); titleRecMovies.visibility = View.VISIBLE; recyclerRecMovies.visibility = View.VISIBLE } else { titleRecMovies.visibility = View.GONE; recyclerRecMovies.visibility = View.GONE }
                if (recommendedSeries.isNotEmpty()) { recSeriesAdapter.submitList(recommendedSeries); titleRecSeries.visibility = View.VISIBLE; recyclerRecSeries.visibility = View.VISIBLE } else { titleRecSeries.visibility = View.GONE; recyclerRecSeries.visibility = View.GONE }

                progressBar.visibility = View.GONE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                textConnectionStatus.setText(R.string.text_server_error)
                e.printStackTrace()
            }
        }
    }

    data class DataPackage(
        val channels: List<LiveStream>,
        val movies: List<VodStream>,
        val series: List<SeriesStream>,
        val epg: List<EpgListing>?,
        val history: List<Interaction>,
        val favorites: List<Favorite>,
        val userPrefs: List<UserPreference>
    )

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

    private fun clearBottomBar() {
        textConnectionStatus.setText(R.string.status_not_connected)
        textConnectionStatus.setTextColor(getColor(android.R.color.darker_gray))
        textStatusProfileName.setText(R.string.text_no_profile)
        textExpirationDate.text = "N/A"
        recyclerFavorites.visibility = View.GONE
        titleFavorites.visibility = View.GONE
        recyclerRecMovies.visibility = View.GONE
        titleRecMovies.visibility = View.GONE
        recyclerRecSeries.visibility = View.GONE
        titleRecSeries.visibility = View.GONE
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val date = Date(timestamp.toLong() * 1000)
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
            sdf.format(date)
        } catch (e: Exception) { getString(R.string.date_invalid) }
    }

    private fun showProfileSelectionDialog() {
        if (allProfiles.isEmpty()) {
            showAddProfileDialog()
            return
        }
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_profile_selection, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)
        dialog.show()

        val containerProfiles = dialogView.findViewById<android.widget.LinearLayout>(R.id.containerProfiles)
        val btnAddNew = dialogView.findViewById<android.widget.Button>(R.id.btnAddNew)
        val btnManage = dialogView.findViewById<android.widget.Button>(R.id.btnManage)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseDialog)

        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 16)

        allProfiles.forEach { profile ->
            val btnProfile = android.widget.Button(this)
            btnProfile.text = profile.profileName
            btnProfile.textSize = 16f
            btnProfile.setTextColor(android.graphics.Color.WHITE)
            btnProfile.isFocusable = true
            btnProfile.setBackgroundResource(R.drawable.selector_glass_button_green)
            btnProfile.backgroundTintList = null
            btnProfile.layoutParams = layoutParams

            val icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_heart_filled)
            icon?.setBounds(0, 0, 48, 48)
            icon?.setTint(android.graphics.Color.WHITE)
            btnProfile.compoundDrawablePadding = 24

            btnProfile.setOnClickListener {
                selectProfile(profile)
                dialog.dismiss()
            }
            containerProfiles.addView(btnProfile)
        }

        btnAddNew.setOnClickListener {
            dialog.dismiss()
            showAddProfileDialog()
        }
        btnManage.setOnClickListener {
            dialog.dismiss()
            showManagementDialog()
        }
        btnClose.setOnClickListener { dialog.dismiss() }
    }

    private fun showManagementDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_profile_management, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)
        dialog.show()

        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.containerProfiles)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseDialog)

        btnClose.setOnClickListener { dialog.dismiss() }

        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 16)

        allProfiles.forEach { profile ->
            val btnProfile = android.widget.Button(this)
            btnProfile.text = profile.profileName
            btnProfile.textSize = 16f
            btnProfile.setTextColor(android.graphics.Color.WHITE)
            btnProfile.isFocusable = true
            btnProfile.setBackgroundResource(R.drawable.selector_glass_button_green)
            btnProfile.backgroundTintList = null
            btnProfile.layoutParams = layoutParams

            val icon = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_menu_edit)
            icon?.setTint(android.graphics.Color.WHITE)
            icon?.setBounds(0, 0, 40, 40)
            btnProfile.setCompoundDrawables(null, null, icon, null)
            btnProfile.compoundDrawablePadding = 24

            btnProfile.setOnClickListener {
                dialog.dismiss()
                showEditProfileDialog(profile)
            }
            container.addView(btnProfile)
        }
    }

    private fun showEditProfileDialog(profileToEdit: com.bybora.smartxtream.database.Profile) {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_profile, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        dialog.show()

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val etProfileName = dialogView.findViewById<android.widget.EditText>(R.id.etProfileName)
        val etUsername = dialogView.findViewById<android.widget.EditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<android.widget.EditText>(R.id.etPassword)
        val etUrl = dialogView.findViewById<android.widget.EditText>(R.id.etUrl)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<android.widget.Button>(R.id.btnDelete)

        tvTitle.setText(R.string.title_edit_profile)
        btnSave.setText(R.string.btn_update)
        btnDelete.visibility = android.view.View.VISIBLE

        etProfileName.setText(profileToEdit.profileName)
        etUsername.setText(profileToEdit.username)
        etPassword.setText(profileToEdit.password)
        etUrl.setText(profileToEdit.serverUrl)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.title_delete_profile)
                .setMessage("${profileToEdit.profileName}: ${getString(R.string.msg_confirm_delete_profile)}")
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    lifecycleScope.launch {
                        val db = com.bybora.smartxtream.database.AppDatabase.getInstance(this@MainActivity)
                        db.profileDao().deleteProfile(profileToEdit)

                        if (activeProfile?.id == profileToEdit.id) {
                            com.bybora.smartxtream.utils.SettingsManager.saveSelectedProfileId(this@MainActivity, -1)
                            activeProfile = null
                            clearBottomBar()
                            textStatusProfileName.setText(R.string.text_no_profile)
                        }

                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.msg_profile_deleted), android.widget.Toast.LENGTH_SHORT).show()
                        showManagementDialog()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnSave.setOnClickListener {
            val name = etProfileName.text.toString().trim()
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val url = etUrl.text.toString().trim()

            if (name.isEmpty() || user.isEmpty() || pass.isEmpty() || url.isEmpty()) {
                android.widget.Toast.makeText(this, getString(R.string.msg_fill_all_fields), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    val updatedProfile = profileToEdit.copy(
                        profileName = name,
                        username = user,
                        password = pass,
                        serverUrl = url
                    )

                    val db = com.bybora.smartxtream.database.AppDatabase.getInstance(this@MainActivity)
                    db.profileDao().updateProfile(updatedProfile)

                    android.widget.Toast.makeText(this@MainActivity, getString(R.string.msg_profile_updated), android.widget.Toast.LENGTH_SHORT).show()

                    if (activeProfile?.id == updatedProfile.id) {
                        activeProfile = updatedProfile
                        textStatusProfileName.text = updatedProfile.profileName
                    }
                    showProfileSelectionDialog()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showAddProfileDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_profile, null)

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        dialog.show()

        val etProfileName = dialogView.findViewById<android.widget.EditText>(R.id.etProfileName)
        val etUsername = dialogView.findViewById<android.widget.EditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<android.widget.EditText>(R.id.etPassword)
        val etUrl = dialogView.findViewById<android.widget.EditText>(R.id.etUrl)
        val btnAdd = dialogView.findViewById<android.widget.Button>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val name = etProfileName.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val url = etUrl.text.toString().trim()

            if (name.isEmpty() || username.isEmpty() || password.isEmpty() || url.isEmpty()) {
                android.widget.Toast.makeText(this, getString(R.string.msg_fill_all_fields), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    val newProfile = com.bybora.smartxtream.database.Profile(
                        profileName = name,
                        username = username,
                        password = password,
                        serverUrl = url,
                        isM3u = false
                    )

                    val db = com.bybora.smartxtream.database.AppDatabase.getInstance(this@MainActivity)
                    val newId = db.profileDao().insertProfile(newProfile)
                    val savedProfile = newProfile.copy(id = newId.toInt())

                    com.bybora.smartxtream.utils.SettingsManager.saveSelectedProfileId(this@MainActivity, newId.toInt())

                    activeProfile = savedProfile
                    textStatusProfileName.text = savedProfile.profileName

                    android.widget.Toast.makeText(this@MainActivity, "${savedProfile.profileName} profiline geçildi", android.widget.Toast.LENGTH_SHORT).show()

                    loadAllContent(savedProfile)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun openChannelList() { activeProfile?.let { val intent = Intent(this, LiveCategoryActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openFilmList() { activeProfile?.let { val intent = Intent(this, FilmsActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun openSeriesList() { activeProfile?.let { val intent = Intent(this, SeriesListActivity::class.java); intent.putProfileExtras(it); startActivity(intent) } ?: showProfileWarning() }
    private fun showProfileWarning() { Toast.makeText(this, getString(R.string.msg_select_profile), Toast.LENGTH_SHORT).show(); showProfileSelectionDialog() }
}