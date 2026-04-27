# CI — Build APK

`build-apk.yml` builds the TradeFlow Sovereign Agent on every push and on
manual dispatch.

## What it does

1. Spins up `ubuntu-latest` with JDK 17, Python 3.11, and the Android SDK.
2. Restores the Gradle cache.
3. Runs `./gradlew :app:assembleDebug` inside `TradeFlow-Android/`.
4. Uploads the resulting `.apk` as a workflow artifact named
   **`tradeflow-debug-apk`** (retained for 14 days).

## How to download the APK

1. Open the repository on GitHub.
2. Go to the **Actions** tab.
3. Click the most recent **Build TradeFlow APK** run.
4. Scroll to the **Artifacts** section and download `tradeflow-debug-apk`.
5. Unzip — inside is `app-debug.apk`. Sideload onto an Android 13+ device:

   ```
   adb install -r app-debug.apk
   ```

## Notes

- The APK is **unsigned debug**. Don't ship it to the Play Store from this
  workflow; for that, switch to `assembleRelease` and add a signing config
  driven by encrypted GitHub Secrets (`KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
- The first run will be slow (~10–15 min) because Chaquopy downloads the
  CPython runtime and pip-installs `websockets` + `requests`. Subsequent
  runs are fast thanks to the Gradle cache.
- If the run fails on `assembleDebug`, the most likely cause is the
  Chaquopy plugin needing a newer Android Gradle Plugin or a buildToolsVersion
  bump — open the failed run's logs and grep for `chaquopy`.
