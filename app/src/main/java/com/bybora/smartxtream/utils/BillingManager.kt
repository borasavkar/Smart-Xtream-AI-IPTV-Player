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

    // DÜZELTME 1: Listener'ı 'billingClient'tan önce tanımladık
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
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                startConnection()
            }
        })
    }

    fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    // DÜZELTME 2: Eksik olan handlePurchase fonksiyonu eklendi
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            } else {
                // Zaten onaylanmışsa premium yap
                _isPremium.value = true
                SettingsManager.setPremiumStatus(context, true)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var isValid = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                isValid = true
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
        }
        if (isValid) {
            _isPremium.value = true
            SettingsManager.setPremiumStatus(context, true)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPremium.value = true
                SettingsManager.setPremiumStatus(context, true)
            }
        }
    }

    fun queryProductDetails(onDetailsLoaded: (List<ProductDetails>) -> Unit) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(SUB_MONTHLY).setProductType(ProductType.SUBS).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(SUB_YEARLY).setProductType(ProductType.SUBS).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(LIFETIME).setProductType(ProductType.INAPP).build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onDetailsLoaded(detailsList)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        val productParams = if (offerToken != null) {
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        } else {
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