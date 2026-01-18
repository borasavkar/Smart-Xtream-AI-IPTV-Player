package com.bybora.smartxtream

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var trackSelector: DefaultTrackSelector? = null

    // UI
    private lateinit var btnSettings: ImageButton
    private lateinit var btnSubtitle: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnFavoritePlayer: ImageButton
    private lateinit var btnNextEpisode: Button
    private lateinit var textNetworkSpeed: TextView
    private lateinit var textResolution: TextView
    private var topControls: View? = null

    // Veri
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

    private val db by lazy { AppDatabase.getInstance(this) }
    private var isFav = false

    private var startTime: Long = 0
    private var startPosition: Long = 0

    private var lastTotalRxBytes: Long = 0
    private var lastTimeStamp: Long = 0
    private var smoothedSpeed: Double = 0.0

    private val handler = Handler(Looper.getMainLooper())
    // ... Diğer değişkenlerin altına ekle
    private var isFallbackTried = false
    private val progressRunnable = object : Runnable {
        override fun run() {
            checkProgress()
            updateNetworkSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private var simpleCache: SimpleCache? = null
    }

    private val subtitlePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> addExternalSubtitle(uri) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        lastTotalRxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid)
        lastTimeStamp = System.currentTimeMillis()

        initViews()
        if (getIntentData()) {
            if (streamType == "series") btnFavoritePlayer.visibility = View.GONE
            if (streamType == "live") disableSeekingForLive()
            checkResumeStatus()
            checkFavoriteStatus()
        } else {
            finish()
        }
    }

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

                if (topControls == null) {
                    btnSettings.visibility = visibility
                    btnSubtitle.visibility = visibility
                }
                if (streamType != "series") {
                    btnFavoritePlayer.visibility = if(topControls==null) visibility else (if(visibility==View.VISIBLE) View.VISIBLE else View.GONE)
                }
            }
        })
    }

    private fun disableSeekingForLive() {
        val pv = playerView ?: return
        pv.setShowRewindButton(false)
        pv.setShowFastForwardButton(false)
        pv.setShowPreviousButton(false)
        pv.setShowNextButton(false)
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        streamId = intent.getIntExtra("EXTRA_STREAM_ID", -1)
        streamType = intent.getStringExtra("EXTRA_STREAM_TYPE") ?: "live"
        fileExtension = intent.getStringExtra("EXTRA_EXTENSION") ?: if (streamType == "live") "ts" else "mp4"
        categoryId = intent.getStringExtra("EXTRA_CATEGORY_ID") ?: "0"
        directUrl = intent.getStringExtra("EXTRA_DIRECT_URL")
        streamName = intent.getStringExtra("EXTRA_STREAM_NAME") ?: getString(R.string.channel_default_name, streamId)
        streamIcon = intent.getStringExtra("EXTRA_STREAM_ICON") ?: ""
        episodeIdList = intent.getIntegerArrayListExtra("EXTRA_EPISODE_LIST")

        if (episodeIdList != null && streamId != -1) {
            val currentIndex = episodeIdList!!.indexOf(streamId)
            if (currentIndex != -1 && currentIndex < episodeIdList!!.size - 1) {
                nextEpisodeId = episodeIdList!![currentIndex + 1]
            } else {
                nextEpisodeId = -1
            }
        } else {
            nextEpisodeId = intent.getIntExtra("EXTRA_NEXT_EPISODE_ID", -1)
        }

        if (directUrl != null) return true
        return !(serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || streamId == -1)
    }

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

    private fun initializePlayer() {
        if (player == null) {
            try {
                val finalUrl = if (directUrl != null) {
                    directUrl!!
                } else {
                    val cleanUrl = serverUrl!!.trimEnd('/')
                    val safeUsername = if (!username.isNullOrEmpty()) java.net.URLEncoder.encode(username, "UTF-8") else ""
                    val safePassword = if (!password.isNullOrEmpty()) java.net.URLEncoder.encode(password, "UTF-8") else ""

                    if (safeUsername.isEmpty() || safePassword.isEmpty()) {
                        "$cleanUrl/live/$streamId.$fileExtension"
                    } else {
                        when (streamType) {
                            "vod", "movie" -> "$cleanUrl/movie/$safeUsername/$safePassword/$streamId.$fileExtension"
                            "series" -> "$cleanUrl/series/$safeUsername/$safePassword/$streamId.$fileExtension"
                            else -> "$cleanUrl/live/$safeUsername/$safePassword/$streamId.$fileExtension"
                        }
                    }
                }

                trackSelector = DefaultTrackSelector(this)
                applyGlobalSettings(trackSelector!!)

                val baseClient: OkHttpClient = RetrofitClient.okHttpClient
                val okHttpClient = baseClient.newBuilder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).build()
                val userAgent = "IPTVSmartersPro"
                val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient).setUserAgent(userAgent)
                val dataSourceFactory: DataSource.Factory = if (streamType != "live") setupCache(this, okHttpDataSourceFactory) else okHttpDataSourceFactory

                // RENDER MODE: ON (Donanım + Yazılım Hibrit)
                val renderersFactory = DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

                // Standart Buffer
                val loadControl = DefaultLoadControl.Builder()
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBufferDurationsMs(
                        3000,  // Min Buffer: 3 saniye (Düşük tutuldu ki hemen hazırlansın)
                        30000, // Max Buffer: 30 saniye
                        1000,  // Oynatmaya Başlama: Sadece 1 saniye dolunca başla (HIZ İÇİN KRİTİK)
                        2000   // Yeniden Tamponlama: Donarsa 2 saniye dolunca devam et
                    )
                    .build()

                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory).setLiveTargetOffsetMs(10_000)

                player = ExoPlayer.Builder(this, renderersFactory).setMediaSourceFactory(mediaSourceFactory).setTrackSelector(trackSelector!!).setLoadControl(loadControl).setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true).setDeviceVolumeControlEnabled(true).setHandleAudioBecomingNoisy(true).build()

                playerView?.player = player
                playerView?.keepScreenOn = true
                val mediaItem = MediaItem.fromUri(finalUrl)
                player?.setMediaItem(mediaItem)
                if (startPosition > 0) player?.seekTo(startPosition)

                player?.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { handlePlayerError(error) }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) handler.post(progressRunnable) else handler.removeCallbacks(progressRunnable) }
                    override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == androidx.media3.common.Player.STATE_READY) smartSelectSubtitle() }
                })

                player?.prepare()
                player?.play()
                startTime = System.currentTimeMillis()

                player?.addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                        runOnUiThread {
                            textResolution.text = "${videoSize.width}x${videoSize.height}"
                            textResolution.visibility = View.VISIBLE
                        }
                    }
                })

            } catch (e: Exception) {
                showDetailedErrorDialog(
                    getString(R.string.title_init_error),
                    e.message ?: getString(R.string.error_unknown))
            }
        }
    }

    private fun setupCache(context: Context, upstreamFactory: DataSource.Factory): DataSource.Factory {
        if (simpleCache == null) {
            val evictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(File(context.cacheDir, "media"), evictor, databaseProvider)
        }
        return CacheDataSource.Factory().setCache(simpleCache!!).setUpstreamDataSourceFactory(upstreamFactory).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun applyGlobalSettings(selector: DefaultTrackSelector) {
        val parametersBuilder = selector.buildUponParameters()
        val (maxWidth, maxHeight) = getMaxSupportedResolution()
        parametersBuilder.setMaxVideoSize(maxWidth, maxHeight)
        parametersBuilder.setMaxVideoBitrate(Int.MAX_VALUE)
        parametersBuilder.setAllowVideoNonSeamlessAdaptiveness(true)
        parametersBuilder.setAllowVideoMixedMimeTypeAdaptiveness(false)
        parametersBuilder.setAllowAudioMixedMimeTypeAdaptiveness(true)
        val audioLang = SettingsManager.getAudioLang(this)
        val subLang = SettingsManager.getSubtitleLang(this)
        parametersBuilder.setPreferredAudioLanguage(audioLang)
        parametersBuilder.setPreferredTextLanguage(subLang)
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        parametersBuilder.setSelectUndeterminedTextLanguage(true)
        selector.parameters = parametersBuilder.build()
    }

    private fun canHandle4K(): Boolean { return true }

    private fun getMaxSupportedResolution(): Pair<Int, Int> {
        return try {
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            var maxWidth = 1920
            var maxHeight = 1080
            codecList.codecInfos.filter { !it.isEncoder }.forEach { codecInfo -> codecInfo.supportedTypes.filter { it.startsWith("video/") }.forEach { type -> try { val cap = codecInfo.getCapabilitiesForType(type); cap.videoCapabilities?.let { if (it.supportedWidths.upper > maxWidth) { maxWidth = it.supportedWidths.upper; maxHeight = it.supportedHeights.upper } } } catch (_: Exception) {} } }
            Pair(maxWidth, maxHeight)
        } catch (_: Exception) { Pair(1920, 1080) }
    }

    private fun smartSelectSubtitle() {
        val preferredLang = SettingsManager.getSubtitleLang(this)
        val tracks = getTracksByType(C.TRACK_TYPE_TEXT)
        var targetTrack = tracks.find { it.name.lowercase().contains(preferredLang) }
        if (targetTrack == null) {
            val searchKeyword = if (preferredLang == "tr") "tur" else if (preferredLang == "ru") "rus" else preferredLang
            targetTrack = tracks.find { it.name.lowercase().contains(searchKeyword) }
        }
        if (targetTrack != null) selectTrack(C.TRACK_TYPE_TEXT, targetTrack.groupIndex, targetTrack.trackIndex, targetTrack.rendererIndex)
    }

    private fun updateNetworkSpeed() {
        val currentRxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid)
        if (currentRxBytes == TrafficStats.UNSUPPORTED.toLong()) return
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTimeStamp
        if (timeDiff >= 1000) {
            val bytesDiff = max(0L, currentRxBytes - lastTotalRxBytes)
            val currentInstantSpeed = (bytesDiff * 1000) / timeDiff
            smoothedSpeed = (smoothedSpeed * 0.6) + (currentInstantSpeed * 0.4)
            val speedText = if (smoothedSpeed >= 1024 * 1024) String.format("%.1f MB/s", smoothedSpeed / (1024f * 1024f)) else String.format("%d KB/s", (smoothedSpeed / 1024).toLong())
            textNetworkSpeed.text = speedText
            lastTotalRxBytes = currentRxBytes
            lastTimeStamp = currentTime
        }
    }

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
                val fav = Favorite(streamId = streamId, streamType = streamType ?: "live", name = streamName, image = streamIcon, categoryId = categoryId)
                db.favoriteDao().addFavorite(fav)
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

    private fun handlePlayerError(error: PlaybackException) {
        val cause = error.cause
        // --- AKILLI KURTARMA MEKANİZMASI ---
        // Eğer hata sunucu kaynaklıysa (404 Bulunamadı, 403 Yasaklı, 405 İzin Yok)
        // Ve yayın CANLI ise, ve daha önce diğer yolu denemediysek:
        if (streamType == "live" && !isFallbackTried && cause is HttpDataSource.InvalidResponseCodeException) {

            val code = cause.responseCode
            // Genelde 400 ile 500 arasındaki hatalar uzantı sorunudur
            if (code in 400..499) {
                isFallbackTried = true // Denedim diye işaretle (Sonsuz döngü olmasın)

                // Uzantıyı değiştir: ts ise m3u8 yap, m3u8 ise ts yap
                fileExtension = if (fileExtension == "ts") "m3u8" else "ts"

                // Player'ı serbest bırak ve yeni uzantıyla tekrar başlat
                player?.release()
                player = null
                initializePlayer() // Tekrar başlat

                // Kullanıcıya çaktırmadan alttan ufak bilgi ver (İstersen bunu sil)
                // Toast.makeText(this, "Alternatif yayın deneniyor...", Toast.LENGTH_SHORT).show()
                return // Fonksiyondan çık, hata mesajı gösterme
            }
        }
        // -----------------------------------

        val title: String; var msg: String;
        when {
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                title = getString(R.string.title_codec_error)
                msg = getString(R.string.error_code_msg, "0x${Integer.toHexString(error.errorCode)}")
                lifecycleScope.launch { kotlinx.coroutines.delay(2000); tryLowerQuality() }
            }
            cause is HttpDataSource.InvalidResponseCodeException -> {
                title = getString(R.string.title_server_error, cause.responseCode)
                msg = when (cause.responseCode) {
                    404 -> getString(R.string.error_file_not_found)
                    403 -> getString(R.string.error_access_denied)
                    else -> getString(R.string.error_server_generic)
                }
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
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

    private fun tryLowerQuality() {
        try {
            val tracks = getTracksByType(C.TRACK_TYPE_VIDEO)
            if (tracks.isEmpty()) return
            val sortedTracks = tracks.mapNotNull { track ->
                val parts = track.name.split("x")
                if (parts.size == 2) {
                    val width = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val height = parts[1].toIntOrNull() ?: return@mapNotNull null
                    Triple(width * height, track, "${width}x${height}")
                } else null
            }.sortedBy { it.first }
            val targetTrack = sortedTracks.firstOrNull { it.first <= 1920 * 1080 } ?: sortedTracks.firstOrNull()
            targetTrack?.let {
                selectTrack(C.TRACK_TYPE_VIDEO, it.second.groupIndex, it.second.trackIndex, it.second.rendererIndex)
                val currentPos = player?.currentPosition ?: 0
                player?.seekTo(currentPos); player?.prepare(); player?.play()
                Toast.makeText(this, getString(R.string.msg_quality_lowered), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { android.util.Log.e("PlayerActivity", "Kalite düşürme hatası", e) }
    }

    private fun showDetailedErrorDialog(title: String, message: String) {
        if (!isFinishing) AlertDialog.Builder(this)
            .setTitle("⚠️ $title")
            .setMessage("$message")
            .setPositiveButton(getString(R.string.btn_ok)) { d, _ -> d.dismiss(); finish() }
            .setCancelable(false)
            .show()
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
                    0 -> showTrackSelectionDialog(C.TRACK_TYPE_VIDEO, getString(R.string.title_resolution));
                    1 -> showTrackSelectionDialog(C.TRACK_TYPE_AUDIO, getString(R.string.title_audio))
                }
            }.show()
    }

    private fun showSubtitleDialog() {
        val tracks = getTracksByType(C.TRACK_TYPE_TEXT)
        val items = tracks.map { it.name }.toMutableList()
        items.add(0, getString(R.string.option_off))
        items.add(getString(R.string.option_load_from_file))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_select_subtitle))
            .setItems(items.toTypedArray()) { _, w ->
                if (w == 0) disableTrack(C.TRACK_TYPE_TEXT)
                else if (w <= tracks.size) {
                    val t = tracks[w - 1];
                    selectTrack(C.TRACK_TYPE_TEXT, t.groupIndex, t.trackIndex, t.rendererIndex)
                } else openFilePicker()
            }.show()
    }

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

                            // DÜZELTİLEN KISIM:
                            val name = when (type) {
                                C.TRACK_TYPE_VIDEO -> "${f.width}x${f.height}"
                                C.TRACK_TYPE_AUDIO -> "${f.language ?: "und"}"
                                C.TRACK_TYPE_TEXT -> {
                                    val lang = f.language ?: "und"
                                    // Eğer etiket yoksa strings.xml'den "Bilinmeyen" çek
                                    val label = f.label ?: getString(R.string.label_unknown)
                                    "$lang ($label)"
                                }
                                else -> "?"
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
        if (tracks.isEmpty()) { Toast.makeText(this, getString(R.string.msg_no_options), Toast.LENGTH_SHORT).show(); return }
        val items = tracks.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle(title).setItems(items) { _, w -> val t = tracks[w]; selectTrack(type, t.groupIndex, t.trackIndex, t.rendererIndex) }.show()
    }

    private fun selectTrack(type: Int, g: Int, t: Int, r: Int?) {
        val info = trackSelector?.currentMappedTrackInfo ?: return
        val rIdx = r ?: (0 until info.rendererCount).find { info.getRendererType(it) == type } ?: return
        val override = TrackSelectionOverride(info.getTrackGroups(rIdx)[g], t)
        trackSelector?.parameters = trackSelector!!.buildUponParameters().setOverrideForType(override).build()
        Toast.makeText(this, getString(R.string.msg_selected), Toast.LENGTH_SHORT).show()
    }

    private fun disableTrack(type: Int) {
        trackSelector?.parameters = trackSelector!!.buildUponParameters().setTrackTypeDisabled(type, true).build()
        Toast.makeText(this, getString(R.string.msg_disabled), Toast.LENGTH_SHORT).show()
    }

    private fun openFilePicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        subtitlePickerLauncher.launch(i)
    }

    private fun addExternalSubtitle(uri: Uri) {
        if (player == null) return
        val media = player?.currentMediaItem ?: return
        val sub = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("tr").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val newMedia = media.buildUpon().setSubtitleConfigurations(listOf(sub)).build()
        val pos = player?.currentPosition ?: 0
        player?.setMediaItem(newMedia); player?.seekTo(pos); player?.prepare(); player?.play()
        Toast.makeText(this, getString(R.string.msg_loaded), Toast.LENGTH_SHORT).show()
    }

    private fun saveProgress() {
        if (player == null || streamType == "live") return
        val pos = player!!.currentPosition; val dur = player!!.duration
        val watched = (System.currentTimeMillis() - startTime) / 1000
        val finished = (dur > 0) && (pos >= (dur * 0.95))
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val exist = db.interactionDao().getInteraction(streamId, streamType!!)
            val total = (exist?.durationSeconds ?: 0) + watched
            val interact = Interaction(id = exist?.id ?: 0, streamId = streamId, streamType = streamType!!, categoryId = categoryId ?: "0", durationSeconds = total, lastPosition = if (finished) 0 else pos, maxDuration = dur, isFinished = finished)
            db.interactionDao().logInteraction(interact)
        }
    }
    // --- PIP (Pencere İçinde Pencere) MODU BAŞLANGICI ---

    // 1. Kullanıcı uygulamadan çıkarken (Home tuşu veya Gesture) PiP'i tetikle
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Eğer player oynuyorsa PiP moduna gir
            if (player != null && player!!.isPlaying) {
                // 16:9 Oranında bir pencere ayarla
                val aspectRatio = android.util.Rational(16, 9)
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    // 2. PiP Moduna girip çıkarken ekranı düzenle (Butonları gizle/göster)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            // PiP Moduna GİRDİ: Kontrolleri GİZLE
            playerView?.useController = false // Player çubuğunu kapat
            btnBack.visibility = View.GONE
            btnSettings.visibility = View.GONE
            btnSubtitle.visibility = View.GONE
            btnFavoritePlayer.visibility = View.GONE
            topControls?.visibility = View.GONE
            textNetworkSpeed.visibility = View.GONE
            textResolution.visibility = View.GONE
            btnNextEpisode.visibility = View.GONE
        } else {
            // PiP Modundan ÇIKTI: Kontrolleri GÖSTER
            playerView?.useController = true // Player çubuğunu aç
            btnBack.visibility = View.VISIBLE
            // Diğer butonların görünürlüğü, önceki durumlarına göre ayarlanmalı
            // Basitçe görünür yapabiliriz veya playerView listener'ı zaten halleder.
            // Biz sadece ana butonları açalım:
            if (streamType != "series") {
                btnFavoritePlayer.visibility = View.VISIBLE
            }
            // Controller listener tetiklendiğinde diğerleri düzelir
        }
    }
    // --- PIP MODU BİTİŞİ ---

    override fun onStop() { super.onStop(); handler.removeCallbacks(progressRunnable); saveProgress(); player?.release(); player = null }
}