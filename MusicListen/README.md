# Listen

Android app that listens in the background and shows nearby track matches as a **short notification message** (title + artist), not a full-screen recognizer UI.

## How it works

1. Enter your [AudD](https://dashboard.audd.io/) API token in the app.
2. Tap **Start** and grant microphone (+ notifications on Android 13+).
3. While active, Android shows a quiet ongoing notification (required for mic access).
4. When a track is matched, you get a normal message-style notification: **track name** and **artist**.

## Build APK

```bash
export ANDROID_HOME=$HOME/android-sdk
cd MusicListen
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Optional: put the token in `local.properties` as `audd.api.token=...` before building so it is prefilled via BuildConfig.
