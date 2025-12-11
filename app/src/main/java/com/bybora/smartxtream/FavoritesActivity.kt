package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager // <-- Grid Düzeni için
import androidx.recyclerview.widget.RecyclerView
import com.bybora.smartxtream.adapter.ChannelAdapter
import com.bybora.smartxtream.adapter.OnChannelClickListener
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.network.ChannelWithEpg
import com.bybora.smartxtream.network.EpgListing
import com.bybora.smartxtream.network.LiveStream
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesActivity : BaseActivity(), OnChannelClickListener {

    private lateinit var recyclerFavorites: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    // Veriler
    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        recyclerFavorites = findViewById(R.id.recycler_favorites)

        // --- DÜZELTME 1: Izgara Görünümü (Yan yana 3 poster) ---
        recyclerFavorites.layoutManager = GridLayoutManager(this, 3)

        // --- DÜZELTME 2: Film Kartı Tasarımı Kullan (Resimli) ---
        adapter = ChannelAdapter(this, R.layout.item_movie_card)

        recyclerFavorites.adapter = adapter

        loadFavorites()
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            db.favoriteDao().getAllFavorites().collectLatest { favList ->
                val list = favList.map { fav ->
                    val stream = LiveStream(
                        streamId = fav.streamId,
                        name = fav.name,
                        streamIcon = fav.image,
                        categoryId = fav.categoryId
                    )

                    // 1. GÖRÜNEN İSİM (Kullanıcı için çevrilir)
                    val displayLabel = when(fav.streamType) {
                        "vod" -> getString(R.string.type_movie)  // "Film" veya "Movie"
                        "series" -> getString(R.string.type_series) // "Dizi" veya "Series"
                        else -> getString(R.string.type_live)    // "Canlı Yayın" veya "Live"
                    }

                    // 2. TEKNİK İSİM (Kod için saklanır - fav.streamType: "vod", "series")
                    // EpgListing'in son parametresi olan 'description' alanına teknik tipi saklıyoruz.
                    val dummyEpg = EpgListing("0", "0", displayLabel, "", "", fav.streamType)

                    ChannelWithEpg(stream, dummyEpg)
                }
                adapter.submitList(list)
            }
        }
    }

    override fun onChannelClick(channelWithEpg: ChannelWithEpg) {
        val type = channelWithEpg.epgNow?.description ?: "live"
        if (type == "series") {
            val intent = Intent(this, SeriesDetailActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_SERIES_ID", channelWithEpg.channel.streamId)
            }
            startActivity(intent)
        } else if (type == "vod") {
            val intent = Intent(this, FilmDetailActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_STREAM_ID", channelWithEpg.channel.streamId)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("EXTRA_SERVER_URL", serverUrl)
                putExtra("EXTRA_USERNAME", username)
                putExtra("EXTRA_PASSWORD", password)
                putExtra("EXTRA_STREAM_ID", channelWithEpg.channel.streamId)
                putExtra("EXTRA_STREAM_TYPE", "live")
                putExtra("EXTRA_STREAM_NAME", channelWithEpg.channel.name)
                putExtra("EXTRA_STREAM_ICON", channelWithEpg.channel.streamIcon)
            }
            startActivity(intent)
        }
    }
}