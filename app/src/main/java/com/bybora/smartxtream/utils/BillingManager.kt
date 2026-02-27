package com.bybora.smartxtream.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(context: Context) {

    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()

    companion object {
        const val SUB_MONTHLY = "sub_monthly"
        const val SUB_YEARLY = "sub_yearly"
        const val LIFETIME = "lifetime_premium"
        private const val TAG = "BillingManager"
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Kullanıcı satın alma işlemini iptal etti.")
            }
            else -> {
                Log.e(TAG, "Satın alma hatası: Kod=${billingResult.responseCode}, Mesaj=${billingResult.debugMessage}")
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkActivePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e(TAG, "Billing servisi bağlantısı koptu.")
            }
        })
    }

    private fun checkActivePurchases() {
        val subsParams = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()

        billingClient.queryPurchasesAsync(subsParams) { _, purchases ->
            if (purchases.isNotEmpty()) {
                _isPremium.value = true
            }

            val inAppParams = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
            billingClient.queryPurchasesAsync(inAppParams) { _, inAppPurchases ->
                if (inAppPurchases.isNotEmpty()) {
                    _isPremium.value = true
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

    fun queryProductDetails(onDetailsLoaded: (List<ProductDetails>) -> Unit) {
        val combinedList = mutableListOf<ProductDetails>()

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

        // 8.3.0 ÇÖZÜMÜ: Sonuç artık QueryProductDetailsResult objesi olarak dönüyor.
        billingClient.queryProductDetailsAsync(subsParams) { resultSubs, queryResultSubs ->
            if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                // Kutuyu açıp içindeki listeyi (productDetailsList) güvenli bir şekilde alıyoruz
                queryResultSubs.productDetailsList?.let { combinedList.addAll(it) }
            }

            val inAppList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME)
                    .setProductType(ProductType.INAPP)
                    .build()
            )

            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()

            billingClient.queryProductDetailsAsync(inAppParams) { resultInApp, queryResultInApp ->
                if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Aynı şekilde inApp listesini de kutudan çıkarıyoruz
                    queryResultInApp.productDetailsList?.let { combinedList.addAll(it) }
                }
                onDetailsLoaded(combinedList)
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