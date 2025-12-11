package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager // DÜZELTME
import androidx.recyclerview.widget.RecyclerView
import com.bybora.smartxtream.adapter.LiveCategoryAdapter
import com.bybora.smartxtream.adapter.OnCategoryClickListener
import com.bybora.smartxtream.adapter.OnSeriesClickListener
import com.bybora.smartxtream.adapter.SeriesAdapter
import com.bybora.smartxtream.network.LiveCategory
import com.bybora.smartxtream.network.RetrofitClient
import com.bybora.smartxtream.network.SeriesStream
import com.bybora.smartxtream.utils.ContentCache
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale

class SeriesListActivity : BaseActivity(), OnCategoryClickListener, OnSeriesClickListener {

    private lateinit var recyclerCategories: RecyclerView
    private lateinit var inputSearchCategory: TextInputEditText
    private lateinit var categoryAdapter: LiveCategoryAdapter
    private var allCategories: List<LiveCategory> = emptyList()

    private lateinit var recyclerSeries: RecyclerView
    private lateinit var inputSearchSeries: TextInputEditText
    private lateinit var seriesAdapter: SeriesAdapter

    private var allSeriesList: List<SeriesStream> = emptyList()
    private var currentSeriesList: List<SeriesStream> = emptyList()

    private lateinit var progressBar: ProgressBar
    private lateinit var textEmptyState: TextView

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_list)

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
        loadData()
    }

    private fun initViews() {
        recyclerCategories = findViewById(R.id.recycler_categories)
        recyclerSeries = findViewById(R.id.recycler_series)
        inputSearchCategory = findViewById(R.id.input_search_category)
        inputSearchSeries = findViewById(R.id.input_search_series)
        progressBar = findViewById(R.id.progress_bar_loading)

        val emptyView = findViewById<View>(R.id.text_empty_state)
        textEmptyState = if (emptyView is TextView) emptyView else TextView(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupAdapters() {
        categoryAdapter = LiveCategoryAdapter(this)
        recyclerCategories.adapter = categoryAdapter

        seriesAdapter = SeriesAdapter(this)
        // DÜZELTME: Liste görünümü
        recyclerSeries.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerSeries.adapter = seriesAdapter
    }

    // ... (setupSearch, loadData vb. aynı kalacak, sadece layoutManager değiştiği için liste görünecek) ...
    // Kısaca loadData'yı da ekliyorum garanti olsun:

    private fun setupSearch() {
        inputSearchCategory.addTextChangedListener { text ->
            val query = text.toString().lowercase(Locale("tr"))
            val filtered = allCategories.filter { it.categoryName.lowercase(Locale("tr")).contains(query) }
            categoryAdapter.submitList(filtered)
        }

        inputSearchSeries.addTextChangedListener { text ->
            val query = text.toString().lowercase(Locale("tr"))
            val filtered = currentSeriesList.filter { it.name?.lowercase(Locale("tr"))?.contains(query) == true }
            seriesAdapter.submitList(filtered)
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val apiService = RetrofitClient.createService(serverUrl!!)

            try {
                val catResponse = apiService.getSeriesCategories(username!!, password!!)
                if (catResponse.isSuccessful) allCategories = catResponse.body() ?: emptyList()
            } catch (e: Exception) { allCategories = emptyList() }

            try {
                if (ContentCache.cachedSeries.isNotEmpty()) {
                    allSeriesList = ContentCache.cachedSeries
                } else {
                    val seriesResponse = apiService.getSeries(username!!, password!!)
                    if (seriesResponse.isSuccessful) allSeriesList = seriesResponse.body() ?: emptyList()
                }
            } catch (e: Exception) { allSeriesList = emptyList() }

            processAndShowData()
        }
    }

    private suspend fun processAndShowData() {
        withContext(Dispatchers.Default) {
            val counts = HashMap<String, Int>()
            allSeriesList.forEach { s ->
                val cId = s.categoryId ?: "0"
                counts[cId] = (counts[cId] ?: 0) + 1
            }

            val allCat = LiveCategory("0", getString(R.string.category_all_series), 0)
            if (allCategories.none { it.categoryId == "0" }) {
                allCategories = listOf(allCat) + allCategories
            }
            counts["0"] = allSeriesList.size

            withContext(Dispatchers.Main) {
                categoryAdapter.setChannelCounts(counts)
                categoryAdapter.submitList(allCategories)

                if (allCategories.isNotEmpty()) {
                    filterSeriesByCategory(allCategories[0].categoryId)
                } else {
                    currentSeriesList = allSeriesList
                    seriesAdapter.submitList(currentSeriesList)
                    updateEmptyState(currentSeriesList.isEmpty())
                }
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        textEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerSeries.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) textEmptyState.text = getString(R.string.msg_content_not_found)
    }

    private fun filterSeriesByCategory(categoryId: String) {
        currentSeriesList = if (categoryId == "0") allSeriesList else allSeriesList.filter { it.categoryId == categoryId }
        seriesAdapter.submitList(currentSeriesList)
        updateEmptyState(currentSeriesList.isEmpty())
    }

    override fun onCategoryClick(category: LiveCategory) {
        inputSearchSeries.text?.clear()
        filterSeriesByCategory(category.categoryId)
    }

    override fun onSeriesClick(series: SeriesStream) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_SERIES_ID", series.seriesId)
            putExtra("EXTRA_STREAM_NAME", series.name)
            putExtra("EXTRA_STREAM_ICON", series.cover ?: series.streamIcon)
        }
        startActivity(intent)
    }
}