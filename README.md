# HDSKay WebView — Google AdMob & Firebase Realtime Database Integration

A full-screen edge-to-edge application featuring native Android APK build support via **GitHub Actions** and web preview.

## 🚀 How to Build APK via GitHub Actions

This repository includes a preconfigured GitHub Actions workflow (`.github/workflows/build-apk.yml`) that automatically builds both **Release APK** and **Debug APK**.

### Step 1: Push Code to GitHub
Push your repository or branch to GitHub:
```bash
git add .
git commit -m "Add AdMob, Firebase RTDB & GitHub Actions APK build"
git push origin main
```

### Step 2: Automatic APK Build
1. Go to your GitHub repository in your browser.
2. Click on the **Actions** tab at the top.
3. You will see the workflow **"Build Android APK (AdMob & Firebase RTDB)"** running automatically.
4. You can also trigger it manually anytime by selecting the workflow and clicking **"Run workflow"**.

### Step 3: Download the Compiled APK
1. When the workflow finishes (green checkmark ✅), click on the completed run.
2. Scroll down to the **Artifacts** section at the bottom.
3. Download:
   - **`HDSKay-Release-APK`**: Signed production-ready APK ready to install on Android phones.
   - **`HDSKay-Debug-APK`**: Testing APK for debug and log inspections.

---

## 📱 Features

- **GitHub Actions Auto Build**: Automatically sets up Java 17, builds Gradle project, signs the APK with a generated release keystore, and uploads downloadable artifacts.
- **Google AdMob Ad Units (via Firebase Realtime Database)**:
  - Ad units are dynamically loaded in real-time from Firebase Realtime Database (`https://myapps-4a8eb-default-rtdb.firebaseio.com/admob`).
  - **Banner Ad**: Persistent bottom unit with responsive layout and auto-hide during HTML5 video fullscreen.
  - **Rewarded Video Ad**: Triggers on initial app open and page navigation with a 5-second reward countdown and skip mechanism.
  - **Interstitial Ad**: Periodic fullscreen ad with countdown timer based on configurable interval (default: 10 minutes).
  - **In-App RTDB Manager Modal**: An interactive modal to inspect and update ad units directly in Firebase RTDB (`/admob`).
- **Edge-to-Edge Fullscreen Experience**: Native-like immersive WebView interface with hardware acceleration and auto-hide navigation.
- **Offline / No-Internet Recovery Modal**: Detects network disconnections in real time with quick connection retry.
- **Chrome Custom Tab Modal**: Clean sandboxed in-app browser for external link navigation without leaving the application.

## 🗄️ Firebase Realtime Database AdMob Schema

The app subscribes to the `/admob` node. You can set these fields directly in Firebase Realtime Database:

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

## 💻 Web Preview Development

```bash
npm install
npm run dev
```

Build for production:

```bash
npm run build
npm start
```