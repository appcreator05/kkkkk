# HDSKay WebView — Google AdMob & Firebase Realtime Database Integration

A full-screen edge-to-edge web application migrated to AI Studio (Node.js & React SPA with Vite and Express proxy).

## Features

- **Google AdMob Ad Units (via Firebase Realtime Database)**:
  - Ad units are dynamically loaded in real-time from Firebase Realtime Database (`https://myapps-4a8eb-default-rtdb.firebaseio.com/admob`).
  - **Banner Ad**: Persistent bottom unit with responsive layout and auto-hide during HTML5 video fullscreen.
  - **Rewarded Video Ad**: Triggers on initial app open and page navigation with a 5-second reward countdown and skip mechanism.
  - **Interstitial Ad**: Periodic fullscreen ad with countdown timer based on configurable interval (default: 10 minutes).
  - **In-App RTDB Manager Modal**: An interactive modal to inspect and update ad units directly in Firebase RTDB (`/admob`).
- **Edge-to-Edge Fullscreen Experience**: Native-like immersive WebView interface with hardware acceleration and auto-hide navigation.
- **Offline / No-Internet Recovery Modal**: Detects network disconnections in real time with quick connection retry.
- **Chrome Custom Tab Modal**: Clean sandboxed in-app browser for external link navigation without leaving the application.
- **Proxy Server**: Express proxy layer enforcing custom user agents and handling Blogger CORS and frame headers.

## Firebase Realtime Database AdMob Schema

The app subscribes to the `/admob` node (with fallback to `/ads`). You can set these fields directly in Firebase Realtime Database:

```json
{
  "admob": {
    "appId": "ca-app-pub-3940256099942544~3347511713",
    "bannerAdUnitId": "ca-app-pub-3940256099942544/6300978111",
    "interstitialAdUnitId": "ca-app-pub-3940256099942544/1033173712",
    "rewardedAdUnitId": "ca-app-pub-3940256099942544/5224354917",
    "adsEnabled": true,
    "interstitialIntervalMinutes": 10
  }
}
```

## Development

```bash
npm install
npm run dev
```

Build for production:

```bash
npm run build
npm start
```