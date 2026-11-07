import React, { useState } from 'react';
import { X, Database, Save, CheckCircle2, ShieldCheck, RefreshCw } from 'lucide-react';
import type { AdMobConfig } from '../types';
import { saveAdMobConfigToRTDB } from '../firebase';

interface AdMobConfigModalProps {
  isOpen: boolean;
  config: AdMobConfig;
  configSource: 'rtdb' | 'default';
  onClose: () => void;
}

export const AdMobConfigModal: React.FC<AdMobConfigModalProps> = ({
  isOpen,
  config,
  configSource,
  onClose,
}) => {
  const [appId, setAppId] = useState(config.appId);
  const [bannerId, setBannerId] = useState(config.bannerAdUnitId);
  const [interstitialId, setInterstitialId] = useState(config.interstitialAdUnitId);
  const [rewardedId, setRewardedId] = useState(config.rewardedAdUnitId);
  const [intervalMin, setIntervalMin] = useState(config.interstitialIntervalMinutes || 10);
  const [adsEnabled, setAdsEnabled] = useState(config.adsEnabled);

  const [isSaving, setIsSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSaveToRTDB = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setSaveError(null);
    try {
      await saveAdMobConfigToRTDB({
        appId: appId.trim(),
        bannerAdUnitId: bannerId.trim(),
        interstitialAdUnitId: interstitialId.trim(),
        rewardedAdUnitId: rewardedId.trim(),
        interstitialIntervalMinutes: Number(intervalMin),
        adsEnabled,
      });
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 3000);
    } catch (err: any) {
      setSaveError(err.message || 'Failed to update Firebase Realtime Database');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/85 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="bg-[#0f141f] border border-amber-500/30 rounded-2xl max-w-lg w-full text-white shadow-2xl overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-5 py-4 border-b border-slate-800 flex items-center justify-between bg-[#151c2c]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-amber-400">
              <Database className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                <span>Firebase RTDB AdMob Manager</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 font-mono">
                  Live Sync
                </span>
              </h3>
              <p className="text-[11px] text-slate-400">
                Connected: myapps-4a8eb-default-rtdb.firebaseio.com
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Status Alert */}
        <div className="px-5 py-2.5 bg-slate-900/60 border-b border-slate-800/80 flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5 text-slate-300">
            <ShieldCheck className="w-4 h-4 text-amber-400" />
            <span>Active Source:</span>
            <span className="font-semibold text-white uppercase text-[11px]">
              {configSource === 'rtdb' ? 'Firebase Realtime Database' : 'Standard Fallback Config'}
            </span>
          </div>
          <span className="text-[10px] text-slate-500 font-mono">path: /admob</span>
        </div>

        {/* Form to view / update ad unit IDs */}
        <form onSubmit={handleSaveToRTDB} className="p-5 space-y-3.5 text-xs">
          <div>
            <label className="block text-slate-300 font-medium mb-1">
              AdMob App ID
            </label>
            <input
              type="text"
              value={appId}
              onChange={(e) => setAppId(e.target.value)}
              placeholder="ca-app-pub-3940256099942544~3347511713"
              className="w-full bg-[#171f30] border border-slate-700 focus:border-amber-500 rounded-lg px-3 py-2 text-white font-mono text-[11px] outline-none"
            />
          </div>

          <div>
            <label className="block text-slate-300 font-medium mb-1">
              Banner Ad Unit ID
            </label>
            <input
              type="text"
              value={bannerId}
              onChange={(e) => setBannerId(e.target.value)}
              placeholder="ca-app-pub-3940256099942544/6300978111"
              className="w-full bg-[#171f30] border border-slate-700 focus:border-amber-500 rounded-lg px-3 py-2 text-white font-mono text-[11px] outline-none"
            />
          </div>

          <div>
            <label className="block text-slate-300 font-medium mb-1">
              Interstitial Ad Unit ID
            </label>
            <input
              type="text"
              value={interstitialId}
              onChange={(e) => setInterstitialId(e.target.value)}
              placeholder="ca-app-pub-3940256099942544/1033173712"
              className="w-full bg-[#171f30] border border-slate-700 focus:border-amber-500 rounded-lg px-3 py-2 text-white font-mono text-[11px] outline-none"
            />
          </div>

          <div>
            <label className="block text-slate-300 font-medium mb-1">
              Rewarded Video Ad Unit ID
            </label>
            <input
              type="text"
              value={rewardedId}
              onChange={(e) => setRewardedId(e.target.value)}
              placeholder="ca-app-pub-3940256099942544/5224354917"
              className="w-full bg-[#171f30] border border-slate-700 focus:border-amber-500 rounded-lg px-3 py-2 text-white font-mono text-[11px] outline-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-3 pt-1">
            <div>
              <label className="block text-slate-300 font-medium mb-1">
                Interstitial Interval (min)
              </label>
              <input
                type="number"
                min="1"
                max="60"
                value={intervalMin}
                onChange={(e) => setIntervalMin(Number(e.target.value))}
                className="w-full bg-[#171f30] border border-slate-700 focus:border-amber-500 rounded-lg px-3 py-2 text-white font-mono text-[11px] outline-none"
              />
            </div>
            <div className="flex flex-col justify-end">
              <label className="flex items-center gap-2 cursor-pointer pb-2">
                <input
                  type="checkbox"
                  checked={adsEnabled}
                  onChange={(e) => setAdsEnabled(e.target.checked)}
                  className="w-4 h-4 accent-amber-500 rounded cursor-pointer"
                />
                <span className="text-slate-300 font-medium">Ads Enabled</span>
              </label>
            </div>
          </div>

          {savedSuccess && (
            <div className="p-2.5 rounded-lg bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-xs flex items-center gap-2 animate-in fade-in">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>Successfully saved to Firebase Realtime Database (/admob)!</span>
            </div>
          )}

          {saveError && (
            <div className="p-2.5 rounded-lg bg-rose-500/20 border border-rose-500/40 text-rose-300 text-xs">
              {saveError}
            </div>
          )}

          <div className="pt-2 flex items-center justify-end gap-2 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer"
            >
              Close
            </button>
            <button
              type="submit"
              disabled={isSaving}
              className="px-4 py-2 rounded-lg bg-gradient-to-r from-amber-500 to-orange-600 hover:from-amber-400 hover:to-orange-500 text-white font-semibold flex items-center gap-1.5 shadow-lg shadow-amber-500/20 disabled:opacity-50 cursor-pointer"
            >
              {isSaving ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <Save className="w-3.5 h-3.5" />
                  <span>Save to Firebase RTDB</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
