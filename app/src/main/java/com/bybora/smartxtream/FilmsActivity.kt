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
import androidx.recyclerview.widget.LinearLayoutManager
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
            // DÜZELTME: Çevrilebilir hata mesajı
            Toast.makeText(this, getString(R.string.error_profile_error), Toast.LENGTH_SHORT).show()
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

        // Hata önleyici
        textEmptyState = try { findViewById(R.id.text_empty_state) } catch (e: Exception) { TextView(this) }
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
        recyclerFilms.layoutManager = LinearLayoutManager(this)
        recyclerFilms.adapter = filmAdapter
    }

    private fun setupSearchListeners() {
        inputSearchCategory.addTextChangedListener {
            if (allCategories.isNotEmpty()) performCategorySearch(it.toString(), true)
        }
        inputSearchFilm.addTextChangedListener {
            if (allFilms.isNotEmpty()) performFilmSearch(it.toString(), true)
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        textEmptyState.visibility = View.GONE

        // 1. ÖNCE HAFIZAYA BAK (Hızlı Açılış)
        if (ContentCache.cachedMovies.isNotEmpty()) {
            allFilms = ContentCache.cachedMovies
            lifecycleScope.launch { fetchCategoriesAndShow() }
        } else {
            // 2. HAFIZA BOŞSA İNDİR
            lifecycleScope.launch { fetchFromNetwork() }
        }
    }

    private suspend fun fetchFromNetwork() {
        val apiService = RetrofitClient.createService(serverUrl!!)

        try {
            val vodResponse = apiService.getVodStreams(username!!, password!!)
            if (vodResponse.isSuccessful) {
                allFilms = vodResponse.body() ?: emptyList()
                ContentCache.cachedMovies = allFilms
                fetchCategoriesAndShow()
            } else {
                // DÜZELTME: Çevrilebilir hata mesajı + Hata kodu
                showError(getString(R.string.error_vod_list_fetch, vodResponse.code().toString()))
            }
        } catch (e: Exception) {
            // DÜZELTME: Çevrilebilir bağlantı hatası
            showError(getString(R.string.error_connection_msg, e.message ?: ""))
            allFilms = emptyList()
            fetchCategoriesAndShow() // Hata olsa bile devam et (Belki kategori gelir)
        }
    }

    private suspend fun fetchCategoriesAndShow() {
        val apiService = RetrofitClient.createService(serverUrl!!)

        try {
            val catResponse = apiService.getVodCategories(username!!, password!!)
            if (catResponse.isSuccessful) {
                val rawCats = catResponse.body() ?: emptyList()
                allCategories = rawCats.map { LiveCategory(it.categoryId, it.categoryName, 0) }
            }
        } catch (e: Exception) {
            allCategories = emptyList()
        }

        // YEDEK PLAN: Kategori listesi boşsa, filmlerden kategori üret
        if (allCategories.isEmpty() && allFilms.isNotEmpty()) {
            val catMap = HashMap<String, String>()
            allFilms.forEach {
                if (it.categoryId != null) {
                    // DÜZELTME: "Kategori 1" gibi isimler çevrilebilir formatta
                    catMap[it.categoryId] = getString(R.string.format_category_default, it.categoryId)
                }
            }
            allCategories = catMap.map { LiveCategory(it.key, it.value, 0) }
        }

        processAndShowData()
    }

    private suspend fun processAndShowData() {
        withContext(Dispatchers.Default) {
            val counts = HashMap<String, Int>()
            // DÜZELTME: "Tüm Filmler" çevrildi
            val allCat = LiveCategory("0", getString(R.string.category_all_movies), 0)
            val mutableCats = ArrayList<LiveCategory>()
            mutableCats.add(allCat)
            mutableCats.addAll(allCategories)
            allCategories = mutableCats

            counts["0"] = allFilms.size
            allFilms.forEach { f ->
                val cId = f.categoryId ?: "0"
                counts[cId] = (counts[cId] ?: 0) + 1
            }

            withContext(Dispatchers.Main) {
                categoryAdapter.submitList(allCategories)
                categoryAdapter.setChannelCounts(counts)

                // Varsayılan olarak tümünü göster
                filmAdapter.submitList(allFilms)
                updateEmptyState(allFilms.isEmpty())

                progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        textEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerFilms.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            // DÜZELTME: Çevrilebilir boş durum mesajı
            textEmptyState.text = getString(R.string.msg_content_not_found_or_loaded)
        }
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

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        // Kullanıcıya hatayı göster
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        updateEmptyState(true)
    }
}