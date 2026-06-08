package com.aiassistant;

import android.app.Activity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin Google Play Billing wrapper for a single subscription.
 * Restores the subscription automatically (it's tied to the Google account),
 * so a user never loses their plan after reinstalling.
 */
public class Billing {

    public interface Listener {
        void onSubscriptionChanged(boolean subscribed);
    }

    /** Master switch — keep false during dev, flip true once the Play product exists. */
    public static boolean enabled() {
        return BuildConfig.BILLING_ENABLED;
    }

    private final Activity activity;
    private final Listener listener;
    private final String productId;
    private BillingClient client;
    private ProductDetails productDetails;

    public Billing(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.productId = BuildConfig.SUB_PRODUCT_ID;
    }

    private final PurchasesUpdatedListener purchasesUpdated = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && purchases != null) {
                handle(purchases);
            }
        }
    };

    public void start() {
        client = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdated)
                .enablePendingPurchases()
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    loadProduct();
                    refresh();
                }
            }
            @Override
            public void onBillingServiceDisconnected() { }
        });
    }

    /** Re-check whether the user owns an active subscription. */
    public void refresh() {
        if (client == null) {
            return;
        }
        client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build(),
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(BillingResult r, List<Purchase> purchases) {
                        boolean sub = false;
                        if (purchases != null) {
                            for (int i = 0; i < purchases.size(); i++) {
                                Purchase p = purchases.get(i);
                                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    sub = true;
                                    acknowledge(p);
                                }
                            }
                        }
                        notifyChanged(sub);
                    }
                });
    }

    private void loadProduct() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS).build());
        client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(products).build(),
                new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(BillingResult r, List<ProductDetails> list) {
                        if (list != null && !list.isEmpty()) {
                            productDetails = list.get(0);
                        }
                    }
                });
    }

    /** Launch the Play purchase sheet. */
    public void subscribe() {
        if (client == null || productDetails == null) {
            return;
        }
        List<ProductDetails.SubscriptionOfferDetails> offers =
                productDetails.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) {
            return;
        }
        String offerToken = offers.get(0).getOfferToken();
        List<BillingFlowParams.ProductDetailsParams> params = new ArrayList<>();
        params.add(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken).build());
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(params).build());
    }

    private void handle(List<Purchase> purchases) {
        boolean sub = false;
        for (int i = 0; i < purchases.size(); i++) {
            Purchase p = purchases.get(i);
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                sub = true;
                acknowledge(p);
            }
        }
        if (sub) {
            notifyChanged(true);
        }
    }

    private void acknowledge(Purchase p) {
        if (!p.isAcknowledged()) {
            client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.getPurchaseToken()).build(),
                    new AcknowledgePurchaseResponseListener() {
                        @Override
                        public void onAcknowledgePurchaseResponse(BillingResult r) { }
                    });
        }
    }

    private void notifyChanged(final boolean sub) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSubscriptionChanged(sub);
                }
            }
        });
    }

    public void destroy() {
        if (client != null) {
            client.endConnection();
        }
    }
}
