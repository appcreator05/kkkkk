import React, { useEffect } from 'react';
import { ExternalLink } from 'lucide-react';

interface AdMobBannerProps {
  adUnitId: string;
  enabled?: boolean;
  isOverlay?: boolean;
}

export const AdMobBanner: React.FC<AdMobBannerProps> = ({ adUnitId, enabled = true, isOverlay = false }) => {
  // If ads are disabled in Firebase RTDB, don't render
  if (!enabled) return null;

  // If running inside Android wrapper with native AdMob SDK, communicate with native layer
  useEffect(() => {
    if (
      typeof window !== 'undefined' &&
      window.AndroidAdMob &&
      typeof window.AndroidAdMob.showBanner === 'function'
    ) {
      try {
        window.AndroidAdMob.showBanner(adUnitId);
      } catch (e) {
        console.error('AndroidAdMob.showBanner error:', e);
      }
    }
  }, [adUnitId]);

  const handleAdClick = () => {
    window.open('https://admob.google.com', '_blank', 'noopener,noreferrer');
  };

  // Truncate AdMob ad unit ID for badge display (e.g., .../6300978111)
  const shortUnitId = adUnitId ? adUnitId.split('/').pop() || adUnitId : 'AdMob';

  return (
    <div
      id="admob-bottom-banner"
      className={`w-full bg-[#0a0d14]/95 border-t border-[#1a2233] shadow-2xl flex items-center justify-center px-2 sm:px-4 py-1 select-none transition-all ${
        isOverlay
          ? 'fixed bottom-0 left-0 right-0 z-[99999] backdrop-blur-md'
          : 'z-40 relative'
      }`}
      style={{ minHeight: '52px' }}
    >
      {/* Standard Google AdMob Mobile Banner (320x50 standard banner unit) */}
      <div
        onClick={handleAdClick}
        className="w-full max-w-[640px] h-[46px] bg-[#101622] hover:bg-[#151d2c] border border-[#202d44] hover:border-amber-500/40 rounded-lg flex items-center justify-between px-3 relative overflow-hidden cursor-pointer transition-colors duration-200 group"
      >
        {/* Subtle standard "Ad" & AdMob marker */}
        <div className="absolute top-0.5 right-1 flex items-center gap-1 pointer-events-none">
          <span className="text-[8px] font-semibold text-amber-400/90 bg-[#0a0d14] px-1 rounded border border-amber-500/30">
            AdMob #{shortUnitId}
          </span>
          <span className="text-[8px] font-medium text-slate-400 bg-[#0a0d14] px-1 rounded border border-slate-700">
            Ad
          </span>
        </div>

        {/* Left: App / Sponsor info */}
        <div className="flex items-center gap-2.5 truncate mr-2">
          {/* AdMob icon badge */}
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-amber-500 via-rose-500 to-indigo-600 flex items-center justify-center font-black text-white text-xs shadow-md shrink-0">
            🎬
          </div>

          {/* Ad details */}
          <div className="flex flex-col text-left truncate">
            <span className="text-xs font-bold text-white group-hover:text-amber-300 transition-colors truncate">
              Cinema Plus 4K & Live Streaming
            </span>
            <span className="text-[10px] text-slate-400 truncate">
              Ultra HD Movies • Zero Buffer Streaming • Google AdMob
            </span>
          </div>
        </div>

        {/* Right: CTA Button */}
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            className="px-3 py-1.5 bg-gradient-to-r from-amber-500 to-orange-600 hover:from-amber-400 hover:to-orange-500 text-white font-bold text-[11px] rounded-md shadow-md flex items-center gap-1 active:scale-95 transition-transform"
          >
            <span>INSTALL</span>
            <ExternalLink className="w-3 h-3 text-white/80" />
          </button>
        </div>
      </div>
    </div>
  );
};
