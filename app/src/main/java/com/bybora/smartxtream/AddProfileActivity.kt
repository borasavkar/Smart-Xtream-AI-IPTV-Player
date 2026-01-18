package com.bybora.smartxtream

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bybora.smartxtream.database.AppDatabase
import com.bybora.smartxtream.database.Profile
import com.bybora.smartxtream.network.RetrofitClient
import com.bybora.smartxtream.utils.SettingsManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class AddProfileActivity : BaseActivity() {

    private lateinit var textTitle: TextView
    private lateinit var layoutProfileName: TextInputLayout
    private lateinit var editTextProfileName: TextInputEditText
    private lateinit var layoutUsername: TextInputLayout
    private lateinit var editTextUsername: TextInputEditText
    private lateinit var layoutPassword: TextInputLayout
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var layoutServerUrl: TextInputLayout
    private lateinit var editTextServerUrl: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var progressBar: ProgressBar

    private val db by lazy { AppDatabase.getInstance(this) }
    private val profileDao by lazy { db.profileDao() }

    private var isEditMode = false
    private var editingProfileId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_profile)

        // UI Tanımlamaları
        textTitle = findViewById(R.id.title)
        layoutProfileName = findViewById(R.id.layout_profile_name)
        editTextProfileName = findViewById(R.id.edit_text_profile_name)
        layoutUsername = findViewById(R.id.layout_username)
        editTextUsername = findViewById(R.id.edit_text_username)
        layoutPassword = findViewById(R.id.layout_password)
        editTextPassword = findViewById(R.id.edit_text_password)
        layoutServerUrl = findViewById(R.id.layout_server_url)
        editTextServerUrl = findViewById(R.id.edit_text_server_url)
        buttonSave = findViewById(R.id.button_save)
        progressBar = findViewById(R.id.progress_bar)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        if (intent.hasExtra("EXTRA_EDIT_ID")) {
            isEditMode = true
            editingProfileId = intent.getIntExtra("EXTRA_EDIT_ID", -1)
            loadProfileData(editingProfileId)
            textTitle.text = getString(R.string.title_edit_profile)
            buttonSave.text = getString(R.string.btn_update_profile)
        } else {
            textTitle.text = getString(R.string.title_add_profile)
            buttonSave.text = getString(R.string.btn_save)
        }

        buttonSave.setOnClickListener {
            validateAndSaveProfile()
        }
    }

    private fun loadProfileData(id: Int) {
        lifecycleScope.launch {
            val profile = profileDao.getProfileById(id)
            if (profile != null) {
                editTextProfileName.setText(profile.profileName)
                editTextUsername.setText(profile.username)
                editTextPassword.setText(profile.password)
                editTextServerUrl.setText(profile.serverUrl)
            }
        }
    }

    private fun validateAndSaveProfile() {
        val serverUrl = editTextServerUrl.text.toString().trim()
        var profileName = editTextProfileName.text.toString().trim()

        if (profileName.isEmpty()) {
            profileName = getString(R.string.default_profile_name)
        } else {
            profileName = profileName.substring(0, 1).uppercase() + profileName.substring(1)
        }

        val username = editTextUsername.text.toString().trim()
        val password = editTextPassword.text.toString().trim()

        if (!validateInputs(profileName, username, serverUrl)) return

        showLoading(true)

        lifecycleScope.launch {
            try {
                // API Doğrulaması (Giriş Yapılıyor...)
                val apiService = RetrofitClient.createService(serverUrl)
                val response = apiService.authenticate(username, password)

                if (response.isSuccessful && response.body()?.userInfo?.auth == 1) {
                    // Giriş Başarılı -> Veritabanına Kaydet
                    saveProfileToDb(serverUrl, profileName, username, password)
                } else {
                    showError(getString(R.string.error_login_failed_check))
                }
            } catch (e: Exception) {
                Log.e("AddProfile", "Error: ${e.message}")
                showError(getString(R.string.error_network_connection))
            }
        }
    }

    private fun saveProfileToDb(url: String, name: String, user: String, pass: String) {
        lifecycleScope.launch {
            val profileToSave = Profile(
                id = if (isEditMode) editingProfileId else 0,
                profileName = name,
                serverUrl = url,
                username = user,
                password = pass,
                isM3u = false
            )

            if (isEditMode) {
                profileDao.updateProfile(profileToSave)
                SettingsManager.saveSelectedProfileId(this@AddProfileActivity, profileToSave.id)
                showToast(R.string.msg_profile_updated)
            } else {
                val newId = profileDao.insertProfile(profileToSave)
                SettingsManager.saveSelectedProfileId(this@AddProfileActivity, newId.toInt())
                showToast(R.string.msg_profile_saved)
            }

            // Kayıt bitti, şimdi Ana Sayfayı temiz bir şekilde başlat
            val intent = Intent(this@AddProfileActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showError(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            buttonSave.visibility = View.INVISIBLE
        } else {
            progressBar.visibility = View.GONE
            buttonSave.visibility = View.VISIBLE
        }
    }

    private fun validateInputs(name: String, user: String, url: String): Boolean {
        layoutProfileName.error = null
        layoutUsername.error = null
        layoutServerUrl.error = null

        if (name.isEmpty()) {
            layoutProfileName.error = getString(R.string.error_profile_name_empty)
            return false
        }
        if (user.isEmpty()) {
            layoutUsername.error = getString(R.string.error_username_empty)
            return false
        }
        if (url.isEmpty() || !url.startsWith("http")) {
            layoutServerUrl.error = getString(R.string.error_invalid_url)
            return false
        }
        return true
    }
}