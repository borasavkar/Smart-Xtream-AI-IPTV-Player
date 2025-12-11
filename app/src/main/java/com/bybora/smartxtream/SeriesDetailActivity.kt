package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bybora.smartxtream.adapter.EpisodeAdapter
import com.bybora.smartxtream.adapter.OnEpisodeClickListener
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.database.Favorite
import com.bybora.smartxtream.network.ApiService
import com.bybora.smartxtream.network.Episode
import com.bybora.smartxtream.network.RetrofitClient
import kotlinx.coroutines.launch

class SeriesDetailActivity : BaseActivity(), OnEpisodeClickListener {

    // UI
    private lateinit var recyclerEpisodes: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var imgBackdrop: ImageView
    private lateinit var imgPoster: ImageView
    private lateinit var textTitle: TextView
    private lateinit var textInfo: TextView
    private lateinit var textPlot: TextView
    private lateinit var btnFavorite: ImageButton

    // Veri
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var seriesId: Int = -1

    private var apiService: ApiService? = null
    private lateinit var episodeAdapter: EpisodeAdapter

    private val db by lazy { AppDatabase.getInstance(this) }
    private var isFav = false
    private var currentSeriesName = ""
    private var currentSeriesImage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_detail)

        initViews()

        if (getIntentData()) {
            apiService = RetrofitClient.createService(serverUrl!!)
            setupRecyclerView()
            fetchSeriesDetails()
        } else {
            finish()
        }
    }

    private fun initViews() {
        recyclerEpisodes = findViewById(R.id.recycler_view_episodes)
        progressBar = findViewById(R.id.episode_list_progress)
        imgBackdrop = findViewById(R.id.img_backdrop)
        imgPoster = findViewById(R.id.img_poster)
        textTitle = findViewById(R.id.text_title)
        textInfo = findViewById(R.id.text_info)
        textPlot = findViewById(R.id.text_plot)
        btnFavorite = findViewById(R.id.btn_favorite_series)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnFavorite.setOnClickListener { toggleFavorite() }
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        seriesId = intent.getIntExtra("EXTRA_SERIES_ID", -1)
        return !(serverUrl.isNullOrEmpty() || seriesId == -1)
    }

    private fun setupRecyclerView() {
        episodeAdapter = EpisodeAdapter(this)
        recyclerEpisodes.layoutManager = LinearLayoutManager(this)
        recyclerEpisodes.adapter = episodeAdapter
    }

    private fun fetchSeriesDetails() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = apiService?.getSeriesInfo(username!!, password!!, seriesId = seriesId)

                val watchedList = db.interactionDao().getAllSeriesInteractions()
                episodeAdapter.updateWatchedStatus(watchedList)

                if (response != null && response.isSuccessful) {
                    val info = response.body()?.info
                    val episodesMap = response.body()?.episodes

                    currentSeriesName = info?.name ?: getString(R.string.series_header)
                    currentSeriesImage = info?.cover ?: ""

                    // --- DÜZELTME BAŞLANGICI: Strings.xml kullanımı ---
                    textTitle.text = currentSeriesName
                    textPlot.text = if (info?.plot.isNullOrEmpty()) getString(R.string.text_no_description) else info.plot

                    val labelRating = getString(R.string.label_rating) // "IMDB:"
                    val rating = info?.rating ?: "N/A"
                    val genre = info?.genre ?: getString(R.string.text_genre_default) // "Genel"

                    textInfo.text = getString(R.string.format_series_info, labelRating, rating, genre)
                    // --- DÜZELTME BİTİŞİ ---

                    if (currentSeriesImage.isNotEmpty()) {
                        Glide.with(this@SeriesDetailActivity).load(currentSeriesImage).into(imgPoster)
                        Glide.with(this@SeriesDetailActivity).load(currentSeriesImage).into(imgBackdrop)
                    }

                    val allEpisodes = episodesMap?.values?.flatten() ?: emptyList()
                    episodeAdapter.submitList(allEpisodes)

                    checkFavoriteStatus()
                }
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                // Hatayı Logcat'e bas (Sadece geliştirici görür)
                android.util.Log.e("SeriesDetail", "Dizi detayı hatası", e)

                progressBar.visibility = View.GONE
                Toast.makeText(this@SeriesDetailActivity, getString(R.string.error_fetch_details), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkFavoriteStatus() {
        lifecycleScope.launch {
            isFav = db.favoriteDao().isFavorite(seriesId, "series")
            updateFavIcon()
        }
    }

    private fun toggleFavorite() {
        lifecycleScope.launch {
            if (isFav) {
                db.favoriteDao().removeFavorite(seriesId, "series")
                isFav = false
                Toast.makeText(this@SeriesDetailActivity, getString(R.string.msg_fav_removed), Toast.LENGTH_SHORT).show()
            } else {
                val fav = Favorite(
                    streamId = seriesId,
                    streamType = "series",
                    name = currentSeriesName,
                    image = currentSeriesImage,
                    categoryId = "0"
                )
                db.favoriteDao().addFavorite(fav)
                isFav = true
                Toast.makeText(this@SeriesDetailActivity, getString(R.string.msg_fav_added), Toast.LENGTH_SHORT).show()
            }
            updateFavIcon()
        }
    }

    private fun updateFavIcon() {
        val icon = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        btnFavorite.setImageResource(icon)
    }

    override fun onResume() {
        super.onResume()
        if (this::episodeAdapter.isInitialized) {
            lifecycleScope.launch {
                val watchedList = db.interactionDao().getAllSeriesInteractions()
                episodeAdapter.updateWatchedStatus(watchedList)
            }
        }
    }

    override fun onEpisodeClick(episode: Episode) {
        val streamId = episode.id.toIntOrNull() ?: return
        val currentList = episodeAdapter.currentList
        val episodeIds = ArrayList(currentList.mapNotNull { it.id.toIntOrNull() })

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", streamId)
            putExtra("EXTRA_STREAM_TYPE", "series")
            putExtra("EXTRA_EXTENSION", episode.fileExtension ?: "mp4")
            putExtra("EXTRA_CATEGORY_ID", "0")
            putIntegerArrayListExtra("EXTRA_EPISODE_LIST", episodeIds)
        }
        startActivity(intent)
    }
}