package com.gpsmap.sayan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.WindowCompat;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HDSKayApp";
    public static final String TARGET_URL = "https://hdskay.blogspot.com";
    public static final String FORCED_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K; AppSecret123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36";

    // Firebase Realtime Database default URL
    public static final String FIREBASE_RTDB_URL = "https://myapps-4a8eb-default-rtdb.firebaseio.com";

    // Standard AdMob Test Ad Units (fallback if RTDB node is empty)
    private String bannerAdUnitId = "ca-app-pub-3940256099942544/6300978111";
    private String interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712";
    private String rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917";
    private boolean adsEnabled = true;
    private long interstitialIntervalMs = 10 * 60 * 1000; // 10 minutes

    private WebView webView;
    private ProgressBar progressBar;
    private RelativeLayout rootLayout;
    private AdView adMobBannerView;
    private FrameLayout splashOverlay;
    private FrameLayout fullscreenContainer;
    private View customVideoView = null;
    private WebChromeClient.CustomViewCallback customViewCallback = null;
    private boolean isVideoFullscreen = false;

    private RelativeLayout noInternetLayout;
    private boolean isOffline = false;

    // AdMob Ads Instances
    private RewardedAd mRewardedAd;
    private InterstitialAd mInterstitialAd;
    private boolean isInterstitialLoading = false;
    private boolean isRewardedLoading = false;
    private long lastInterstitialShownTime = System.currentTimeMillis();

    private String pendingUrlToLoad = null;
    private boolean isNavigatingPostAd = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable interstitialPeriodicChecker = new Runnable() {
        @Override
        public void run() {
            checkAndShowPeriodicInterstitial();
            handler.postDelayed(this, 30 * 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            androidx.multidex.MultiDex.install(this);
        } catch (Exception ignored) {}
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Immersive Edge-to-Edge Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        hideSystemUI();

        // 2. Initialize Google Mobile Ads (AdMob)
        try {
            MobileAds.initialize(this, initializationStatus -> Log.d(TAG, "AdMob MobileAds initialized."));
        } catch (Exception e) {
            Log.e(TAG, "MobileAds initialization error: " + e.getMessage());
        }

        // 3. Dynamic Layout Hierarchy
        rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(0xFF0F172A);
        setContentView(rootLayout);

        // Fullscreen container for HTML5 videos
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        rootLayout.addView(fullscreenContainer, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Setup WebView, Progress Bar, Offline Dialog, Splash
        setupWebView();
        setupProgressBar();
        buildNoInternetPopup();
        setupSplashScreen();
        setupNetworkMonitoring();

        // 4. Initialize Firebase Realtime Database & fetch AdMob Ad Unit IDs
        initFirebaseAndFetchAdUnits();

        // 5. Start periodic Interstitial ad checker
        handler.postDelayed(interstitialPeriodicChecker, 30 * 1000);

        // 6. Deep link or initial target load
        handleDeepLink(getIntent());
        if (webView.getUrl() == null || webView.getUrl().isEmpty()) {
            if (isNetworkAvailable()) {
                webView.loadUrl(TARGET_URL);
            } else {
                showNoInternetPopup();
            }
        }
    }

    private void initFirebaseAndFetchAdUnits() {
        try {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey("AIzaSyCyDd6rCV7WaQe8sMF0Xmob3dpm6z6wEEQ")
                    .setApplicationId("1:342267842050:web:4749870bb18b05ed94611b")
                    .setDatabaseUrl(FIREBASE_RTDB_URL)
                    .setProjectId("myapps-4a8eb")
                    .build();

            FirebaseApp firebaseApp;
            if (FirebaseApp.getApps(this).isEmpty()) {
                firebaseApp = FirebaseApp.initializeApp(this, options, "HDSKayFirebase");
            } else {
                firebaseApp = FirebaseApp.getInstance("HDSKayFirebase");
            }

            FirebaseDatabase database = FirebaseDatabase.getInstance(firebaseApp, FIREBASE_RTDB_URL);
            DatabaseReference admobRef = database.getReference("admob");

            admobRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String b = snapshot.child("bannerAdUnitId").getValue(String.class);
                        if (b != null && !b.isEmpty()) bannerAdUnitId = b;

                        String i = snapshot.child("interstitialAdUnitId").getValue(String.class);
                        if (i != null && !i.isEmpty()) interstitialAdUnitId = i;

                        String r = snapshot.child("rewardedAdUnitId").getValue(String.class);
                        if (r != null && !r.isEmpty()) rewardedAdUnitId = r;

                        Boolean enabled = snapshot.child("adsEnabled").getValue(Boolean.class);
                        if (enabled != null) adsEnabled = enabled;

                        Long intervalMin = snapshot.child("interstitialIntervalMinutes").getValue(Long.class);
                        if (intervalMin != null && intervalMin > 0) {
                            interstitialIntervalMs = intervalMin * 60 * 1000;
                        }

                        Log.d(TAG, "AdMob config loaded from Firebase RTDB: banner=" + bannerAdUnitId);
                    }

                    // Reload/Attach Banner Ad with updated Ad Unit ID
                    setupAdMobBanner();
                    preloadInterstitialAd();
                    preloadRewardedVideoAd();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Firebase RTDB read cancelled: " + error.getMessage());
                    setupAdMobBanner();
                    preloadInterstitialAd();
                    preloadRewardedVideoAd();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Firebase RTDB init error: " + e.getMessage());
            setupAdMobBanner();
            preloadInterstitialAd();
            preloadRewardedVideoAd();
        }
    }

    private void setupAdMobBanner() {
        if (!adsEnabled) {
            if (adMobBannerView != null) adMobBannerView.setVisibility(View.GONE);
            return;
        }

        runOnUiThread(() -> {
            try {
                if (adMobBannerView != null) {
                    rootLayout.removeView(adMobBannerView);
                    adMobBannerView.destroy();
                }

                adMobBannerView = new AdView(this);
                adMobBannerView.setAdSize(AdSize.BANNER);
                adMobBannerView.setAdUnitId(bannerAdUnitId);

                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                adMobBannerView.setLayoutParams(params);

                rootLayout.addView(adMobBannerView);
                adMobBannerView.bringToFront();

                AdRequest adRequest = new AdRequest.Builder().build();
                adMobBannerView.loadAd(adRequest);
                Log.d(TAG, "AdMob Banner load requested for unit: " + bannerAdUnitId);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up AdMob banner: " + e.getMessage());
            }
        });
    }

    private void preloadInterstitialAd() {
        if (!adsEnabled || isInterstitialLoading || mInterstitialAd != null) return;
        isInterstitialLoading = true;

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, interstitialAdUnitId, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                mInterstitialAd = interstitialAd;
                isInterstitialLoading = false;
                Log.d(TAG, "AdMob Interstitial Ad loaded successfully.");

                mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        mInterstitialAd = null;
                        lastInterstitialShownTime = System.currentTimeMillis();
                        hideSystemUI();
                        preloadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        mInterstitialAd = null;
                        preloadInterstitialAd();
                    }
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mInterstitialAd = null;
                isInterstitialLoading = false;
                Log.w(TAG, "AdMob Interstitial failed to load: " + loadAdError.getMessage());
                handler.postDelayed(MainActivity.this::preloadInterstitialAd, 30000);
            }
        });
    }

    private void preloadRewardedVideoAd() {
        if (!adsEnabled || isRewardedLoading || mRewardedAd != null) return;
        isRewardedLoading = true;

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, rewardedAdUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                mRewardedAd = rewardedAd;
                isRewardedLoading = false;
                Log.d(TAG, "AdMob Rewarded Ad loaded successfully.");

                mRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        mRewardedAd = null;
                        executePendingNavigation();
                        preloadRewardedVideoAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        mRewardedAd = null;
                        executePendingNavigation();
                        preloadRewardedVideoAd();
                    }
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mRewardedAd = null;
                isRewardedLoading = false;
                Log.w(TAG, "AdMob Rewarded failed to load: " + loadAdError.getMessage());
                handler.postDelayed(MainActivity.this::preloadRewardedVideoAd, 25000);
            }
        });
    }

    public void handlePostClick(final String targetUrl) {
        runOnUiThread(() -> {
            if (targetUrl == null || targetUrl.trim().isEmpty()) return;
            pendingUrlToLoad = targetUrl;

            if (!adsEnabled) {
                executePendingNavigation();
                return;
            }

            if (mRewardedAd != null) {
                mRewardedAd.show(MainActivity.this, rewardItem -> Log.d(TAG, "AdMob reward earned: " + rewardItem.getAmount()));
                return;
            }

            // Fallback: If rewarded ad isn't ready, try interstitial fallback or navigate directly after short timeout
            if (mInterstitialAd != null) {
                mInterstitialAd.show(MainActivity.this);
                return;
            }

            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show();
            executePendingNavigation();
            preloadRewardedVideoAd();
        });
    }

    private void checkAndShowPeriodicInterstitial() {
        long now = System.currentTimeMillis();
        if (now - lastInterstitialShownTime < interstitialIntervalMs || isOffline || isVideoFullscreen || !adsEnabled) {
            return;
        }

        if (mInterstitialAd != null) {
            mInterstitialAd.show(this);
            lastInterstitialShownTime = System.currentTimeMillis();
        } else {
            preloadInterstitialAd();
        }
    }

    private void executePendingNavigation() {
        runOnUiThread(() -> {
            if (pendingUrlToLoad != null && !pendingUrlToLoad.isEmpty()) {
                String destination = pendingUrlToLoad;
                pendingUrlToLoad = null;
                isNavigatingPostAd = true;
                if (webView != null) {
                    webView.loadUrl(destination);
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        rootLayout.addView(webView, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(FORCED_USER_AGENT);

        // JavaScript Bridges: AndroidAdMob & legacy AndroidStartApp
        Object bridgeObj = new Object() {
            @JavascriptInterface
            public void showRewardedVideoForUrl(final String url) {
                handlePostClick(url);
            }

            @JavascriptInterface
            public void setBannerVisibility(final boolean visible) {
                runOnUiThread(() -> {
                    if (adMobBannerView != null) {
                        adMobBannerView.setVisibility((visible && !isVideoFullscreen) ? View.VISIBLE : View.GONE);
                    }
                });
            }

            @JavascriptInterface
            public void setVideoFullscreen(final boolean isFs) {
                runOnUiThread(() -> {
                    isVideoFullscreen = isFs;
                    if (isFs) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        if (adMobBannerView != null) adMobBannerView.setVisibility(View.GONE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        if (adMobBannerView != null && adsEnabled) adMobBannerView.setVisibility(View.VISIBLE);
                    }
                    hideSystemUI();
                });
            }

            @JavascriptInterface
            public void openCustomTab(final String url) {
                openInCustomTabs(url);
            }

            @JavascriptInterface
            public boolean isNativeApp() {
                return true;
            }
        };

        webView.addJavascriptInterface(bridgeObj, "AndroidAdMob");
        webView.addJavascriptInterface(bridgeObj, "AndroidStartApp");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                    progressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                isVideoFullscreen = true;

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                if (adMobBannerView != null) adMobBannerView.setVisibility(View.GONE);

                fullscreenContainer.addView(customVideoView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.bringToFront();
                hideSystemUI();
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;
                fullscreenContainer.removeView(customVideoView);
                customVideoView = null;
                fullscreenContainer.setVisibility(View.GONE);
                isVideoFullscreen = false;

                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                hideSystemUI();
                if (adMobBannerView != null && adsEnabled) adMobBannerView.setVisibility(View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            private boolean handleUrl(Uri uri) {
                if (uri == null) return false;
                String scheme = uri.getScheme();
                if (scheme != null && !scheme.startsWith("http")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                    return true;
                }

                if (isNavigatingPostAd) {
                    isNavigatingPostAd = false;
                    return false;
                }

                String host = uri.getHost();
                if (host != null && host.contains("blogspot.com")) {
                    String path = uri.getPath();
                    if (path != null && path.matches(".*/\\d{4}/\\d{2}/.*\\.html.*")) {
                        handlePostClick(uri.toString());
                        return true;
                    }
                    return false;
                }

                openInCustomTabs(uri.toString());
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showNoInternetPopup();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideNoInternetPopup();
            }
        });
    }

    private void openInCustomTabs(String url) {
        try {
            CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
            intent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        }
    }

    private void setupProgressBar() {
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10);
        p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        rootLayout.addView(progressBar, p);
    }

    private void setupSplashScreen() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.BLACK);
        ImageView img = new ImageView(this);
        img.setImageResource(R.drawable.splash);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashOverlay.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(splashOverlay, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splashOverlay.bringToFront();

        handler.postDelayed(() -> {
            if (splashOverlay != null) {
                splashOverlay.animate().alpha(0f).setDuration(400).withEndAction(() -> splashOverlay.setVisibility(View.GONE));
            }
        }, 2500);
    }

    private void buildNoInternetPopup() {
        noInternetLayout = new RelativeLayout(this);
        noInternetLayout.setBackgroundColor(0xD90F172A);
        noInternetLayout.setVisibility(View.GONE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(64, 56, 64, 56);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(32f);
        card.setBackground(bg);

        TextView t = new TextView(this);
        t.setText("No Internet Connection");
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        card.addView(t);

        Button btn = new Button(this);
        btn.setText("Retry");
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(0xFF0284C7);
        btn.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                hideNoInternetPopup();
                if (webView != null) webView.reload();
            }
        });
        card.addView(btn);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        noInternetLayout.addView(card, lp);
        rootLayout.addView(noInternetLayout, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showNoInternetPopup() {
        isOffline = true;
        if (noInternetLayout != null) {
            noInternetLayout.setVisibility(View.VISIBLE);
            noInternetLayout.bringToFront();
        }
    }

    private void hideNoInternetPopup() {
        isOffline = false;
        if (noInternetLayout != null) noInternetLayout.setVisibility(View.GONE);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network net = cm.getActiveNetwork();
            if (net != null) {
                NetworkCapabilities c = cm.getNetworkCapabilities(net);
                return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        return false;
    }

    private void setupNetworkMonitoring() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> hideNoInternetPopup());
                }

                @Override
                public void onLost(@NonNull Network network) {
                    runOnUiThread(() -> showNoInternetPopup());
                }
            });
        }
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if ("movieapp".equalsIgnoreCase(data.getScheme())) {
            String q = data.getQueryParameter("url");
            if (q != null && webView != null) webView.loadUrl(q);
        } else if ("http".equalsIgnoreCase(data.getScheme()) || "https".equalsIgnoreCase(data.getScheme())) {
            if (webView != null) webView.loadUrl(data.toString());
        }
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}
