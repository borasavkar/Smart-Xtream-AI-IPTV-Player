package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bybora.smartxtream.adapter.ChannelAdapter
import com.bybora.smartxtream.adapter.LiveCategoryAdapter
import com.bybora.smartxtream.adapter.OnCategoryClickListener
import com.bybora.smartxtream.adapter.OnChannelClickListener
import com.bybora.smartxtream.network.ChannelWithEpg
import com.bybora.smartxtream.network.EpgListing
import com.bybora.smartxtream.network.LiveCategory
import com.bybora.smartxtream.network.RetrofitClient
import com.bybora.smartxtream.utils.ContentCache
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Locale

class LiveCategoryActivity : BaseActivity(), OnCategoryClickListener, OnChannelClickListener {

    // Sol Taraf
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var inputSearchCategory: TextInputEditText
    private lateinit var categoryAdapter: LiveCategoryAdapter
    private var allCategories: List<LiveCategory> = emptyList()

    // Sağ Taraf
    private lateinit var recyclerChannels: RecyclerView
    private lateinit var inputSearchChannel: TextInputEditText
    private lateinit var channelAdapter: ChannelAdapter
    private var currentChannels: List<ChannelWithEpg> = emptyList()

    private lateinit var progressBar: ProgressBar
    private lateinit var textEmptyState: TextView

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_category)

        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")

        if (serverUrl.isNullOrEmpty()) {
            finish()
            return
        }

        initViews()
        setupAdapters()
        setupSearch()
        fetchCategories()
    }

    private fun initViews() {
        recyclerCategories = findViewById(R.id.recycler_categories)
        recyclerChannels = findViewById(R.id.recycler_channels)
        inputSearchCategory = findViewById(R.id.input_search_category)
        inputSearchChannel = findViewById(R.id.input_search_channel)
        progressBar = findViewById(R.id.progress_bar_loading)

        val emptyView = findViewById<View>(R.id.text_empty_state)
        textEmptyState = if (emptyView is TextView) emptyView else TextView(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupAdapters() {
        categoryAdapter = LiveCategoryAdapter(this)
        recyclerCategories.adapter = categoryAdapter

        channelAdapter = ChannelAdapter(this, R.layout.item_channel)
        recyclerChannels.adapter = channelAdapter
    }

    private fun setupSearch() {
        inputSearchCategory.addTextChangedListener { text ->
            val query = text.toString().lowercase(Locale("tr"))
            val filtered = allCategories.filter { it.categoryName.lowercase(Locale("tr")).contains(query) }
            categoryAdapter.submitList(filtered)
        }

        inputSearchChannel.addTextChangedListener { text ->
            val query = text.toString().lowercase(Locale("tr"))
            val filtered = currentChannels.filter { it.channel.name?.lowercase(Locale("tr"))?.contains(query) == true }
            channelAdapter.submitList(filtered)
        }
    }

    private fun fetchCategories() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 1. Kategorileri API'den çek
                val apiService = RetrofitClient.createService(serverUrl!!)
                val response = apiService.getLiveCategories(username!!, password!!)

                if (response.isSuccessful && response.body() != null) {
                    allCategories = response.body() ?: emptyList()

                    // --- GÜVENLİK ÖNLEMİ ---
                    // Eğer önbellek boşsa, kanalları burada indir
                    if (ContentCache.cachedChannels.isEmpty()) {
                        try {
                            val streamResp = apiService.getLiveStreams(username!!, password!!)
                            if (streamResp.isSuccessful) {
                                ContentCache.cachedChannels = streamResp.body() ?: emptyList()
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }

                    // 2. KANAL SAYILARINI HESAPLA
                    val allChannels = ContentCache.cachedChannels
                    val counts = HashMap<String, Int>()

                    allChannels.forEach { ch ->
                        val catId = ch.categoryId ?: "0"
                        counts[catId] = (counts[catId] ?: 0) + 1
                    }

                    categoryAdapter.setChannelCounts(counts)
                    categoryAdapter.submitList(allCategories)

                    // 3. İlk kategoriyi otomatik seç
                    if (allCategories.isNotEmpty()) {
                        loadChannelsForCategory(allCategories[0].categoryId)
                    } else {
                        progressBar.visibility = View.GONE
                    }
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LiveCategoryActivity, "Kategori bulunamadı", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@LiveCategoryActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadChannelsForCategory(categoryId: String) {
        textEmptyState.visibility = View.GONE

        val allChannels = ContentCache.cachedChannels
        val filtered = allChannels.filter { it.categoryId == categoryId }

        val epgList = ContentCache.cachedEpg
        currentChannels = filtered.map { ch ->
            val epgNow = epgList?.find { it.epgId == ch.streamId.toString() }
                ?: EpgListing("0", "0", "Canlı Yayın", "", "", "")
            ChannelWithEpg(ch, epgNow)
        }

        channelAdapter.submitList(currentChannels)

        if (currentChannels.isEmpty()) {
            textEmptyState.visibility = View.VISIBLE
            textEmptyState.text = "Bu kategoride kanal yok."
        }

        progressBar.visibility = View.GONE
    }

    override fun onCategoryClick(category: LiveCategory) {
        inputSearchChannel.text?.clear()
        loadChannelsForCategory(category.categoryId)
    }

    override fun onChannelClick(channelWithEpg: ChannelWithEpg) {
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