import React, { useEffect, useState } from 'react';
import { X, ExternalLink, Star } from 'lucide-react';

interface AdMobInterstitialProps {
  adUnitId: string;
  enabled?: boolean;
  onClose: () => void;
}

export const AdMobInterstitial: React.FC<AdMobInterstitialProps> = ({
  adUnitId,
  enabled = true,
  onClose,
}) => {
  const [seconds, setSeconds] = useState(3);

  // If ads are disabled in Firebase RTDB, close immediately
  useEffect(() => {
    if (!enabled) {
      onClose();
    }
  }, [enabled, onClose]);

  useEffect(() => {
    // If native Android AdMob bridge is present, invoke it
    if (
      typeof window !== 'undefined' &&
      window.AndroidAdMob &&
      typeof window.AndroidAdMob.showInterstitial === 'function'
    ) {
      try {
        window.AndroidAdMob.showInterstitial(adUnitId);
      } catch (err) {
        console.error('AndroidAdMob.showInterstitial failed:', err);
      }
    }
  }, [adUnitId]);

  useEffect(() => {
    const timer = setInterval(() => {
      setSeconds((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const handleAction = () => {
    window.open('https://admob.google.com', '_blank', 'noopener,noreferrer');
    onClose();
  };

  const shortUnitId = adUnitId ? adUnitId.split('/').pop() || adUnitId : 'AdMob';

  return (
    <div
      id="admob-interstitial-overlay"
      className="fixed inset-0 z-[99998] bg-black/90 backdrop-blur-md flex items-center justify-center p-4 select-none animate-fadeIn"
    >
      <div className="w-full max-w-md bg-[#121620] border border-[#232c40] rounded-2xl shadow-2xl overflow-hidden flex flex-col relative">
        {/* Top bar with Ad label & Close button */}
        <div className="flex items-center justify-between px-4 py-3 bg-[#181f2c] border-b border-[#242d40]">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-300 bg-black/60 border border-slate-700 px-2 py-0.5 rounded">
              Google AdMob
            </span>
            <span className="text-[10px] text-amber-400/90 font-mono">
              #{shortUnitId}
            </span>
          </div>

          {seconds === 0 ? (
            <button
              id="close-admob-interstitial-btn"
              type="button"
              onClick={onClose}
              className="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-200 hover:text-white flex items-center justify-center transition-colors cursor-pointer"
              title="Close Ad"
            >
              <X className="w-4 h-4" />
            </button>
          ) : (
            <span className="text-xs text-slate-400 font-mono">
              Close in {seconds}s
            </span>
          )}
        </div>

        {/* Interstitial Ad Graphic & Body */}
        <div className="p-6 flex flex-col items-center text-center">
          <div className="w-20 h-20 rounded-2xl bg-gradient-to-tr from-amber-500 via-rose-500 to-indigo-600 p-0.5 shadow-xl mb-4">
            <div className="w-full h-full bg-[#0d121c] rounded-2xl flex items-center justify-center text-3xl font-black text-white">
              🎬
            </div>
          </div>

          <h3 className="text-xl font-black text-white mb-1 tracking-tight">
            Top Entertainment & Movies
          </h3>

          <div className="flex items-center justify-center gap-1.5 text-amber-400 text-xs mb-3">
            <Star className="w-3.5 h-3.5 fill-amber-400" />
            <span className="font-bold">4.9</span>
            <span className="text-slate-400">• Verified AdMob Partner</span>
          </div>

          <p className="text-xs sm:text-sm text-slate-300 mb-6 leading-relaxed">
            Experience ultra HD entertainment and high-speed streaming on any mobile device. Instant access with zero lag.
          </p>

          <button
            type="button"
            onClick={handleAction}
            className="w-full py-3 bg-gradient-to-r from-amber-500 to-orange-600 hover:from-amber-400 hover:to-orange-500 text-white font-extrabold text-sm rounded-xl shadow-lg shadow-orange-600/30 flex items-center justify-center gap-2 transition-transform active:scale-98 cursor-pointer"
          >
            <span>INSTALL & PLAY NOW</span>
            <ExternalLink className="w-4 h-4" />
          </button>

          {seconds === 0 && (
            <button
              type="button"
              onClick={onClose}
              className="mt-3 text-xs text-slate-400 hover:text-slate-200 py-1 cursor-pointer"
            >
              Skip & Continue
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
