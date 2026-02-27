package com.bybora.smartxtream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.database.Favorite
import com.bybora.smartxtream.database.Interaction
import com.bybora.smartxtream.network.RetrofitClient
import com.bybora.smartxtream.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import androidx.activity.enableEdgeToEdge

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : BaseActivity() {

    // ============================================================
    // PLAYER & UI
    // ============================================================
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var trackSelector: DefaultTrackSelector? = null

    private lateinit var btnSettings: ImageButton
    private lateinit var btnSubtitle: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnFavoritePlayer: ImageButton
    private lateinit var btnNextEpisode: Button
    private lateinit var textNetworkSpeed: TextView
    private lateinit var textResolution: TextView
    private lateinit var textOverlayClock: TextView
    private var topControls: View? = null

    // ============================================================
    // STREAM DATA
    // ============================================================
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var streamId: Int = -1
    private var streamType: String? = "live"
    private var fileExtension: String = "mp4"
    private var categoryId: String? = "0"
    private var directUrl: String? = null
    private var nextEpisodeId: Int = -1
    private var streamName: String = ""
    private var streamIcon: String = ""
    private var episodeIdList: ArrayList<Int>? = null
    private var streamGenre: String = ""
    private var streamCast: String = ""
    private var streamDirector: String = ""

    // ============================================================
    // STATE
    // ============================================================
    private val db by lazy { AppDatabase.getInstance(this) }
    private var isFav = false
    private var startTime: Long = 0
    private var startPosition: Long = 0
    private var lastTotalRxBytes: Long = 0
    private var lastTimeStamp: Long = 0
    private var smoothedSpeed: Double = 0.0
    private val handler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockRunnable: Runnable

    /**
     * How many fallback attempts have been made for the current stream.
     * Each attempt tries a different protocol/extension combo.
     */
    private var fallbackAttempt = 0
    private val maxFallbackAttempts = 3

    /**
     * Device RAM in MB — read once and reused everywhere.
     */
    private var totalRamMB: Long = 0
    private var availableRamMB: Long = 0

    private val progressRunnable = object : Runnable {
        override fun run() {
            checkProgress()
            updateNetworkSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        // Static cache shared across player sessions
        private var simpleCache: SimpleCache? = null
    }

    private val subtitlePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> addExternalSubtitle(uri) }
            }
        }

    // ============================================================
    // LIFECYCLE
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 👈 EKRANIN UYUMASINI VE KİLİTLENMESİNİ ENGELLEYEN SİHİRLİ KOD
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        lastTotalRxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid)
        lastTimeStamp = System.currentTimeMillis()

        // Read RAM info once here, reused in all profile/logic functions
        readRamInfo()

        initViews()
        checkSystemRamStatus()

        if (getIntentData()) {
            if (streamType == "series") btnFavoritePlayer.visibility = View.GONE
            if (streamType == "live") disableSeekingForLive()
            checkResumeStatus()
            checkFavoriteStatus()
        } else {
            finish()
        }

        if (isTvDevice()) {
            hideSystemUI()
        } else {
            val orientation = resources.configuration.orientation
            adjustFullScreen(orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        }
    }

    override fun onResume() {
        super.onResume()
        startClockUpdate()
    }

    override fun onPause() {
        super.onPause()
        stopClockUpdate()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressRunnable)
        saveProgress()
        player?.release()
        player = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (player?.isPlaying == true) {
                val aspectRatio = android.util.Rational(16, 9)
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            playerView?.useController = false
            btnBack.visibility = View.GONE
            btnSettings.visibility = View.GONE
            btnSubtitle.visibility = View.GONE
            btnFavoritePlayer.visibility = View.GONE
            topControls?.visibility = View.GONE
            textNetworkSpeed.visibility = View.GONE
            textResolution.visibility = View.GONE
            btnNextEpisode.visibility = View.GONE
        } else {
            playerView?.useController = true
            btnBack.visibility = View.VISIBLE
            if (streamType != "series") btnFavoritePlayer.visibility = View.VISIBLE
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isTvDevice()) return
        adjustFullScreen(newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    // ============================================================
    // RAM INFO — Read once, use everywhere
    // ============================================================
    private fun readRamInfo() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            totalRamMB = memInfo.totalMem / (1024 * 1024)
            availableRamMB = memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            totalRamMB = 2048
            availableRamMB = 512
            e.printStackTrace()
        }
    }

    private fun checkSystemRamStatus() {
        if (availableRamMB < 500 || totalRamMB < 2048) {
            Toast.makeText(this, "⚠️ Low device memory — economy mode active", Toast.LENGTH_LONG).show()
            android.util.Log.w("PlayerActivity", "⚠️ Low memory: total=${totalRamMB}MB available=${availableRamMB}MB")
        }
    }

    // ============================================================
    // VIEW INIT
    // ============================================================
    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        try { topControls = findViewById(R.id.top_controls) } catch (_: Exception) {}
        btnSettings = findViewById(R.id.btn_settings)
        btnSubtitle = findViewById(R.id.btn_subtitle)
        btnBack = findViewById(R.id.btn_back)
        btnFavoritePlayer = findViewById(R.id.btn_favorite_player)
        btnNextEpisode = findViewById(R.id.btn_next_episode)
        textNetworkSpeed = findViewById(R.id.text_network_speed)
        textResolution = findViewById(R.id.text_video_resolution)
        textOverlayClock = findViewById(R.id.text_overlay_clock)

        // NOTE: Surface type (SurfaceView vs TextureView) is best controlled via XML layout:
        //   app:surface_type="surface_view"  → for low-end devices (less GPU memory)
        //   app:surface_type="texture_view"  → for high-end devices (smooth PiP)
        // Runtime switching is not supported in current Media3 versions.

        btnBack.setOnClickListener { finish() }
        btnSettings.setOnClickListener { showSettingsDialog() }
        btnSubtitle.setOnClickListener { showSubtitleDialog() }
        btnFavoritePlayer.setOnClickListener { toggleFavorite() }
        btnNextEpisode.setOnClickListener { playNextEpisode() }

        playerView?.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
            override fun onVisibilityChanged(visibility: Int) {
                topControls?.visibility = visibility
                btnBack.visibility = visibility
                textNetworkSpeed.visibility = visibility
                textResolution.visibility = visibility
                textOverlayClock.visibility = visibility

                if (topControls == null) {
                    btnSettings.visibility = visibility
                    btnSubtitle.visibility = visibility
                }
                if (streamType != "series") {
                    btnFavoritePlayer.visibility =
                        if (topControls == null) visibility
                        else if (visibility == View.VISIBLE) View.VISIBLE else View.GONE
                }
            }
        })
    }

    private fun disableSeekingForLive() {
        playerView?.apply {
            setShowRewindButton(false)
            setShowFastForwardButton(false)
            setShowPreviousButton(false)
            setShowNextButton(false)
        }
    }

    // ============================================================
    // INTENT DATA
    // ============================================================
    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        streamId = intent.getIntExtra("EXTRA_STREAM_ID", -1)
        streamType = intent.getStringExtra("EXTRA_STREAM_TYPE") ?: "live"
        fileExtension = intent.getStringExtra("EXTRA_EXTENSION")
            ?: if (streamType == "live") "ts" else "mp4"
        categoryId = intent.getStringExtra("EXTRA_CATEGORY_ID") ?: "0"
        directUrl = intent.getStringExtra("EXTRA_DIRECT_URL")
        streamName = intent.getStringExtra("EXTRA_STREAM_NAME")
            ?: getString(R.string.channel_default_name, streamId)
        streamIcon = intent.getStringExtra("EXTRA_STREAM_ICON") ?: ""
        episodeIdList = intent.getIntegerArrayListExtra("EXTRA_EPISODE_LIST")
        streamGenre = intent.getStringExtra("EXTRA_GENRE") ?: ""
        streamCast = intent.getStringExtra("EXTRA_CAST") ?: ""
        streamDirector = intent.getStringExtra("EXTRA_DIRECTOR") ?: ""

        if (episodeIdList != null && streamId != -1) {
            val currentIndex = episodeIdList!!.indexOf(streamId)
            nextEpisodeId = if (currentIndex != -1 && currentIndex < episodeIdList!!.size - 1) {
                episodeIdList!![currentIndex + 1]
            } else -1
        } else {
            nextEpisodeId = intent.getIntExtra("EXTRA_NEXT_EPISODE_ID", -1)
        }

        // Treat video-extension live streams as movies
        if (streamType == "live" && fileExtension in listOf("mp4", "mkv", "avi")) {
            streamType = "movie"
        }

        if (directUrl != null) return true
        return !(serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || streamId == -1)
    }

    // ============================================================
    // RESUME CHECK
    // ============================================================
    private fun checkResumeStatus() {
        if (streamType == "live") {
            initializePlayer()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val interaction = db.interactionDao().getInteraction(streamId, streamType!!)
            if (interaction != null && !interaction.isFinished) {
                startPosition = interaction.lastPosition
            }
            withContext(Dispatchers.Main) { initializePlayer() }
        }
    }

    // ============================================================
    // BUFFER PROFILES — Separate logic for live vs VOD
    // ============================================================
    private fun createDynamicLoadControl(): DefaultLoadControl {
        val isLowMemory = availableRamMB < 500 || totalRamMB < 2048

        // IMPROVEMENT: Live streams need low-latency buffers regardless of RAM.
        // High buffers on live = viewer is watching a delayed stream.
        if (streamType == "live") {
            return DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs */     if (isLowMemory) 1000 else 1500,
                    /* maxBufferMs */     if (isLowMemory) 5000 else 8000,
                    /* bufferForPlaybackMs */   800,
                    /* bufferForPlaybackAfterRebufferMs */ 1500
                )
                .setTargetBufferBytes(if (isLowMemory) 8 * 1024 * 1024 else 16 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(true) // For live: always prefer time
                .build()
        }

        // VOD — 5-level RAM-based profile
        data class BufferProfile(
            val targetBufferMB: Int,
            val minBuffer: Int,
            val maxBuffer: Int,
            val startBuffer: Int,
            val rebuffer: Int,
            val segmentMultiplier: Int,
            val backBuffer: Int,
            val name: String
        )

        val profile = when {
            isLowMemory ->
                BufferProfile(30, 3000, 15000, 1500, 3000, 1, 0, "Economy")
            totalRamMB < 3000 ->
                BufferProfile(60, 8000, 30000, 2000, 4000, 1, 10000, "Standard")
            totalRamMB < 4096 ->
                BufferProfile(120, 12000, 45000, 2500, 5000, 2, 15000, "Advanced")
            totalRamMB < 6144 ->
                BufferProfile(200, 15000, 60000, 2500, 5000, 2, 20000, "High Performance")
            else ->
                BufferProfile(350, 25000, 120000, 3000, 6000, 4, 30000, "Ultra")
        }

        android.util.Log.i(
            "PlayerActivity",
            "💾 RAM: ${totalRamMB}MB | Available: ${availableRamMB}MB | Profile: ${profile.name} | Buffer: ${profile.targetBufferMB}MB"
        )

        val allocator = androidx.media3.exoplayer.upstream.DefaultAllocator(
            true,
            C.DEFAULT_BUFFER_SEGMENT_SIZE * profile.segmentMultiplier
        )

        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(profile.minBuffer, profile.maxBuffer, profile.startBuffer, profile.rebuffer)
            .setTargetBufferBytes(profile.targetBufferMB * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(isLowMemory)
            .apply {
                if (profile.backBuffer > 0) {
                    setBackBuffer(profile.backBuffer, true)
                }
            }
            .build()
    }

    // ============================================================
    // PLAYER INITIALIZATION
    // ============================================================
    private fun initializePlayer() {
        if (player != null) return

        try {
            val finalUrl = buildStreamUrl()

            // 1. BANDWIDTH METER — smarter initial estimate based on RAM + network type
            val (isWifi, isFastConnection) = getNetworkInfo()
            val initialBitrate = when {
                totalRamMB < 2048 -> 2_500_000L    // 2.5 Mbps → start at 480p
                totalRamMB < 3000 -> if (isWifi) 8_000_000L else 5_000_000L   // 720p
                totalRamMB < 4096 -> if (isWifi) 15_000_000L else 10_000_000L // 1080p
                totalRamMB < 6144 -> if (isWifi) 25_000_000L else 20_000_000L // 1080p+
                else              -> if (isWifi) 50_000_000L else 40_000_000L // 4K
            }

            val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(this)
                .setInitialBitrateEstimate(initialBitrate)
                .build()

            // 2. TRACK SELECTOR
            trackSelector = DefaultTrackSelector(this)
            val parametersBuilder = trackSelector!!.buildUponParameters()
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)

            // IMPROVEMENT: On low-RAM devices, prefer H.264 over H.265.
            // H.265 decoding is cheaper on bandwidth but more CPU-heavy without hardware support.
            if (totalRamMB < 2048) {
                parametersBuilder.setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
            }

            // Enable tunneled playback on TV devices (better hardware video path)
            if (isTvDevice() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                parametersBuilder.setTunnelingEnabled(true)
            }

            applyGlobalSettings(trackSelector!!, parametersBuilder)

            // 3. NETWORK — shorter timeouts for live (fail fast, retry fast)
            val connectTimeout = if (streamType == "live") 8L else 20L
            val readTimeout = if (streamType == "live") 8L else 20L

            // --- S8 VE ESKİ CİHAZLAR İÇİN NETWORK (LEGACY TLS) ---
            val cipherSuites = okhttp3.ConnectionSpec.MODERN_TLS.cipherSuites.orEmpty().toMutableList()
            cipherSuites.add(okhttp3.CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA)
            cipherSuites.add(okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA)

            val legacySpec = okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.COMPATIBLE_TLS)
                .cipherSuites(*cipherSuites.toTypedArray())
                .tlsVersions(okhttp3.TlsVersion.TLS_1_2, okhttp3.TlsVersion.TLS_1_1, okhttp3.TlsVersion.TLS_1_0)
                .build()

            val okHttpClient = OkHttpClient.Builder()
                .connectionSpecs(listOf(legacySpec, okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.CLEARTEXT))
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()

            val baseDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("IPTVSmartersPro")

            // Only cache VOD/series — caching live streams wastes memory
            val finalDataSourceFactory: DataSource.Factory = if (streamType != "live" && totalRamMB >= 2048) {
                setupCache(this, baseDataSourceFactory)
            } else {
                android.util.Log.i("PlayerActivity", "⚡ Disk yavaşlığı riski: Cache iptal edildi, direkt RAM'den oynatılıyor.")
                baseDataSourceFactory
            }

            // 4. EXTRACTORS (TS-optimized)
            val extractorsFactory = DefaultExtractorsFactory()
                .setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                )

            // 5. MEDIA SOURCE — smart routing by URL format
            // IMPROVEMENT: Explicit source type = proper ABR for HLS/DASH,
            // instead of relying on DefaultMediaSourceFactory's guessing.
            val mediaSource = buildMediaSource(finalUrl, finalDataSourceFactory, extractorsFactory)

            // 6. RENDERER — hardware first, software fallback, extensions preferred
            val renderersFactory = DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF) // 👈 DOĞRUSU BU (TV'ler için hayat kurtarır)

            // 7. BUILD PLAYER
            player = ExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector!!)
                .setLoadControl(createDynamicLoadControl())
                .setBandwidthMeter(bandwidthMeter)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build()

            // Audio focus: pause when another app takes audio
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            player?.setAudioAttributes(audioAttributes, true)

            playerView?.player = player
            playerView?.keepScreenOn = true

            // Set media source directly (not MediaItem) so we get proper HLS/DASH ABR
            player?.setMediaSource(mediaSource)

            if (startPosition > 0) player?.seekTo(startPosition)

            // 8. LISTENERS
            player?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) handler.post(progressRunnable)
                    else handler.removeCallbacks(progressRunnable)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        smartSelectSubtitle()
                    }
                }
            })

// --- AKILLI TEŞHİS VE UYARI SİSTEMİ (DOKTOR MODU) ---
            var droppedFramesCount = 0
            var lastWarningTime = 0L

            player?.addAnalyticsListener(object : AnalyticsListener {

                override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                    runOnUiThread {
                        textResolution.text = "${videoSize.width}x${videoSize.height}"
                        textResolution.visibility = View.VISIBLE
                    }

                    // TEŞHİS 1: Cihaz Gerçek 4K Destekliyor mu?
                    val maxRes = getMaxSupportedResolution()
                    if (videoSize.width > 2500 && maxRes.first < 3840) {
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "⚠️ DONANIM UYARISI: Cihazınızın çipi donanımsal 4K desteklemiyor. Görüntü yazılımsal çözüldüğü için kasmalar yaşanabilir.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // TEŞHİS 2: İşlemci (CPU/GPU) Şişmesi (Kare Atlama)
                override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                    droppedFramesCount += droppedFrames
                    val now = System.currentTimeMillis()

                    // Eğer 30 saniye içinde 60 kareden fazla atlarsa işlemci ağlıyor demektir.
                    if (droppedFramesCount > 60 && (now - lastWarningTime > 30000)) {
                        droppedFramesCount = 0
                        lastWarningTime = now
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "⚠️ PERFORMANS UYARISI: Cihazınızın işlemcisi (CPU/GPU) bu yüksek kaliteyi işlemekte zorlanıyor. Görüntüde atlamalar olabilir.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // TEŞHİS 3: İnternet veya Sunucu Yetersizliği
                override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                    val requiredBitrate = mediaLoadData.trackFormat?.bitrate ?: 0
                    val now = System.currentTimeMillis()

                    // smoothedSpeed (Bayt/sn) * 8 = Bit/sn.
                    // Eğer anlık hızımız, videonun istediği hızın %80'inin altındaysa:
                    if (requiredBitrate > 0 && smoothedSpeed > 0 && (smoothedSpeed * 8) < (requiredBitrate * 0.8) && (now - lastWarningTime > 30000)) {
                        lastWarningTime = now
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "⚠️ BAĞLANTI UYARISI: İnternet hızınız veya sunucu çıkış hızı bu kalite için yetersiz. Lütfen çözünürlüğü düşürün.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })

            player?.prepare()
            player?.play()
            startTime = System.currentTimeMillis()

        } catch (e: Exception) {
            showDetailedErrorDialog(getString(R.string.title_init_error), e.message ?: "")
        }
    }

    // ============================================================
    // SMART URL BUILDER
    // ============================================================
    private fun buildStreamUrl(): String {
        if (directUrl != null) return directUrl!!

        val cleanUrl = serverUrl!!.trimEnd('/')
        val safeUsername = if (!username.isNullOrEmpty())
            java.net.URLEncoder.encode(username, "UTF-8") else ""
        val safePassword = if (!password.isNullOrEmpty())
            java.net.URLEncoder.encode(password, "UTF-8") else ""

        return if (safeUsername.isEmpty() || safePassword.isEmpty()) {
            "$cleanUrl/live/$streamId.$fileExtension"
        } else {
            when (streamType) {
                "vod", "movie" -> "$cleanUrl/movie/$safeUsername/$safePassword/$streamId.$fileExtension"
                "series"       -> "$cleanUrl/series/$safeUsername/$safePassword/$streamId.$fileExtension"
                else           -> "$cleanUrl/live/$safeUsername/$safePassword/$streamId.$fileExtension"
            }
        }
    }

    // ============================================================
    // SMART MEDIA SOURCE FACTORY
    // IMPROVEMENT: Detect stream format and use the right source type.
    // This is what unlocks real ABR (adaptive bitrate) for HLS/DASH.
    // ============================================================
    private fun buildMediaSource(
        url: String,
        dataSourceFactory: DataSource.Factory,
        extractorsFactory: DefaultExtractorsFactory
    ): MediaSource {
        val uri = Uri.parse(url)
        val mediaItem = MediaItem.fromUri(uri)

        return when {
            // HLS — .m3u8 or extension "m3u8"
            url.contains(".m3u8", ignoreCase = true) || fileExtension == "m3u8" -> {
                android.util.Log.i("PlayerActivity", "▶ Source type: HLS")
                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true) // Faster start
                    .createMediaSource(mediaItem)
            }

            // DASH — .mpd
            // To enable full DASH support, add to build.gradle:
            //   implementation "androidx.media3:media3-exoplayer-dash:<version>"
            // Then uncomment: import androidx.media3.exoplayer.dash.DashMediaSource
            // For now, DefaultMediaSourceFactory handles .mpd via auto-detection.
            url.contains(".mpd", ignoreCase = true) -> {
                android.util.Log.i("PlayerActivity", "▶ Source type: DASH (via DefaultMediaSourceFactory)")
                DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }

            // TS live streams — use Progressive with TS extractors
            url.contains(".ts", ignoreCase = true) || fileExtension == "ts" -> {
                android.util.Log.i("PlayerActivity", "▶ Source type: Progressive TS")
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }

            // Everything else (mp4, mkv, avi) — Progressive
            else -> {
                android.util.Log.i("PlayerActivity", "▶ Source type: Progressive (default)")
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }

    // ============================================================
    // CACHE SETUP (VOD only)
    // ============================================================
    private fun setupCache(context: Context, upstreamFactory: DataSource.Factory): DataSource.Factory {
        if (simpleCache == null) {
            val cacheSize = when {
                totalRamMB < 2048 -> 100L * 1024 * 1024   // 100 MB
                totalRamMB < 3000 -> 300L * 1024 * 1024   // 300 MB
                totalRamMB < 4096 -> 500L * 1024 * 1024   // 500 MB
                totalRamMB < 6144 -> 800L * 1024 * 1024   // 800 MB
                else              -> 1200L * 1024 * 1024  // 1.2 GB
            }
            android.util.Log.i("PlayerActivity", "💿 Cache: ${cacheSize / (1024 * 1024)}MB")
            val evictor = LeastRecentlyUsedCacheEvictor(cacheSize)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(File(context.cacheDir, "media"), evictor, databaseProvider)
        }
        return CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // ============================================================
    // TRACK SELECTOR — GLOBAL SETTINGS
    // ============================================================
    private fun applyGlobalSettings(
        selector: DefaultTrackSelector,
        parametersBuilder: DefaultTrackSelector.Parameters.Builder
    ) {
        val audioLang = SettingsManager.getAudioLang(this)
        val subLang = SettingsManager.getSubtitleLang(this)
        parametersBuilder
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setPreferredAudioLanguage(audioLang)
            .setPreferredTextLanguage(subLang)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setSelectUndeterminedTextLanguage(true)
        selector.parameters = parametersBuilder.build()
    }

    // ============================================================
    // ERROR HANDLING — Multi-step fallback
    // IMPROVEMENT: Tries multiple protocol/extension combos before giving up.
    // Order: ts → m3u8 → direct HTTP retry → error dialog
    // ============================================================
    private fun handlePlayerError(error: PlaybackException) {
        val cause = error.cause
        android.util.Log.e("PlayerActivity", "❌ Player error [code=${error.errorCode}]: ${error.message}", error)

        val isCodecError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
        val isNetworkError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        val isHttpError = cause is HttpDataSource.InvalidResponseCodeException &&
                (cause.responseCode in 400..499)
        val isConnectionError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        // Attempt automatic recovery for live streams
        if (streamType == "live" && fallbackAttempt < maxFallbackAttempts) {
            fallbackAttempt++
            when (fallbackAttempt) {
                1 -> {
                    // Step 1: Toggle extension ts ↔ m3u8
                    val oldExt = fileExtension
                    fileExtension = if (fileExtension == "ts") "m3u8" else "ts"
                    android.util.Log.i("PlayerActivity", "🔄 Fallback #1: $oldExt → $fileExtension")
                    Toast.makeText(this, "Retrying with .$fileExtension...", Toast.LENGTH_SHORT).show()
                    restartPlayer()
                    return
                }
                2 -> {
                    // Step 2: Try the other extension again (covers ts→m3u8→ts cycle)
                    fileExtension = if (fileExtension == "ts") "m3u8" else "ts"
                    android.util.Log.i("PlayerActivity", "🔄 Fallback #2: Alternate extension retry")
                    Toast.makeText(this, "Retrying connection...", Toast.LENGTH_SHORT).show()

                    // Also clear cache in case a corrupt segment is cached
                    try {
                        com.bybora.smartxtream.utils.SmartCacheManager.clearCache(this) {
                            restartPlayer()
                        }
                    } catch (_: Exception) {
                        restartPlayer()
                    }
                    return
                }
                3 -> {
                    // Step 3: Try direct URL without username/password (some servers need this)
                    val altUrl = serverUrl?.let {
                        "${it.trimEnd('/')}/live/$streamId.$fileExtension"
                    }
                    if (altUrl != null && altUrl != buildStreamUrl()) {
                        android.util.Log.i("PlayerActivity", "🔄 Fallback #3: Direct URL without credentials")
                        directUrl = altUrl
                        Toast.makeText(this, "Trying alternate connection...", Toast.LENGTH_SHORT).show()
                        restartPlayer()
                        return
                    }
                }
            }
        }

        // Codec error: try dropping to lower quality before showing error
        if (isCodecError) {
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000)
                tryLowerQuality()
            }
        }

        // Build user-friendly error message
        val title: String
        val msg: String
        when {
            isCodecError -> {
                title = getString(R.string.title_codec_error)
                msg = getString(R.string.error_code_msg, "0x${Integer.toHexString(error.errorCode)}")
            }
            cause is HttpDataSource.InvalidResponseCodeException -> {
                title = getString(R.string.title_server_error, cause.responseCode)
                msg = when (cause.responseCode) {
                    404  -> getString(R.string.error_file_not_found)
                    403  -> getString(R.string.error_access_denied)
                    else -> getString(R.string.error_server_generic)
                }
            }
            isNetworkError -> {
                title = getString(R.string.title_no_internet)
                msg = getString(R.string.error_connection_failed)
            }
            else -> {
                title = getString(R.string.title_playback_error)
                msg = getString(R.string.error_code_msg, error.message ?: getString(R.string.label_unknown))
            }
        }

        showDetailedErrorDialog(title, msg)
    }

    /**
     * Releases and restarts the player with the current (possibly updated) URL/extension.
     */
    private fun restartPlayer() {
        player?.release()
        player = null
        initializePlayer()
    }

    private fun tryLowerQuality() {
        try {
            val tracks = getTracksByType(C.TRACK_TYPE_VIDEO)
            if (tracks.isEmpty()) return
            val sorted = tracks.mapNotNull { track ->
                val parts = track.name.split("x")
                if (parts.size == 2) {
                    val w = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val h = parts[1].toIntOrNull() ?: return@mapNotNull null
                    Triple(w * h, track, "${w}x${h}")
                } else null
            }.sortedBy { it.first }

            // Pick highest resolution that is still ≤ 1080p
            val target = sorted.lastOrNull { it.first <= 1920 * 1080 } ?: sorted.firstOrNull()
            target?.let {
                selectTrack(C.TRACK_TYPE_VIDEO, it.second.groupIndex, it.second.trackIndex, it.second.rendererIndex)
                val pos = player?.currentPosition ?: 0
                player?.seekTo(pos)
                player?.prepare()
                player?.play()
                Toast.makeText(this, getString(R.string.msg_quality_lowered), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "Quality fallback error", e)
        }
    }

    // ============================================================
    // NETWORK INFO
    // ============================================================
    private fun getNetworkInfo(): Pair<Boolean, Boolean> {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val downstreamBandwidth = capabilities?.linkDownstreamBandwidthKbps ?: 0
        val isFastConnection = downstreamBandwidth > 15000
        return Pair(isWifi, isFastConnection)
    }

    // ============================================================
    // NETWORK SPEED DISPLAY
    // ============================================================
    private fun updateNetworkSpeed() {
        val currentRxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid)
        if (currentRxBytes == TrafficStats.UNSUPPORTED.toLong()) return
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTimeStamp

        if (timeDiff >= 1000) {
            val bytesDiff = max(0L, currentRxBytes - lastTotalRxBytes)
            val instantSpeed = (bytesDiff * 1000) / timeDiff
            // EMA smoothing: 60% old + 40% new
            smoothedSpeed = (smoothedSpeed * 0.6) + (instantSpeed * 0.4)

            val speedText = if (smoothedSpeed >= 1024 * 1024) {
                "${(smoothedSpeed / (1024f * 1024f)).toInt()} MB/s"
            } else {
                "${(smoothedSpeed / 1024).toLong()} KB/s"
            }

            textNetworkSpeed.text = speedText
            lastTotalRxBytes = currentRxBytes
            lastTimeStamp = currentTime
        }
    }

    // ============================================================
    // PROGRESS TRACKING
    // ============================================================
    private fun checkProgress() {
        if (player == null || streamType == "live") return
        val current = player!!.currentPosition
        val duration = player!!.duration
        if (nextEpisodeId != -1 && duration > 0 && (duration - current) < 45_000) {
            if (btnNextEpisode.visibility != View.VISIBLE) btnNextEpisode.visibility = View.VISIBLE
        } else {
            if (btnNextEpisode.visibility == View.VISIBLE) btnNextEpisode.visibility = View.GONE
        }
    }

    private fun saveProgress() {
        if (player == null || streamType == "live") return

        val pos = player!!.currentPosition
        val dur = player!!.duration
        val watched = (System.currentTimeMillis() - startTime) / 1000
        val watchedMinutes = (watched / 60).toDouble()

        if (watchedMinutes > 5) {
            val activeProfileId = com.bybora.smartxtream.utils.SettingsManager.getSelectedProfileId(this)
            val points = watchedMinutes * com.bybora.smartxtream.utils.PreferenceManager.SCORE_WATCH_PER_MIN
            val meta = com.bybora.smartxtream.utils.PreferenceManager.MetaDataContainer(streamGenre, streamCast, streamDirector)

            lifecycleScope.launch(Dispatchers.IO) {
                com.bybora.smartxtream.utils.PreferenceManager.analyzeAndStore(
                    applicationContext, activeProfileId, meta, points
                )
            }
        }

        val finished = (dur > 0) && (pos >= (dur * 0.95))
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val exist = db.interactionDao().getInteraction(streamId, streamType!!)
            val total = (exist?.durationSeconds ?: 0) + watched
            val interact = Interaction(
                id = exist?.id ?: 0,
                streamId = streamId,
                streamType = streamType!!,
                categoryId = categoryId ?: "0",
                durationSeconds = total,
                lastPosition = if (finished) 0 else pos,
                maxDuration = dur,
                isFinished = finished
            )
            db.interactionDao().logInteraction(interact)
        }
    }

    // ============================================================
    // NEXT EPISODE
    // ============================================================
    private fun playNextEpisode() {
        saveProgress()
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", nextEpisodeId)
            putExtra("EXTRA_STREAM_TYPE", streamType)
            putExtra("EXTRA_EXTENSION", fileExtension)
            putExtra("EXTRA_CATEGORY_ID", categoryId)
            if (episodeIdList != null) putIntegerArrayListExtra("EXTRA_EPISODE_LIST", episodeIdList)
        }
        startActivity(intent)
        finish()
    }

    // ============================================================
    // FAVORITES
    // ============================================================
    private fun checkFavoriteStatus() {
        if (streamType == "series") return
        lifecycleScope.launch {
            isFav = db.favoriteDao().isFavorite(streamId, streamType ?: "live")
            updateFavIcon()
        }
    }

    private fun toggleFavorite() {
        lifecycleScope.launch {
            if (isFav) {
                db.favoriteDao().removeFavorite(streamId, streamType ?: "live")
                isFav = false
                Toast.makeText(this@PlayerActivity, getString(R.string.msg_fav_removed), Toast.LENGTH_SHORT).show()
            } else {
                val fav = Favorite(
                    streamId = streamId,
                    streamType = streamType ?: "live",
                    name = streamName,
                    image = streamIcon,
                    categoryId = categoryId
                )
                db.favoriteDao().addFavorite(fav)

                val activeProfileId = com.bybora.smartxtream.utils.SettingsManager.getSelectedProfileId(this@PlayerActivity)
                val meta = com.bybora.smartxtream.utils.PreferenceManager.MetaDataContainer(
                    genre = streamGenre, cast = streamCast, director = streamDirector
                )
                com.bybora.smartxtream.utils.PreferenceManager.analyzeAndStore(
                    applicationContext, activeProfileId, meta,
                    com.bybora.smartxtream.utils.PreferenceManager.SCORE_FAVORITE
                )

                isFav = true
                Toast.makeText(this@PlayerActivity, getString(R.string.msg_fav_added), Toast.LENGTH_SHORT).show()
            }
            updateFavIcon()
        }
    }

    private fun updateFavIcon() {
        val icon = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        btnFavoritePlayer.setImageResource(icon)
    }

    // ============================================================
    // SUBTITLE
    // ============================================================
    private fun smartSelectSubtitle() {
        val preferredLang = SettingsManager.getSubtitleLang(this)
        val tracks = getTracksByType(C.TRACK_TYPE_TEXT)
        val searchKeyword = when (preferredLang) {
            "tr" -> "tur"
            "ru" -> "rus"
            else -> preferredLang
        }
        val target = tracks.find { it.name.lowercase().contains(preferredLang) }
            ?: tracks.find { it.name.lowercase().contains(searchKeyword) }
        target?.let { selectTrack(C.TRACK_TYPE_TEXT, it.groupIndex, it.trackIndex, it.rendererIndex) }
    }

    private fun showSubtitleDialog() {
        val tracks = getTracksByType(C.TRACK_TYPE_TEXT)
        val items = tracks.map { it.name }.toMutableList().also {
            it.add(0, getString(R.string.option_off))
            it.add(getString(R.string.option_load_from_file))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_select_subtitle))
            .setItems(items.toTypedArray()) { _, w ->
                when {
                    w == 0           -> disableTrack(C.TRACK_TYPE_TEXT)
                    w <= tracks.size -> tracks[w - 1].let { selectTrack(C.TRACK_TYPE_TEXT, it.groupIndex, it.trackIndex, it.rendererIndex) }
                    else             -> openFilePicker()
                }
            }.show()
    }

    private fun openFilePicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        subtitlePickerLauncher.launch(i)
    }

    private fun addExternalSubtitle(uri: Uri) {
        if (player == null) return
        val media = player?.currentMediaItem ?: return
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("tr")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val newMedia = media.buildUpon().setSubtitleConfigurations(listOf(sub)).build()
        val pos = player?.currentPosition ?: 0
        player?.setMediaItem(newMedia)
        player?.seekTo(pos)
        player?.prepare()
        player?.play()
        Toast.makeText(this, getString(R.string.msg_loaded), Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // TRACK SELECTION
    // ============================================================
    data class TrackInfo(val name: String, val groupIndex: Int, val trackIndex: Int, val rendererIndex: Int)

    private fun getTracksByType(type: Int): List<TrackInfo> {
        val list = mutableListOf<TrackInfo>()
        val info = trackSelector?.currentMappedTrackInfo ?: return emptyList()

        for (i in 0 until info.rendererCount) {
            if (info.getRendererType(i) == type) {
                val groups = info.getTrackGroups(i)
                for (j in 0 until groups.length) {
                    val group = groups[j]
                    for (k in 0 until group.length) {
                        if (info.getTrackSupport(i, j, k) == C.FORMAT_HANDLED) {
                            val f = group.getFormat(k)
                            val name = when (type) {
                                C.TRACK_TYPE_VIDEO -> "${f.width}x${f.height}"
                                C.TRACK_TYPE_AUDIO -> f.language ?: "und"
                                C.TRACK_TYPE_TEXT  -> "${f.language ?: "und"} (${f.label ?: getString(R.string.label_unknown)})"
                                else               -> "?"
                            }
                            list.add(TrackInfo(name, j, k, i))
                        }
                    }
                }
            }
        }
        return list
    }

    private fun showTrackSelectionDialog(type: Int, title: String) {
        val tracks = getTracksByType(type)
        if (tracks.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_no_options), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(tracks.map { it.name }.toTypedArray()) { _, w ->
                val t = tracks[w]
                selectTrack(type, t.groupIndex, t.trackIndex, t.rendererIndex)
            }.show()
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.option_video_quality),
            getString(R.string.option_audio_lang)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_settings))
            .setItems(options) { _, w ->
                when (w) {
                    0 -> showTrackSelectionDialog(C.TRACK_TYPE_VIDEO, getString(R.string.title_resolution))
                    1 -> showTrackSelectionDialog(C.TRACK_TYPE_AUDIO, getString(R.string.title_audio))
                }
            }.show()
    }

    private fun selectTrack(type: Int, g: Int, t: Int, r: Int?) {
        val info = trackSelector?.currentMappedTrackInfo ?: return
        val rIdx = r ?: (0 until info.rendererCount).find { info.getRendererType(it) == type } ?: return
        val override = TrackSelectionOverride(info.getTrackGroups(rIdx)[g], t)
        trackSelector?.parameters = trackSelector!!.buildUponParameters()
            .setOverrideForType(override)
            .build()
        Toast.makeText(this, getString(R.string.msg_selected), Toast.LENGTH_SHORT).show()
    }

    private fun disableTrack(type: Int) {
        trackSelector?.parameters = trackSelector!!.buildUponParameters()
            .setTrackTypeDisabled(type, true)
            .build()
        Toast.makeText(this, getString(R.string.msg_disabled), Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // MAX SUPPORTED RESOLUTION (for future use / logging)
    // ============================================================
    private fun getMaxSupportedResolution(): Pair<Int, Int> {
        return try {
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            var maxWidth = 1920
            var maxHeight = 1080
            codecList.codecInfos.filter { !it.isEncoder }.forEach { codecInfo ->
                codecInfo.supportedTypes.filter { it.startsWith("video/") }.forEach { type ->
                    try {
                        val cap = codecInfo.getCapabilitiesForType(type)
                        cap.videoCapabilities?.let {
                            if (it.supportedWidths.upper > maxWidth) {
                                maxWidth = it.supportedWidths.upper
                                maxHeight = it.supportedHeights.upper
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            Pair(maxWidth, maxHeight)
        } catch (_: Exception) {
            Pair(1920, 1080)
        }
    }

    // ============================================================
    // ERROR DIALOG
    // ============================================================
    private fun showDetailedErrorDialog(title: String, message: String) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("⚠️ $title")
            .setMessage(message)
            .setPositiveButton(getString(R.string.btn_ok)) { d, _ -> d.dismiss(); finish() }
            .setCancelable(false)
            .show()
    }

    // ============================================================
    // SCREEN / FULLSCREEN MANAGEMENT
    // ============================================================
    private fun adjustFullScreen(isLandscape: Boolean) {
        if (isLandscape) {
            hideSystemUI()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.also {
                    it.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        } else {
            showSystemUI()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.also {
                    it.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun hideSystemUI() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    // ============================================================
    // CLOCK OVERLAY
    // ============================================================
    private fun startClockUpdate() {
        clockRunnable = object : Runnable {
            override fun run() {
                val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                if (::textOverlayClock.isInitialized) {
                    textOverlayClock.text = currentTime
                }
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable)
    }

    private fun stopClockUpdate() {
        if (::clockRunnable.isInitialized) {
            clockHandler.removeCallbacks(clockRunnable)
        }
    }
}
