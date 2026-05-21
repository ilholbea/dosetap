package com.dosetap.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BillingManager"

/** One-time in-app product ID — must match Play Console exactly. */
const val PRODUCT_ID_PRO = "dosetap_pro"

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    scope.launch {
                        queryPurchases()
                        queryProductDetails()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected — will retry on next use")
            }
        })
    }

    private suspend fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val hasPro = result.purchasesList.any { purchase ->
                purchase.products.contains(PRODUCT_ID_PRO) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            _isPro.value = hasPro
            Log.d(TAG, "queryPurchases: isPro=$hasPro (${result.purchasesList.size} purchases)")
            // Acknowledge any unacknowledged purchases
            result.purchasesList.forEach { maybeAcknowledge(it) }
        }
    }

    private suspend fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = result.productDetailsList?.firstOrNull()
            Log.d(TAG, "Product details: ${_productDetails.value?.title}")
        }
    }

    /** Launches the Play purchase flow. Call from an Activity context. */
    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value
        if (details == null) {
            Log.w(TAG, "launchPurchase: product details not loaded yet, reconnecting")
            connect()
            return
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(TAG, "launchBillingFlow: ${result.responseCode} ${result.debugMessage}")
    }

    /** Restores purchases — useful for reinstalls and device transfers. */
    fun restorePurchases() {
        scope.launch { queryPurchases() }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    scope.launch {
                        maybeAcknowledge(purchase)
                        if (purchase.products.contains(PRODUCT_ID_PRO) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        ) {
                            _isPro.value = true
                            Log.i(TAG, "Pro purchase confirmed")
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Log.d(TAG, "Purchase cancelled by user")
            else ->
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private suspend fun maybeAcknowledge(purchase: Purchase) {
        if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.acknowledgePurchase(params)
            Log.d(TAG, "Acknowledge: ${result.responseCode} ${result.debugMessage}")
        }
    }

    /** Formatted price string from Play, e.g. "€2.99". Falls back to "€2.99" if not loaded. */
    fun formattedPrice(): String =
        _productDetails.value
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
            ?: "€2.99"
}
