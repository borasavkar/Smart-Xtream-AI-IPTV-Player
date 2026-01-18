package com.bybora.smartxtream

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.bybora.smartxtream.utils.BillingManager
import com.bybora.smartxtream.utils.SettingsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.bybora.smartxtream.utils.TrialManager
import androidx.activity.OnBackPressedCallback

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var billingManager: BillingManager
    private lateinit var btnMonthly: Button
    private lateinit var btnYearly: Button
    private lateinit var btnLifetime: Button
    private lateinit var txtStatus: TextView

    // Ürünleri hafızada tutmak için liste
    private var availableProducts: List<ProductDetails> = emptyList()
    // SubscriptionActivity.kt içindeki onBackPressed Düzeltmesi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)
// --- GERİ TUŞU YÖNETİMİ (YENİ SİSTEM - ASYNC) ---
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Önce Premium mu diye bak (Hızlı)
                if (SettingsManager.isPremiumUser(this@SubscriptionActivity)) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }

                // Değilse Sunucuya Sor (Bekletmemek için hızlıca kontrol ediyoruz)
                TrialManager.checkTrialOnServer(this@SubscriptionActivity, object : TrialManager.TrialCheckListener {
                    override fun onCheckResult(isActive: Boolean, message: String) {
                        if (!isActive) {
                            // Deneme bitmiş, çıkış yap
                            finishAffinity()
                        } else {
                            // Deneme devam ediyor, geri gitmeye izin ver
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                })
            }
        })
        // ----------------------------------------

        billingManager = BillingManager(this)
        billingManager.startConnection()

        btnMonthly = findViewById(R.id.btn_sub_monthly)
        btnYearly = findViewById(R.id.btn_sub_yearly)
        btnLifetime = findViewById(R.id.btn_sub_lifetime)
        txtStatus = findViewById(R.id.text_sub_status)

        // 1. ADIM: Butonlara tıklama özelliğini HEMEN ver (Veri gelmesini bekleme)
        setupClickListeners()

        // 2. ADIM: Google Play'den fiyatları çekmeye başla
        loadProducts()

        // 3. ADIM: Satın alma başarılı olursa ne yapacağını dinle
        observePurchaseStatus()
    }

    private fun setupClickListeners() {
        btnMonthly.setOnClickListener {
            initiatePurchase(BillingManager.SUB_MONTHLY)
        }
        btnYearly.setOnClickListener {
            initiatePurchase(BillingManager.SUB_YEARLY)
        }
        btnLifetime.setOnClickListener {
            initiatePurchase(BillingManager.LIFETIME)
        }
    }

    private fun loadProducts() {
        billingManager.queryProductDetails { detailsList ->
            // Listeyi hafızaya kaydet
            availableProducts = detailsList

            // UI güncellemesini (Fiyat yazdırma) ana iş parçacığında yap
            runOnUiThread {
                if (detailsList.isEmpty()) {
                    // Eğer liste boşsa log veya uyarı verebilirsin ama kullanıcıyı rahatsız etme
                    // Butonlar varsayılan metinleriyle kalır.
                    return@runOnUiThread
                }

                detailsList.forEach { product ->
                    val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                        ?: product.oneTimePurchaseOfferDetails?.formattedPrice
                        ?: "---"

                    when (product.productId) {
                        BillingManager.SUB_MONTHLY -> {
                            btnMonthly.text = getString(R.string.fmt_btn_monthly, price)
                        }
                        BillingManager.SUB_YEARLY -> {
                            btnYearly.text = getString(R.string.fmt_btn_yearly, price)
                        }
                        BillingManager.LIFETIME -> {
                            btnLifetime.text = getString(R.string.fmt_btn_lifetime, price)
                        }
                    }
                }
            }
        }
    }

    // Yardımcı Fonksiyon: Güvenli Satın Alma Başlatıcı
    private fun initiatePurchase(productId: String) {
        val product = availableProducts.find { it.productId == productId }

        if (product != null) {
            billingManager.launchPurchaseFlow(this, product)
        } else {
            // Eğer ürün bilgisi henüz yüklenmediyse kullanıcıyı uyar
            Toast.makeText(this, getString(R.string.msg_connecting_retry), Toast.LENGTH_SHORT).show()
            // Tekrar yüklemeyi tetikle
            loadProducts()
        }
    }

    private fun observePurchaseStatus() {
        lifecycleScope.launch {
            billingManager.isPremium.collectLatest { isPremium ->
                if (isPremium) {
                    runOnUiThread {
                        txtStatus.text = getString(R.string.msg_premium_active)
                        Toast.makeText(this@SubscriptionActivity, getString(R.string.msg_purchase_success), Toast.LENGTH_LONG).show()

                        // Butonları gizle veya devre dışı bırak (Opsiyonel)
                        btnMonthly.isEnabled = false
                        btnYearly.isEnabled = false
                        btnLifetime.isEnabled = false
                    }

                    // Premium bilgisini kaydet
                    SettingsManager.setPremiumStatus(this@SubscriptionActivity, true)

                    // 1.5 saniye sonra ana ekrana dön
                    btnMonthly.postDelayed({
                        finish() // Bu ekranı kapat, MainActivity'ye dön
                    }, 1500)
                }
            }
        }
    }
}
