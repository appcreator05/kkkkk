package com.gps.tools;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    // AdMob Ad Units (Loaded 100% dynamically from Firebase Realtime Database)
    private String bannerAdUnitId = "";
    private String interstitialAdUnitId = "";
    private String rewardedAdUnitId = "";
    private boolean adsEnabled = true;

    // Random interval between 10 to 15 minutes (in milliseconds) for organic bot protection
    private long getRandomRewardedIntervalMs() {
        long minMs = 10 * 60 * 1000L; // 10 minutes
        long maxMs = 15 * 60 * 1000L; // 15 minutes
        return minMs + (long) (Math.random() * (maxMs - minMs));
    }

    private long nextRewardedIntervalMs = getRandomRewardedIntervalMs();
    // Post click Interstitial Cooldown: 5 minutes frequency capping
    private long lastPostAdShownTime = 0;
    private long postAdCooldownMs = 5 * 60 * 1000L; // 5 minutes cooldown

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
    private long lastRewardedShownTime = System.currentTimeMillis();
    private long lastInterstitialShownTime = System.currentTimeMillis();

    private String pendingUrlToLoad = null;
    private boolean isNavigatingPostAd = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Automatic Rewarded Video periodic checker (every 10-15 minutes)
    private final Runnable rewardedPeriodicChecker = new Runnable() {
        @Override
        public void run() {
            checkAndShowPeriodicRewarded();
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

        // Request runtime permissions for camera, microphone, location, downloads
        requestAppPermissions();

        // 4. Initialize Firebase Realtime Database & fetch AdMob Ad Unit IDs
        initFirebaseAndFetchAdUnits();

        // 5. Start periodic automatic Rewarded Video ad checker (every 10-15 minutes)
        handler.postDelayed(rewardedPeriodicChecker, 60 * 1000);

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
                        if (b != null) bannerAdUnitId = b.trim();

                        String i = snapshot.child("interstitialAdUnitId").getValue(String.class);
                        if (i != null) interstitialAdUnitId = i.trim();

                        String r = snapshot.child("rewardedAdUnitId").getValue(String.class);
                        if (r != null) rewardedAdUnitId = r.trim();

                        Boolean enabled = snapshot.child("adsEnabled").getValue(Boolean.class);
                        if (enabled != null) adsEnabled = enabled;

                        Long cooldownMin = snapshot.child("postAdCooldownMinutes").getValue(Long.class);
                        if (cooldownMin != null && cooldownMin > 0) {
                            postAdCooldownMs = cooldownMin * 60 * 1000L;
                        }

                        Log.d(TAG, "AdMob live config received from Firebase RTDB: banner=" + bannerAdUnitId +
                                ", interstitial=" + interstitialAdUnitId + ", rewarded=" + rewardedAdUnitId);
                    }

                    // Attach / Preload ads only when valid Ad Unit IDs are provided by Firebase
                    if (bannerAdUnitId != null && !bannerAdUnitId.isEmpty()) setupAdMobBanner();
                    if (interstitialAdUnitId != null && !interstitialAdUnitId.isEmpty()) preloadInterstitialAd();
                    if (rewardedAdUnitId != null && !rewardedAdUnitId.isEmpty()) preloadRewardedVideoAd();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Firebase RTDB read cancelled: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Firebase RTDB init error: " + e.getMessage());
        }
    }

    private void setupAdMobBanner() {
        if (!adsEnabled || bannerAdUnitId == null || bannerAdUnitId.trim().isEmpty()) {
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    adMobBannerView.setElevation(100f);
                    adMobBannerView.setTranslationZ(100f);
                }

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
        if (!adsEnabled || isInterstitialLoading || mInterstitialAd != null || interstitialAdUnitId == null || interstitialAdUnitId.trim().isEmpty()) return;
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
                        executePendingNavigation();
                        hideSystemUI();
                        preloadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        mInterstitialAd = null;
                        executePendingNavigation();
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
        if (!adsEnabled || isRewardedLoading || mRewardedAd != null || rewardedAdUnitId == null || rewardedAdUnitId.trim().isEmpty()) return;
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
                        lastRewardedShownTime = System.currentTimeMillis();
                        nextRewardedIntervalMs = getRandomRewardedIntervalMs();
                        Log.d(TAG, "Ad dismissed. Next random rewarded video ad in " + (nextRewardedIntervalMs / 60000.0) + " min");
                        executePendingNavigation();
                        hideSystemUI();
                        if (isVideoFullscreen && adMobBannerView != null && adsEnabled) {
                            adMobBannerView.setVisibility(View.VISIBLE);
                            adMobBannerView.bringToFront();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                adMobBannerView.setElevation(100f);
                                adMobBannerView.setTranslationZ(100f);
                            }
                        }
                        preloadRewardedVideoAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        mRewardedAd = null;
                        lastRewardedShownTime = System.currentTimeMillis();
                        nextRewardedIntervalMs = getRandomRewardedIntervalMs();
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

            long now = System.currentTimeMillis();
            boolean shouldShowAd = (lastPostAdShownTime == 0 || (now - lastPostAdShownTime) >= postAdCooldownMs);

            if (shouldShowAd) {
                if (mInterstitialAd != null) {
                    lastPostAdShownTime = now;
                    Log.d(TAG, "Showing Interstitial on post click. Next ad available after 5-min cooldown.");
                    mInterstitialAd.show(MainActivity.this);
                    return;
                } else {
                    preloadInterstitialAd();
                }
            } else {
                long remainingSec = (postAdCooldownMs - (now - lastPostAdShownTime)) / 1000;
                Log.d(TAG, "Post click ad in 5-min cooldown. Remaining: " + remainingSec + "s. Opening post directly without ads.");
            }

            // During 5-minute cooldown or ad not ready: open post immediately without any ad!
            executePendingNavigation();
        });
    }

    private void checkAndShowPeriodicRewarded() {
        long now = System.currentTimeMillis();
        if (rewardedAdUnitId == null || rewardedAdUnitId.trim().isEmpty() ||
                now - lastRewardedShownTime < nextRewardedIntervalMs || isOffline || !adsEnabled) {
            return;
        }

        if (mRewardedAd != null) {
            mRewardedAd.show(this, rewardItem -> Log.d(TAG, "AdMob random periodic reward earned: " + rewardItem.getAmount()));
            lastRewardedShownTime = System.currentTimeMillis();
            nextRewardedIntervalMs = getRandomRewardedIntervalMs();
            Log.d(TAG, "Next random rewarded video ad scheduled in " + (nextRewardedIntervalMs / 60000.0) + " minutes");
        } else {
            preloadRewardedVideoAd();
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

        // 1. Third-party cookies & Session cookies configuration
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        WebSettings settings = webView.getSettings();

        // 2. JavaScript & JavaScript Optimisation & Security
        settings.setJavaScriptEnabled(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setEnableSmoothTransition(true);

        // 3. Pop-up and redirect support
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        // 4. Sound & Audio Autoplay (No user gesture block)
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 5. Intrusive ads & Embedded content (Mixed Content: HTTP inside HTTPS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);

        // 6. On-device site data, Data Store, Local Storage, IndexedDB & Cache
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // On modern Android (API 33+), AppCache is removed from WebSettings.
        // Invoke via reflection to maintain backwards compatibility on older Android versions without breaking API 33+ builds.
        try {
            File appCacheDir = new File(getApplicationContext().getCacheDir(), "webcache");
            if (!appCacheDir.exists()) appCacheDir.mkdirs();
            Method setAppCachePathMethod = settings.getClass().getMethod("setAppCachePath", String.class);
            setAppCachePathMethod.invoke(settings, appCacheDir.getAbsolutePath());
            Method setAppCacheEnabledMethod = settings.getClass().getMethod("setAppCacheEnabled", boolean.class);
            setAppCacheEnabledMethod.invoke(settings, true);
        } catch (Throwable ignored) {}
        try {
            File dbDir = getApplicationContext().getDir("databases", Context.MODE_PRIVATE);
            if (!dbDir.exists()) dbDir.mkdirs();
            Method setDatabasePathMethod = settings.getClass().getMethod("setDatabasePath", String.class);
            setDatabasePathMethod.invoke(settings, dbDir.getPath());
        } catch (Throwable ignored) {}

        // 7. Location (Geolocation)
        settings.setGeolocationEnabled(true);
        try {
            File geoDir = new File(getApplicationContext().getFilesDir(), "geolocation");
            if (!geoDir.exists()) geoDir.mkdirs();
            Method setGeoPathMethod = settings.getClass().getMethod("setGeolocationDatabasePath", String.class);
            setGeoPathMethod.invoke(settings, geoDir.getPath());
        } catch (Throwable ignored) {}

        // 8. Viewport, zoom, and forced Android user-agent
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(FORCED_USER_AGENT);

        // 9. Automatic Download Manager Listener
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Downloading file...");
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    request.setTitle(filename);
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(MainActivity.this, "Downloading " + filename + "...", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Automatic download fallback: " + e.getMessage());
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(i);
                    } catch (Exception ignored) {}
                }
            }
        });

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
                        adMobBannerView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        if (visible) {
                            adMobBannerView.bringToFront();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                adMobBannerView.setElevation(100f);
                                adMobBannerView.setTranslationZ(100f);
                            }
                        }
                    }
                });
            }

            @JavascriptInterface
            public void setVideoFullscreen(final boolean isFs) {
                runOnUiThread(() -> {
                    isVideoFullscreen = isFs;
                    if (isFs) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                    // Keep banner ad visible and overlaying even in fullscreen video mode
                    if (adMobBannerView != null && adsEnabled) {
                        adMobBannerView.setVisibility(View.VISIBLE);
                        adMobBannerView.bringToFront();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            adMobBannerView.setElevation(100f);
                            adMobBannerView.setTranslationZ(100f);
                        }
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

            // 10. Microphone, Camera & Protected Content / DRM (RESOURCE_PROTECTED_MEDIA_ID)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> {
                    try {
                        request.grant(request.getResources());
                        Log.d(TAG, "WebChromeClient auto-granted permissions: " + Arrays.toString(request.getResources()));
                    } catch (Exception e) {
                        Log.w(TAG, "Permission grant failed: " + e.getMessage());
                    }
                });
            }

            // 11. Location (Geolocation Permission Prompt Callback)
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
                Log.d(TAG, "WebChromeClient auto-granted geolocation for origin: " + origin);
            }

            // 12. Pop-up and redirect handling (window.open & target="_blank")
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popWebView = new WebView(MainActivity.this);
                WebSettings popSettings = popWebView.getSettings();
                popSettings.setJavaScriptEnabled(true);
                popSettings.setDomStorageEnabled(true);
                popSettings.setUserAgentString(FORCED_USER_AGENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(popWebView, true);
                }
                popWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        return handlePopupTarget(req != null && req.getUrl() != null ? req.getUrl().toString() : null);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        return handlePopupTarget(url);
                    }

                    private boolean handlePopupTarget(String target) {
                        if (target == null || target.isEmpty()) return false;
                        if (target.contains("blogspot.com") || target.contains("hdskay")) {
                            handlePostClick(target);
                        } else {
                            openInCustomTabs(target);
                        }
                        return true;
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popWebView);
                resultMsg.sendToTarget();
                return true;
            }

            // 13. Embedded Content: Fullscreen HTML5 Video Players & Custom Views
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

                fullscreenContainer.addView(customVideoView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.bringToFront();

                // Keep banner ad visible as an overlay over the fullscreen video
                if (adMobBannerView != null && adsEnabled) {
                    adMobBannerView.setVisibility(View.VISIBLE);
                    adMobBannerView.bringToFront();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        adMobBannerView.setElevation(100f);
                        adMobBannerView.setTranslationZ(100f);
                    }
                }

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

                // Keep banner ad visible and on top
                if (adMobBannerView != null && adsEnabled) {
                    adMobBannerView.setVisibility(View.VISIBLE);
                    adMobBannerView.bringToFront();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        adMobBannerView.setElevation(100f);
                        adMobBannerView.setTranslationZ(100f);
                    }
                }
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
                if (host != null && (host.contains("blogspot.com") || host.contains("hdskay"))) {
                    String path = uri.getPath();
                    if (path != null && (path.endsWith(".html") || path.contains(".html") || path.contains("/search") || path.contains("/p/") || path.length() > 1)) {
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
                CookieManager.getInstance().flush();
            }
        });
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.CAMERA);
            perms.add(Manifest.permission.RECORD_AUDIO);
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }

            List<String> needed = new ArrayList<>();
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }
            if (!needed.isEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "Permissions result: " + Arrays.toString(permissions) + " -> " + Arrays.toString(grantResults));
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
