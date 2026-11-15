<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/1af989eb-4fea-4e2f-98a7-10a7786215d9

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## Automated GitHub Builds (CI)

Because AI Studio's GitHub exporter skips updating binary APK files inside `.build-outputs`, pushing this repository triggers the **Android CI Build & Test** workflow on GitHub Actions to:

1. Run `:app:testDebugUnitTest` and `:app:assembleDebug` using JDK 21, Gradle 9.3.1, and Android SDK 36.
2. Automatically commit the refreshed `app-debug.apk` directly into `.build-outputs/` (using `[skip ci]`) so the repository folder stays up to date.
3. Attach the latest `app-debug.apk` to the **`latest`** entry under **Releases** and to **Actions -> Artifacts (`app-debug-apk`)**.

> **One-time GitHub repo check:** In your GitHub repository under **Settings -> Actions -> General -> Workflow permissions**, ensure **Read and write permissions** is selected so Actions can update `.build-outputs/app-debug.apk` and publish the Release APK.

## Latest Build Highlights (`v1.3` / `versionCode 4`)

- **Streamlined Material 3 Finance UX:** Unified the duplicate summary cards into a single focal Cashflow & Balances card, consolidated `Paste SMS`, `Import Backup File`, and `Add Manual Entry` into a clean `+` menu, and moved the **AI Financial Analyst** into a modal bottom sheet opened from the top-bar sparkle button.
- **Ethiopian Bank SMS Parser Perfection:** Strips Telebirr/bank footer sign-offs before classification (eliminating `"using telebirr ethio telecom"` counterparty names and false `"Airtime & Data"` labels on P2P transfers), rejects OTP/failed/promo/balance-only alerts, and adds 1-tap Type/Category re-classification plus raw SMS re-parsing.
- **Legible Finance Charts & 90-Day Auto-Scan:** Fixed X-axis vertical date wrapping and clipped legends on the trend `LineChart`, backed by off-main-thread 90-day inbox scanning and chunked Room deduplication.
- **Zero-Permission SMS Import Alternatives & AI Q&A:** Share alerts directly from Messages (`Share -> Voice Task`), tap the clipboard bank SMS banner, or import `.csv`, `.txt`, and `.xml` backups without granting `READ_SMS`.

## Installing Sideloaded APKs (`Google Play Protect` Notice)

Because this APK retains `READ_SMS` for automatic inbox scanning, Android's **Google Play Protect Enhanced Fraud Protection** blocks installing the APK directly from a phone's **Downloads** folder ("App blocked to protect your device"). To install it on a test device:

- **Option 1 (Recommended — USB ADB):**
  ```bash
  adb install -r app-debug.apk
  ```
- **Option 2 (Device Sideload):** Temporarily open **Google Play Store -> Profile icon -> Play Protect -> Settings** and turn off **Scan apps with Play Protect** while installing `app-debug.apk`, then turn scanning back on immediately afterward.


