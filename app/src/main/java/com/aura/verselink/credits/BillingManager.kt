package com.aura.verselink.credits

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Google Play Billing for credit packs, wired to the credits backend.
 *
 * Order of operations for every purchase (deliberate, and matched to the
 * backend contract):
 * 1. Play completes the charge and hands us a purchase token.
 * 2. The backend verifies the token with Google and grants the credits
 *    (amount taken only from its server-side product map). The purchase is
 *    launched with obfuscatedAccountId = the backend user id, which the
 *    backend checks — a mismatch is rejected there.
 * 3. ONLY after a successful grant (or an "already granted" replay) do we
 *    consume the purchase, which also acknowledges it and makes the pack
 *    buyable again.
 *
 * A purchase that fails step 2 (offline, session expired, backend down) is
 * left unconsumed on purpose: [processUnfinishedPurchases] re-runs step 2–3
 * for it on the next start, so a paid pack can never be lost to a crash.
 *
 * Purchase tokens are never logged.
 */
class BillingManager(
    context: Context,
    private val creditsClient: CreditsClient,
    private val store: CreditsStore,
    private val scope: CoroutineScope,
    /** Called with the fresh balance (microdollars) after any grant. */
    private val onBalanceUpdated: (Long) -> Unit,
    /** User-facing status messages (toasts / labels) from billing events. */
    private val onEvent: (String) -> Unit
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private var billingClient: BillingClient? = null

    @Volatile
    private var connected = false

    /** Product details for the packs Play actually sells, in catalog order.
     *  Empty until [start] connects and queries; stays empty (and the buy UI
     *  hidden) when Play returns nothing — e.g. products not created yet. */
    @Volatile
    var availableProducts: List<ProductDetails> = emptyList()
        private set

    /** Invoked after [availableProducts] changes so the UI can rebuild. */
    var onProductsChanged: (() -> Unit)? = null

    fun start() {
        if (billingClient != null) return
        val client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) {
                    queryProducts()
                    processUnfinishedPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    fun end() {
        billingClient?.endConnection()
        billingClient = null
        connected = false
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        val products = CreditsConfig.PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val found = queryResult.productDetailsList
                // Keep catalog display order regardless of Play's return order.
                availableProducts = CreditsConfig.PRODUCT_IDS.mapNotNull { id ->
                    found.firstOrNull { it.productId == id }
                }
                onProductsChanged?.invoke()
            }
        }
    }

    /** Launches the Play purchase sheet for [details]. Requires a credits
     *  sign-in: the backend user id goes on the purchase as its obfuscated
     *  account id, and the backend rejects purchases bound to anyone else. */
    fun buy(activity: Activity, details: ProductDetails) {
        val client = billingClient
        if (client == null || !connected) {
            onEvent("Play billing isn't ready yet — try again in a moment")
            return
        }
        val userId = store.userId()
        if (userId == null) {
            onEvent("Sign in with Google before buying credits")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .setObfuscatedAccountId(userId.toString())
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> onEvent("Purchase cancelled")
            else -> onEvent("Purchase didn't complete (Play error ${result.responseCode})")
        }
    }

    /** Re-verifies and consumes any purchase Play still holds for this user.
     *  Crash/offline recovery — safe to call on every start. */
    fun processUnfinishedPurchases() {
        val client = billingClient ?: return
        if (!connected) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> verifyThenConsume(purchase)
            Purchase.PurchaseState.PENDING ->
                onEvent("Purchase is pending — credits arrive once the payment completes")
            else -> Unit
        }
    }

    private fun verifyThenConsume(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        scope.launch {
            val verify = try {
                creditsClient.verifyPurchase(productId, purchase.purchaseToken)
            } catch (e: CreditsException.SessionExpired) {
                onEvent("Sign in again to finish crediting your purchase")
                return@launch
            } catch (e: CreditsException) {
                onEvent("Couldn't verify your purchase yet — it will retry when the app restarts")
                return@launch
            }
            // Backend has granted (or had already granted) these credits:
            // safe to consume, which acknowledges the purchase and frees
            // the pack for repurchase.
            consume(purchase.purchaseToken)
            onBalanceUpdated(verify.balanceMicros)
            onEvent(
                if (verify.granted) {
                    "Added ${CreditsFormat.formatCredits(verify.grantedMicros)}"
                } else {
                    "Purchase confirmed — credits were already on your balance"
                }
            )
        }
    }

    private fun consume(purchaseToken: String) {
        val client = billingClient ?: return
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.consumeAsync(params) { _, _ -> /* result is best-effort; unfinished purchases retry on start */ }
    }
}
