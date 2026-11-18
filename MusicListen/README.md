# Listen

Always-on Android listener that **locally** detects nearby music, and only then calls AudD once to identify the track. Results appear as a short notification (title + artist).

## Behavior

1. Foreground service runs continuously (quiet ongoing notification — required by Android for mic).
2. Every ~1s it analyzes a short mic probe **on-device** (energy, sustain, band balance). Silence / noise / speech-like choppiness → **no API call**.
3. After ~2 consecutive music-like probes, it records ~7s and calls AudD **once**.
4. Cooldown after match (~2 min), miss (~75s), or error (~90s). Same track is not re-notified for ~3 min.
5. Restarts after reboot / app update if you left it enabled. Asks to disable battery optimization for reliability.

## Install

Use `dist/Listen-debug.apk`.

1. Open **Listen**, paste [AudD](https://dashboard.audd.io/) token.
2. Tap **Start always-on**, allow mic + notifications, allow unrestricted battery.
3. When music plays nearby, you get a message notification: track + artist.

## Rebuild

```bash
export ANDROID_HOME=$HOME/android-sdk
cd MusicListen
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/Listen-debug.apk
```
