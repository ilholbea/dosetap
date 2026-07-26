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
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.dosetap.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "BillingManager"

/** One-time in-app product ID — must match Play Console exactly. */
const val PRODUCT_ID_PRO = "dosetap_pro"

/** Cap on the exponential reconnect backoff after a billing-service disconnect. */
private const val MAX_RECONNECT_DELAY_MILLIS = 60_000L

class BillingManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Seeded false, then immediately overwritten from the DataStore cache so a paying user
    // who is offline on cold start is treated as Pro until a *successful* query says otherwise.
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Reconnect attempt counter for exponential backoff; reset on a successful setup. */
    private var reconnectAttempts = 0

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        // Seed entitlement from the persisted cache before billing connects, so an offline
        // cold start doesn't flash the paywall at a paying user (E-C2).
        scope.launch {
            val cached = settingsRepository.proEntitlementCached.first()
            if (cached) {
                _isPro.value = true
                Log.d(TAG, "Seeded isPro=true from cache")
            }
        }
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    reconnectAttempts = 0
                    scope.launch {
                        queryPurchases()
                        queryProductDetails()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect with exponential backoff (1s, 2s, 4s … capped). Entitlement stays
                // at its cached value in the meantime — we never downgrade without a live query.
                val delayMillis = minOf(
                    1000L shl reconnectAttempts.coerceAtMost(6),
                    MAX_RECONNECT_DELAY_MILLIS,
                )
                reconnectAttempts++
                Log.w(TAG, "Billing disconnected — reconnecting in ${delayMillis}ms (attempt $reconnectAttempts)")
                scope.launch {
                    delay(delayMillis)
                    connect()
                }
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
            // A *successful* query is the single source of truth: it may upgrade or downgrade,
            // and it persists the result so future offline starts read the right value.
            _isPro.value = hasPro
            settingsRepository.setProEntitlementCached(hasPro)
            Log.d(TAG, "queryPurchases: isPro=$hasPro (${result.purchasesList.size} purchases)")
            // Acknowledge any unacknowledged purchases
            result.purchasesList.forEach { maybeAcknowledge(it) }
        } else {
            // Failed/unreachable query must never downgrade a paying user — leave cached value intact.
            Log.w(TAG, "queryPurchases failed: ${result.billingResult.debugMessage} — keeping cached entitlement")
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
                            settingsRepository.setProEntitlementCached(true)
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
