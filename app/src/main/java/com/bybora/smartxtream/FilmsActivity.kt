package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bybora.smartxtream.adapter.FilmAdapter
import com.bybora.smartxtream.adapter.LiveCategoryAdapter
import com.bybora.smartxtream.adapter.OnCategoryClickListener
import com.bybora.smartxtream.adapter.OnFilmClickListener
import com.bybora.smartxtream.network.LiveCategory
import com.bybora.smartxtream.network.RetrofitClient
import com.bybora.smartxtream.network.VodStream
import com.bybora.smartxtream.utils.ContentCache
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.util.Locale

class FilmsActivity : BaseActivity(), OnCategoryClickListener, OnFilmClickListener {

    private lateinit var layoutSearchCategory: TextInputLayout
    private lateinit var inputSearchCategory: TextInputEditText
    private lateinit var layoutSearchFilm: TextInputLayout
    private lateinit var inputSearchFilm: TextInputEditText
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var recyclerFilms: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textEmptyState: TextView

    private lateinit var categoryAdapter: LiveCategoryAdapter
    private lateinit var filmAdapter: FilmAdapter

    private var allCategories: List<LiveCategory> = emptyList()
    private var allFilms: List<VodStream> = emptyList()
    private var selectedCategoryId: String? = null

    private var categorySearchJob: Job? = null
    private var filmSearchJob: Job? = null

    private var serverUrl: String? = null
    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_films)

        initViews()

        if (!getIntentData()) {
            Toast.makeText(this, "Profil hatası", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupAdapters()
        setupSearchListeners()
        loadData()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        layoutSearchCategory = findViewById(R.id.layout_search_category)
        inputSearchCategory = findViewById(R.id.input_search_category)
        layoutSearchFilm = findViewById(R.id.layout_search_film)
        inputSearchFilm = findViewById(R.id.input_search_film)
        recyclerCategories = findViewById(R.id.recycler_categories)
        recyclerFilms = findViewById(R.id.recycler_films)
        progressBar = findViewById(R.id.progress_bar_loading)

        val emptyView = findViewById<View>(R.id.text_empty_state)
        textEmptyState = if (emptyView is TextView) emptyView else TextView(this)
    }

    private fun getIntentData(): Boolean {
        serverUrl = intent.getStringExtra("EXTRA_SERVER_URL")
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")
        return !(serverUrl.isNullOrEmpty() || username == null || password == null)
    }

    private fun setupAdapters() {
        categoryAdapter = LiveCategoryAdapter(this)
        recyclerCategories.adapter = categoryAdapter

        filmAdapter = FilmAdapter(this)
        // Liste görünümü için
        recyclerFilms.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerFilms.adapter = filmAdapter
    }

    private fun setupSearchListeners() {
        inputSearchCategory.addTextChangedListener {
            // Liste henüz yüklenmediyse arama yapma
            if (allCategories.isNotEmpty()) {
                performCategorySearch(it.toString(), true)
            }
        }

        inputSearchFilm.addTextChangedListener {
            if (allFilms.isNotEmpty()) {
                performFilmSearch(it.toString(), true)
            }
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        textEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            val apiService = RetrofitClient.createService(serverUrl!!)

            // 1. Kategorileri Çek (Bağımsız)
            try {
                val catResponse = apiService.getVodCategories(username!!, password!!)
                if (catResponse.isSuccessful) {
                    val rawCats = catResponse.body() ?: emptyList()
                    allCategories = rawCats.map { LiveCategory(it.categoryId, it.categoryName, 0) }
                }
            } catch (e: Exception) { allCategories = emptyList() }

            // 2. Filmleri Çek (Bağımsız)
            try {
                // Önce cache kontrolü
                if (ContentCache.cachedMovies.isNotEmpty()) {
                    allFilms = ContentCache.cachedMovies
                } else {
                    val vodResponse = apiService.getVodStreams(username!!, password!!)
                    if (vodResponse.isSuccessful) {
                        allFilms = vodResponse.body() ?: emptyList()
                    }
                }
            } catch (e: Exception) { allFilms = emptyList() }

            processAndShowData()
        }
    }

    private suspend fun processAndShowData() {
        withContext(Dispatchers.Default) {
            val counts = HashMap<String, Int>()

            val allCat = LiveCategory("0", "Tüm Filmler", 0)
            val mutableCats = mutableListOf(allCat)
            mutableCats.addAll(allCategories)
            allCategories = mutableCats

            counts["0"] = allFilms.size
            allFilms.forEach { f ->
                val cId = f.categoryId ?: "0"
                counts[cId] = (counts[cId] ?: 0) + 1
            }

            withContext(Dispatchers.Main) {
                // Kategorileri ve sayıları güncelle
                categoryAdapter.submitList(allCategories)
                categoryAdapter.setChannelCounts(counts)

                // İlk açılışta tüm filmleri göster
                filmAdapter.submitList(allFilms)
                updateEmptyState(allFilms.isEmpty())

                progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        textEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerFilms.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun performCategorySearch(txt: String, isAuto: Boolean) {
        categorySearchJob?.cancel()
        categorySearchJob = lifecycleScope.launch {
            if (isAuto) delay(300)
            val q = txt.lowercase(Locale("tr"))
            val res = allCategories.filter { it.categoryName.lowercase(Locale("tr")).contains(q) }
            categoryAdapter.submitList(res)
        }
    }

    private fun performFilmSearch(txt: String, isAuto: Boolean) {
        filmSearchJob?.cancel()
        filmSearchJob = lifecycleScope.launch {
            if (isAuto) delay(500)
            val q = txt.lowercase(Locale("tr"))
            val res = if (q.isNotEmpty()) {
                allFilms.filter { it.name?.lowercase(Locale("tr"))?.contains(q) == true }
            } else if (selectedCategoryId != null && selectedCategoryId != "0") {
                allFilms.filter { it.categoryId == selectedCategoryId }
            } else {
                allFilms
            }
            filmAdapter.submitList(res)
            updateEmptyState(res.isEmpty())
        }
    }

    override fun onCategoryClick(category: LiveCategory) {
        selectedCategoryId = category.categoryId
        inputSearchFilm.text?.clear()

        val res = if (category.categoryId == "0") allFilms
        else allFilms.filter { it.categoryId == category.categoryId }

        filmAdapter.submitList(res)
        recyclerFilms.scrollToPosition(0)
        updateEmptyState(res.isEmpty())
    }

    override fun onFilmClick(film: VodStream) {
        val intent = Intent(this, FilmDetailActivity::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_USERNAME", username)
            putExtra("EXTRA_PASSWORD", password)
            putExtra("EXTRA_STREAM_ID", film.streamId)
            putExtra("EXTRA_STREAM_TYPE", "vod")
            putExtra("EXTRA_EXTENSION", film.fileExtension)
        }
        startActivity(intent)
    }
}