package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.database.Favorite
import com.bybora.smartxtream.network.RetrofitClient
import kotlinx.coroutines.launch

class FilmDetailActivity : BaseActivity() {

    // UI
    private lateinit var imgBackdrop: ImageView
    private lateinit var imgPoster: ImageView
    private lateinit var textTitle: TextView
    private lateinit var textRating: TextView
    private lateinit var textGenre: TextView
    private lateinit var textDescription: TextView
    private lateinit var textCast: TextView
    private lateinit var textDirector: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnFavorite: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: View

    // Veri
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var streamId: Int = -1
    private var currentExtension: String = "mp4"
    private var directSource: String? = null

    // DB
    private val db by lazy { AppDatabase.getInstance(this) }
    private var isFav = false
    private var currentMovieName = ""
    private var currentMovieImage = ""
    private var categoryId = "0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_film_detail)

        initViews()

        if (getIntentData()) {
            fetchMovieDetails()
            checkFavoriteStatus()
        } else {
            finish()
        }
    }

    private fun initViews() {
        imgBackdrop = findViewById(R.id.img_backdrop)
        imgPoster = findViewById(R.id.img_poster)
        textTitle = findViewById(R.id.text_title)
        textRating = findViewById(R.id.text_rating)
        textGenre = findViewById(R.id.text_genre)
        textDescription = findViewById(R.id.text_plot)
        textCast = findViewById(R.id.text_cast)
        textDirector = findViewById(R.id.text_director)
        btnPlay = findViewById(R.id.btn_play)
        btnFavorite = findViewById(R.id.btn_favorite)
        progressBar = findViewById(R.id.progress_loader)

        layoutContent = (findViewById<View>(R.id.text_title)?.parent as? View) ?: findViewById(android.R.id.content)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnPlay.setOnClickListener { playMovie() }
        btnFavorite.setOnClickListener { toggleFavorite() }
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        streamId = intent.getIntExtra("EXTRA_STREAM_ID", -1)
        return !(serverUrl.isNullOrEmpty() || streamId == -1)
    }

    private fun fetchMovieDetails() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.createService(serverUrl!!)
                val response = apiService.getVodInfo(username!!, password!!, vodId = streamId)

                if (response.isSuccessful && response.body() != null) {
                    val info = response.body()!!.info
                    val movieData = response.body()!!.movieData

                    currentMovieName = info?.name ?: getString(R.string.film_name)
                    currentMovieImage = info?.image ?: ""
                    currentExtension = movieData?.extension ?: "mp4"
                    directSource = movieData?.directSource

                    // --- DÜZELTME BAŞLANGICI: Strings.xml kullanımı ---
                    textTitle.text = currentMovieName

                    // Açıklama yoksa dil dosyasından "Açıklama Bulunamadı" yazısını al
                    textDescription.text = if (info?.plot.isNullOrEmpty()) getString(R.string.text_no_description) else info?.plot

                    // Puan yoksa N/A yaz, varsa başına IMDB ekle (Strings.xml'den)
                    val ratingLabel = getString(R.string.label_rating) // "IMDB:"
                    textRating.text = "$ratingLabel ${info?.rating ?: "N/A"}"

                    val year = info?.releaseDate ?: ""
                    val duration = info?.duration ?: ""
                    val genre = info?.genre ?: getString(R.string.text_genre_default) // "Genel"

                    // Yıl | Süre | Tür formatı
                    textGenre.text = listOf(year, duration, genre).filter { it.isNotEmpty() }.joinToString(" | ")

                    textCast.text = if (info?.cast.isNullOrEmpty()) "-" else info?.cast
                    textDirector.text = if (info?.director.isNullOrEmpty()) "-" else info?.director
                    // --- DÜZELTME BİTİŞİ ---

                    if (!info?.image.isNullOrEmpty()) {
                        Glide.with(this@FilmDetailActivity).load(info.image).into(imgPoster)
                        Glide.with(this@FilmDetailActivity).load(info.image).into(imgBackdrop)
                    }

                    categoryId = movieData?.categoryId ?: "0"

                } else {
                    Toast.makeText(this@FilmDetailActivity, getString(R.string.error_fetch_details), Toast.LENGTH_SHORT).show()
                }
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@FilmDetailActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playMovie() {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", streamId)
            putExtra("EXTRA_STREAM_TYPE", "vod")
            putExtra("EXTRA_STREAM_NAME", currentMovieName)
            putExtra("EXTRA_STREAM_ICON", currentMovieImage)
            putExtra("EXTRA_EXTENSION", currentExtension)
            if (directSource != null) putExtra("EXTRA_DIRECT_URL", directSource)
        }
        startActivity(intent)
    }

    private fun checkFavoriteStatus() {
        lifecycleScope.launch {
            isFav = db.favoriteDao().isFavorite(streamId, "vod")
            updateFavIcon()
        }
    }

    private fun toggleFavorite() {
        lifecycleScope.launch {
            if (isFav) {
                db.favoriteDao().removeFavorite(streamId, "vod")
                isFav = false
                Toast.makeText(this@FilmDetailActivity, getString(R.string.msg_fav_removed), Toast.LENGTH_SHORT).show()
            } else {
                val fav = Favorite(
                    streamId = streamId,
                    streamType = "vod",
                    name = currentMovieName,
                    image = currentMovieImage,
                    categoryId = categoryId
                )
                db.favoriteDao().addFavorite(fav)
                isFav = true
                Toast.makeText(this@FilmDetailActivity, getString(R.string.msg_fav_added), Toast.LENGTH_SHORT).show()
            }
            updateFavIcon()
        }
    }

    private fun updateFavIcon() {
        val icon = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        btnFavorite.setImageResource(icon)
    }
}