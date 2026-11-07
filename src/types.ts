export interface AdMobConfig {
  appId: string;
  bannerAdUnitId: string;
  interstitialAdUnitId: string;
  rewardedAdUnitId: string;
  adsEnabled: boolean;
  rewardedIntervalMinutes: number; // 10 to 15 minutes automatic timer
  interstitialPostCount: number; // 10 posts open threshold
  interstitialIntervalMinutes?: number;
}

export interface AndroidAdMobBridge {
  showBanner?: (adUnitId?: string) => void;
  hideBanner?: () => void;
  showInterstitial?: (adUnitId?: string) => void;
  showRewardedVideo?: (adUnitId?: string) => void;
  showRewardedVideoForUrl?: (url: string, adUnitId?: string) => void;
  isNativeApp?: () => boolean;
  setVideoFullscreen?: (isFullscreen: boolean) => void;
  openCustomTab?: (url: string) => void;
}

declare global {
  interface Window {
    AndroidAdMob?: AndroidAdMobBridge;
    // Backward compatibility bridges
    AndroidStartApp?: any;
    Android?: any;
    onRewardedVideoClosed?: () => void;
  }
}

export type AdType = 'rewarded' | 'interstitial' | 'banner';
