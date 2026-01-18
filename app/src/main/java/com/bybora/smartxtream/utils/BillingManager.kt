package com.bybora.smartxtream.utils

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(private val context: Context) {

    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()

    companion object {
        const val SUB_MONTHLY = "sub_monthly"
        const val SUB_YEARLY = "sub_yearly"
        const val LIFETIME = "lifetime_premium"
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Kullanıcı iptal etti
        } else {
            // Hata
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkActivePurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Bağlantı koptu
            }
        })
    }

    private fun checkActivePurchases() {
        // 1. Abonelikleri Kontrol Et
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { _, purchases ->
            if (purchases.isNotEmpty()) {
                _isPremium.value = true
            } else {
                // 2. Abonelik yoksa, Tek Seferlik (Ömür Boyu) satın almayı kontrol et
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
                ) { _, inAppPurchases ->
                    if (inAppPurchases.isNotEmpty()) {
                        _isPremium.value = true
                    }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isPremium.value = true
                    }
                }
            } else {
                _isPremium.value = true
            }
        }
    }

    // --- KRİTİK DÜZELTME: SORGULARI AYIRIYORUZ ---
    fun queryProductDetails(onDetailsLoaded: (List<ProductDetails>) -> Unit) {
        val combinedList = mutableListOf<ProductDetails>()

        // 1. Grup: Abonelikler (SUBS)
        val subsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUB_MONTHLY)
                .setProductType(ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUB_YEARLY)
                .setProductType(ProductType.SUBS)
                .build()
        )

        val subsParams = QueryProductDetailsParams.newBuilder().setProductList(subsList).build()

        billingClient.queryProductDetailsAsync(subsParams) { resultSubs, listSubs ->
            // Abonelik sonuçlarını listeye ekle
            if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                listSubs?.let { combinedList.addAll(it) }
            }

            // 2. Grup: Tek Seferlik (INAPP) - Ömür Boyu
            val inAppList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME)
                    .setProductType(ProductType.INAPP)
                    .build()
            )

            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()

            // Şimdi bunu soruyoruz
            billingClient.queryProductDetailsAsync(inAppParams) { resultInApp, listInApp ->
                if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                    listInApp?.let { combinedList.addAll(it) }
                }

                // Hepsini birleştirip ekrana gönderiyoruz
                onDetailsLoaded(combinedList)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        val productParams = if (offerToken != null) {
            // Abonelik ise offerToken gerekir
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        } else {
            // Tek seferlik ise gerekmez
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }
}