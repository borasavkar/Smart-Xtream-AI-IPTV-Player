package com.bybora.smartxtream

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bybora.smartxtream.utils.BillingManager
import com.bybora.smartxtream.utils.SettingsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var billingManager: BillingManager
    private lateinit var btnMonthly: Button
    private lateinit var btnYearly: Button
    private lateinit var btnLifetime: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        billingManager = BillingManager(this)
        billingManager.startConnection()

        btnMonthly = findViewById(R.id.btn_sub_monthly)
        btnYearly = findViewById(R.id.btn_sub_yearly)
        btnLifetime = findViewById(R.id.btn_sub_lifetime)
        txtStatus = findViewById(R.id.text_sub_status)

        // Fiyatları Google Play'den çek ve butonlara yaz
        loadProducts()

        // Satın alma başarılı olursa ne yapacağını dinle
        observePurchaseStatus()
    }

    private fun loadProducts() {
        billingManager.queryProductDetails { detailsList ->
            runOnUiThread {
                detailsList.forEach { product ->
                    // Fiyatı (örn: 49.99 TL) al ve butona yaz
                    val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                        ?: product.oneTimePurchaseOfferDetails?.formattedPrice

                    when (product.productId) {
                        BillingManager.SUB_MONTHLY -> {
                            btnMonthly.text = "${getString(R.string.monthly_subscription)} ($price)"
                            btnMonthly.setOnClickListener { billingManager.launchPurchaseFlow(this, product) }
                        }
                        BillingManager.SUB_YEARLY -> {
                            btnYearly.text = "${getString(R.string.annual_subscription_with_advantage)} ($price)"
                            btnYearly.setOnClickListener { billingManager.launchPurchaseFlow(this, product) }
                        }
                        BillingManager.LIFETIME -> {
                            btnLifetime.text = "${getString(R.string.lifeTime_subscription)} ($price)"
                            btnLifetime.setOnClickListener { billingManager.launchPurchaseFlow(this, product) }
                        }
                    }
                }
            }
        }
    }

    private fun observePurchaseStatus() {
        lifecycleScope.launch {
            billingManager.isPremium.collectLatest { isPremium ->
                if (isPremium) {
                    txtStatus.text = "Premium Aktif! Teşekkürler."
                    Toast.makeText(this@SubscriptionActivity, "Satın alma başarılı!", Toast.LENGTH_LONG).show()

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