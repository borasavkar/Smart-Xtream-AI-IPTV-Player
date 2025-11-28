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
        setContentView(R.layout.activity_subscription) // XML dosyanızı oluşturun

        billingManager = BillingManager(this)
        billingManager.startConnection()

        btnMonthly = findViewById(R.id.btn_sub_monthly) // XML ID'lerinizle eşleştirin
        btnYearly = findViewById(R.id.btn_sub_yearly)
        btnLifetime = findViewById(R.id.btn_sub_lifetime)
        txtStatus = findViewById(R.id.text_sub_status)

        loadProducts()
        observePurchaseStatus()
    }

    private fun loadProducts() {
        billingManager.queryProductDetails { detailsList ->
            runOnUiThread {
                detailsList.forEach { product ->
                    when (product.productId) {
                        BillingManager.SUB_MONTHLY -> {
                            val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            btnMonthly.text = "Aylık Abonelik ($price)"
                            btnMonthly.setOnClickListener { billingManager.launchPurchaseFlow(this, product) }
                        }
                        BillingManager.SUB_YEARLY -> {
                            val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            btnYearly.text = "Yıllık Abonelik ($price)"
                            btnYearly.setOnClickListener { billingManager.launchPurchaseFlow(this, product) }
                        }
                        BillingManager.LIFETIME -> {
                            val price = product.oneTimePurchaseOfferDetails?.formattedPrice
                            btnLifetime.text = "Ömür Boyu ($price)"
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
                    SettingsManager.setPremiumStatus(this@SubscriptionActivity, true)
                    // Bir süre sonra ana ekrana dön
                    btnMonthly.postDelayed({ finish() }, 1500)
                }
            }
        }
    }
}