import { initializeApp, getApps, getApp } from 'firebase/app';
import { getDatabase, ref, onValue, set, get } from 'firebase/database';
import type { AdMobConfig } from './types';

// Web app's Firebase configuration provided by the user
export const firebaseConfig = {
  apiKey: "AIzaSyCyDd6rCV7WaQe8sMF0Xmob3dpm6z6wEEQ",
  authDomain: "myapps-4a8eb.firebaseapp.com",
  databaseURL: "https://myapps-4a8eb-default-rtdb.firebaseio.com",
  projectId: "myapps-4a8eb",
  storageBucket: "myapps-4a8eb.appspot.com",
  messagingSenderId: "342267842050",
  appId: "1:342267842050:web:4749870bb18b05ed94611b"
};

// Initialize Firebase
export const firebaseApp = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
export const rtdb = getDatabase(firebaseApp);

// Standard Google AdMob Official Test Ad Unit IDs (used as default fallback if RTDB is not yet set)
export const DEFAULT_ADMOB_CONFIG: AdMobConfig = {
  appId: 'ca-app-pub-3940256099942544~3347511713',
  bannerAdUnitId: 'ca-app-pub-3940256099942544/6300978111',
  interstitialAdUnitId: 'ca-app-pub-3940256099942544/1033173712',
  rewardedAdUnitId: 'ca-app-pub-3940256099942544/5224354917',
  adsEnabled: true,
  interstitialIntervalMinutes: 10,
};

/**
 * Subscribes in real-time to the AdMob configuration in Firebase Realtime Database.
 * Listens to 'admob' and 'ads' nodes with fallback to defaults.
 */
export function subscribeToAdMobConfig(
  onConfigUpdate: (config: AdMobConfig, source: 'rtdb' | 'default') => void
): () => void {
  const admobRef = ref(rtdb, 'admob');

  const unsubscribe = onValue(
    admobRef,
    (snapshot) => {
      const data = snapshot.val();
      if (data && typeof data === 'object') {
        const merged: AdMobConfig = {
          appId: data.appId || data.app_id || DEFAULT_ADMOB_CONFIG.appId,
          bannerAdUnitId:
            data.bannerAdUnitId ||
            data.banner_ad_unit_id ||
            data.banner ||
            DEFAULT_ADMOB_CONFIG.bannerAdUnitId,
          interstitialAdUnitId:
            data.interstitialAdUnitId ||
            data.interstitial_ad_unit_id ||
            data.interstitial ||
            DEFAULT_ADMOB_CONFIG.interstitialAdUnitId,
          rewardedAdUnitId:
            data.rewardedAdUnitId ||
            data.rewarded_ad_unit_id ||
            data.rewarded ||
            DEFAULT_ADMOB_CONFIG.rewardedAdUnitId,
          adsEnabled:
            data.adsEnabled !== undefined
              ? Boolean(data.adsEnabled)
              : data.enabled !== undefined
              ? Boolean(data.enabled)
              : true,
          interstitialIntervalMinutes:
            Number(data.interstitialIntervalMinutes || data.interstitial_interval_minutes) || 10,
        };
        onConfigUpdate(merged, 'rtdb');
      } else {
        // Try fallback 'ads' node
        const adsRef = ref(rtdb, 'ads');
        get(adsRef)
          .then((altSnap) => {
            const altData = altSnap.val();
            if (altData && typeof altData === 'object') {
              const mergedAlt: AdMobConfig = {
                appId: altData.appId || altData.app_id || DEFAULT_ADMOB_CONFIG.appId,
                bannerAdUnitId:
                  altData.bannerAdUnitId ||
                  altData.banner_ad_unit_id ||
                  altData.banner ||
                  DEFAULT_ADMOB_CONFIG.bannerAdUnitId,
                interstitialAdUnitId:
                  altData.interstitialAdUnitId ||
                  altData.interstitial_ad_unit_id ||
                  altData.interstitial ||
                  DEFAULT_ADMOB_CONFIG.interstitialAdUnitId,
                rewardedAdUnitId:
                  altData.rewardedAdUnitId ||
                  altData.rewarded_ad_unit_id ||
                  altData.rewarded ||
                  DEFAULT_ADMOB_CONFIG.rewardedAdUnitId,
                adsEnabled:
                  altData.adsEnabled !== undefined
                    ? Boolean(altData.adsEnabled)
                    : altData.enabled !== undefined
                    ? Boolean(altData.enabled)
                    : true,
                interstitialIntervalMinutes:
                  Number(altData.interstitialIntervalMinutes || altData.interstitial_interval_minutes) || 10,
              };
              onConfigUpdate(mergedAlt, 'rtdb');
            } else {
              onConfigUpdate(DEFAULT_ADMOB_CONFIG, 'default');
            }
          })
          .catch(() => {
            onConfigUpdate(DEFAULT_ADMOB_CONFIG, 'default');
          });
      }
    },
    (error) => {
      console.warn('[Firebase RTDB] Failed to read admob node:', error.message);
      onConfigUpdate(DEFAULT_ADMOB_CONFIG, 'default');
    }
  );

  return unsubscribe;
}

/**
 * Allows writing or updating AdMob Ad Units directly to Firebase Realtime Database
 */
export async function saveAdMobConfigToRTDB(config: Partial<AdMobConfig>): Promise<void> {
  const admobRef = ref(rtdb, 'admob');
  await set(admobRef, {
    appId: config.appId ?? DEFAULT_ADMOB_CONFIG.appId,
    bannerAdUnitId: config.bannerAdUnitId ?? DEFAULT_ADMOB_CONFIG.bannerAdUnitId,
    interstitialAdUnitId: config.interstitialAdUnitId ?? DEFAULT_ADMOB_CONFIG.interstitialAdUnitId,
    rewardedAdUnitId: config.rewardedAdUnitId ?? DEFAULT_ADMOB_CONFIG.rewardedAdUnitId,
    adsEnabled: config.adsEnabled ?? true,
    interstitialIntervalMinutes: config.interstitialIntervalMinutes ?? 10,
    updatedAt: new Date().toISOString(),
  });
}
