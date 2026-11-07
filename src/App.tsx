import React, { useState, useEffect, useRef } from 'react';
import { AdMobBanner } from './components/AdMobBanner';
import { AdMobRewardedVideo } from './components/AdMobRewardedVideo';
import { AdMobInterstitial } from './components/AdMobInterstitial';
import { AdMobConfigModal } from './components/AdMobConfigModal';
import { NoInternetPopup } from './components/NoInternetPopup';
import { CustomTabModal } from './components/CustomTabModal';
import { Maximize2, Minimize2, WifiOff, Database } from 'lucide-react';
import { subscribeToAdMobConfig, DEFAULT_ADMOB_CONFIG } from './firebase';
import type { AdMobConfig } from './types';
import './types';

const TARGET_URL = 'https://hdskay.blogspot.com';
const REQUIRED_USER_AGENT =
  'Mozilla/5.0 (Linux; Android 10; K; AppSecret123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36';

export default function App() {
  const [currentUrl, setCurrentUrl] = useState<string>(
    `/api/proxy?url=${encodeURIComponent(TARGET_URL)}&ua=${encodeURIComponent(REQUIRED_USER_AGENT)}`
  );
  const [isLoading, setIsLoading] = useState(true);

  // Firebase Realtime Database AdMob configuration
  const [adMobConfig, setAdMobConfig] = useState<AdMobConfig>(DEFAULT_ADMOB_CONFIG);
  const [configSource, setConfigSource] = useState<'rtdb' | 'default'>('default');
  const [showConfigModal, setShowConfigModal] = useState<boolean>(false);

  // Fullscreen Rewarded Video Ad state: triggers automatically every 10-15 minutes (organic pacing)
  const [showRewarded, setShowRewarded] = useState<boolean>(false);
  const [pendingUrl, setPendingUrl] = useState<string | null>(null);

  // Interstitial Ad state: triggers on post click with 5-minute cooldown (within 5 minutes, posts open instantly!)
  const [showInterstitial, setShowInterstitial] = useState<boolean>(false);
  const [lastPostAdShownTime, setLastPostAdShownTime] = useState<number>(0);

  // Chrome Custom Tab state for external / extra links
  const [customTabUrl, setCustomTabUrl] = useState<string | null>(null);

  // Offline / No Internet state
  const [isOffline, setIsOffline] = useState<boolean>(!navigator.onLine);

  // Fullscreen state
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [isVideoFullscreen, setIsVideoFullscreen] = useState<boolean>(false);

  const iframeRef = useRef<HTMLIFrameElement | null>(null);

  // Splash Screen preview state
  const [showSplash, setShowSplash] = useState<boolean>(true);

  // 1. Subscribe to Firebase Realtime Database for dynamic AdMob ad units
  useEffect(() => {
    const unsubscribe = subscribeToAdMobConfig((config, source) => {
      setAdMobConfig(config);
      setConfigSource(source);
      console.log(`[AdMob] Config loaded from ${source}:`, config);
    });

    return () => unsubscribe();
  }, []);

  // 2. Auto hide splash screen after 2.5 seconds
  useEffect(() => {
    const timer = setTimeout(() => {
      setShowSplash(false);
    }, 2500);
    return () => clearTimeout(timer);
  }, []);

  // 3. Listen for navigation & Custom Tab messages from the proxied iframe
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (!event.data || typeof event.data !== 'object') return;

      // Internal Blog navigation -> Shows Interstitial on post click, then 5-minute cooldown where posts open instantly!
      if (event.data.type === 'HDSKAY_PAGE_NAV') {
        const target = event.data.targetUrl;
        if (target) {
          const now = Date.now();
          const cooldownMs = (adMobConfig.postAdCooldownMinutes || 5) * 60 * 1000;
          const shouldShowAd =
            adMobConfig.adsEnabled &&
            Boolean(adMobConfig.interstitialAdUnitId) &&
            (lastPostAdShownTime === 0 || now - lastPostAdShownTime >= cooldownMs);

          if (shouldShowAd) {
            setPendingUrl(target);
            setLastPostAdShownTime(now);
            setShowInterstitial(true);
          } else {
            // In 5-minute cooldown period: post opens immediately without any ad interruption
            setCurrentUrl(target);
            setIsLoading(true);
          }
        }
      }

      // Extra / External link click -> Open in Chrome Custom Tab
      if (event.data.type === 'HDSKAY_OPEN_CUSTOM_TAB') {
        const externalTarget = event.data.targetUrl;
        if (externalTarget) {
          setCustomTabUrl(externalTarget);
        }
      }

      // HTML5 Video Fullscreen state change
      if (event.data.type === 'HDSKAY_FULLSCREEN_CHANGE') {
        const isFs = Boolean(event.data.isFullscreen);
        setIsVideoFullscreen(isFs);

        // Attempt screen orientation lock to landscape during video fullscreen
        try {
          if (isFs && screen.orientation && typeof (screen.orientation as any).lock === 'function') {
            (screen.orientation as any).lock('landscape').catch(() => {});
          } else if (!isFs && screen.orientation && typeof screen.orientation.unlock === 'function') {
            screen.orientation.unlock();
          }
        } catch {}
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [adMobConfig.adsEnabled, adMobConfig.interstitialAdUnitId, adMobConfig.postAdCooldownMinutes, lastPostAdShownTime]);

  // 4. Monitor network connectivity changes
  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // 5. Monitor Fullscreen changes
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(Boolean(document.fullscreenElement));
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  // 6. Keep Screen Awake (Wake Lock API) whenever the app is open
  useEffect(() => {
    let wakeLockSentinel: any = null;

    const requestWakeLock = async () => {
      try {
        if ('wakeLock' in navigator) {
          wakeLockSentinel = await (navigator as any).wakeLock.request('screen');
        }
      } catch {
        // Ignored
      }
    };

    requestWakeLock();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        requestWakeLock();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (wakeLockSentinel) {
        wakeLockSentinel.release().catch(() => {});
      }
    };
  }, []);

  // 7. Automatic Rewarded Video Ad timer (random interval between 10 to 15 minutes for organic bot protection)
  // 7. Automatic Rewarded Video Ad timer (random interval between 10 to 15 minutes, triggers even during fullscreen video)
  useEffect(() => {
    if (!adMobConfig.adsEnabled || !adMobConfig.rewardedAdUnitId) return;

    // Pick random interval between 10 and 15 minutes (in ms)
    const getRandomIntervalMs = () => (10 + Math.random() * 5) * 60 * 1000;

    let timeoutId: NodeJS.Timeout;
    const scheduleNextRewardedAd = () => {
      const nextDelay = getRandomIntervalMs();
      console.log(`[AdMob] Next random rewarded video ad scheduled in ${(nextDelay / 60000).toFixed(1)} minutes`);
      timeoutId = setTimeout(() => {
        if (!isOffline && document.visibilityState === 'visible') {
          setShowRewarded(true);
        }
        scheduleNextRewardedAd();
      }, nextDelay);
    };

    scheduleNextRewardedAd();

    return () => clearTimeout(timeoutId);
  }, [adMobConfig.adsEnabled, adMobConfig.rewardedAdUnitId, isOffline]);

  // Dismiss Rewarded Video Ad
  const handleRewardedClose = () => {
    setShowRewarded(false);
  };

  // Dismiss Interstitial Ad and load the requested post (initiates 5-minute cooldown)
  const handleInterstitialClose = () => {
    setShowInterstitial(false);
    if (pendingUrl) {
      setCurrentUrl(pendingUrl);
      setPendingUrl(null);
      setIsLoading(true);
    }
  };

  // Toggle true Fullscreen
  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  };

  const handleRetryInternet = () => {
    if (navigator.onLine) {
      setIsOffline(false);
    } else {
      setIsOffline(true);
    }
  };

  return (
    <div className="w-screen h-screen m-0 p-0 overflow-hidden bg-[#000000] relative flex flex-col justify-between select-none">
      {/* Top Loading Progress Indicator */}
      {isLoading && (
        <div className="absolute top-0 left-0 right-0 h-1 bg-transparent z-50 overflow-hidden">
          <div className="h-full bg-amber-500 animate-pulse w-full duration-700" />
        </div>
      )}

      {/* Main WebView Window (True Edge-to-Edge Fullscreen) */}
      <div className="w-full flex-1 relative overflow-hidden bg-black">
        <iframe
          id="webview-frame"
          ref={iframeRef}
          src={currentUrl}
          title="HDSKay WebView"
          className="w-full h-full border-none m-0 p-0 block"
          style={{ scrollbarWidth: 'none' }}
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          onLoad={() => setIsLoading(false)}
        />
      </div>

      {/* Floating Controls: AdMob/RTDB Info, Fullscreen & Offline Trigger */}
      {!isVideoFullscreen && (
        <div className="absolute top-3 right-3 z-40 flex items-center gap-2">
          {/* Firebase RTDB AdMob Manager Button */}
          <button
            id="admob-rtdb-config-btn"
            type="button"
            onClick={() => setShowConfigModal(true)}
            className="bg-black/75 hover:bg-black/95 text-amber-300 hover:text-amber-200 border border-amber-500/40 p-2 rounded-full backdrop-blur-md text-xs flex items-center gap-1 shadow-lg transition-all cursor-pointer"
            title="Firebase Realtime Database AdMob Units"
          >
            <Database className="w-3.5 h-3.5 text-amber-400" />
            <span className="hidden sm:inline text-[11px] pr-1 font-semibold">AdMob RTDB</span>
          </button>

          {/* Test Offline Simulator Button */}
          <button
            id="simulate-offline-btn"
            type="button"
            onClick={() => setIsOffline((prev) => !prev)}
            className="bg-black/70 hover:bg-black/90 text-gray-300 hover:text-white border border-gray-700/60 p-2 rounded-full backdrop-blur-md text-xs flex items-center gap-1 shadow-lg transition-all cursor-pointer"
            title="Test No Internet Popup"
          >
            <WifiOff className="w-3.5 h-3.5 text-amber-400" />
            <span className="hidden sm:inline text-[11px] pr-1">Offline Test</span>
          </button>

          {/* Fullscreen Toggle */}
          <button
            id="toggle-fullscreen-btn"
            type="button"
            onClick={toggleFullscreen}
            className="bg-black/70 hover:bg-black/90 text-gray-300 hover:text-white border border-gray-700/60 p-2 rounded-full backdrop-blur-md shadow-lg transition-all cursor-pointer"
            title={isFullscreen ? 'Exit Fullscreen' : 'Enter Immersive Fullscreen'}
          >
            {isFullscreen ? (
              <Minimize2 className="w-3.5 h-3.5 text-amber-400" />
            ) : (
              <Maximize2 className="w-3.5 h-3.5 text-amber-400" />
            )}
          </button>
        </div>
      )}

      {/* Persistent AdMob Bottom Banner Ad (Loaded from Firebase RTDB) - Always visible as overlay even during fullscreen video */}
      {adMobConfig.adsEnabled && Boolean(adMobConfig.bannerAdUnitId) && (
        <AdMobBanner
          adUnitId={adMobConfig.bannerAdUnitId}
          enabled={adMobConfig.adsEnabled}
          isOverlay={isVideoFullscreen || isFullscreen}
        />
      )}

      {/* Chrome Custom Tab Modal (Opens when extra/external links are clicked) */}
      <CustomTabModal
        url={customTabUrl}
        onClose={() => setCustomTabUrl(null)}
      />

      {/* Custom "No Internet Connection" Popup Modal */}
      <NoInternetPopup
        isOpen={isOffline}
        onRetry={handleRetryInternet}
        onClose={() => setIsOffline(false)}
      />

      {/* Fullscreen AdMob Rewarded Video Ad (triggers randomly every 10-15 min) */}
      {showRewarded && adMobConfig.adsEnabled && Boolean(adMobConfig.rewardedAdUnitId) && (
        <AdMobRewardedVideo
          adUnitId={adMobConfig.rewardedAdUnitId}
          enabled={adMobConfig.adsEnabled}
          onClose={handleRewardedClose}
        />
      )}

      {/* Fullscreen AdMob Interstitial Ad (triggers automatically on every 10th post open) */}
      {showInterstitial && adMobConfig.adsEnabled && Boolean(adMobConfig.interstitialAdUnitId) && (
        <AdMobInterstitial
          adUnitId={adMobConfig.interstitialAdUnitId}
          enabled={adMobConfig.adsEnabled}
          onClose={handleInterstitialClose}
        />
      )}

      {/* Firebase RTDB AdMob Units Inspector / Editor Modal */}
      <AdMobConfigModal
        isOpen={showConfigModal}
        config={adMobConfig}
        configSource={configSource}
        onClose={() => setShowConfigModal(false)}
      />

      {/* Cinematic Splash Screen Overlay */}
      {showSplash && (
        <div
          id="app-splash-screen"
          className="fixed inset-0 z-[120] bg-black flex items-center justify-center transition-opacity duration-500 overflow-hidden"
          onClick={() => setShowSplash(false)}
        >
          <img
            src="/splash.png"
            alt="Movie App Splash Screen"
            referrerPolicy="no-referrer"
            className="w-full h-full object-cover object-center animate-in fade-in zoom-in-95 duration-500"
          />
        </div>
      )}
    </div>
  );
}
