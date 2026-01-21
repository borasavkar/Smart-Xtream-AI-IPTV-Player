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
        // Senin mevcut ürün ID'lerin
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
            // Kullanıcı iptal etti, bir şey yapmaya gerek yok
        } else {
            // Hata oluştu
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkActivePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Bağlantı koparsa tekrar denenebilir
            }
        })
    }

    // Aktif abonelikleri kontrol eden standart fonksiyon
    private fun checkActivePurchases() {
        // 1. Abonelikleri (SUBS) Kontrol Et
        val querySubParams = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(querySubParams) { _, purchases ->
            if (purchases.isNotEmpty()) {
                // Aktif bir abonelik var
                _isPremium.value = true
            } else {
                // 2. Abonelik yoksa, Ömür Boyu (INAPP) var mı diye bak
                val queryInAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(ProductType.INAPP)
                    .build()

                billingClient.queryPurchasesAsync(queryInAppParams) { _, inAppPurchases ->
                    if (inAppPurchases.isNotEmpty()) {
                        // Ömür boyu satın alım var
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

    // --- BILLING 8.3.0 UYUMLU SORGULAMA FONKSİYONU ---
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

        // KRİTİK DÜZELTME: Billing 8.3.0 { billingResult, result -> } yapısını kullanır.
        billingClient.queryProductDetailsAsync(subsParams) { billingResult, result ->

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Liste artık 'result' paketinin içinde (.productDetailsList)
                result.productDetailsList?.let { combinedList.addAll(it) }
            }

            // 2. Grup: Tek Seferlik (INAPP - LIFETIME)
            val inAppList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME)
                    .setProductType(ProductType.INAPP)
                    .build()
            )

            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()

            // İkinci sorgu
            billingClient.queryProductDetailsAsync(inAppParams) { billingResultInApp, resultInApp ->

                if (billingResultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                    resultInApp.productDetailsList?.let { combinedList.addAll(it) }
                }

                // Hepsini birleştirip arayüze gönder
                onDetailsLoaded(combinedList)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val productParamsList = if (productDetails.productType == ProductType.SUBS) {
            // Abonelik ise OfferToken şart
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken != null) {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            } else {
                // Token yoksa hata vermemesi için boş liste (veya log basılabilir)
                emptyList()
            }
        } else {
            // Tek seferlik (INAPP) ise OfferToken'a gerek yok
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        }

        if (productParamsList.isNotEmpty()) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productParamsList)
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }
}