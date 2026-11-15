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

## Latest Build Highlights (`v1.2` / `versionCode 3`)

- **Finance Stability & 90-Day Background Auto-Scan:** Fixed main-thread stalls and chart normalization crashes when opening Finance after granting `READ_SMS`. Device inbox scans now run off the UI thread with single-flight deduplication covering the last 90 days, pre-compiled regex/date parsers, chunked Room inserts, and virtualized LazyColumn items.
- **Responsive Balanced Finance UI:** Rebuilt the Finance header with symmetric `Income` / `Outflow` metric tiles, an equal-weight `2x2` action grid (`Scan Inbox`, `Paste SMS`, `Import File`, `Add Entry`), and a collapsible `Filters` panel showing an active filter badge and summary.
- **Zero-Permission SMS Import Alternatives:** Share alerts directly from Messages (`Share -> Voice Task`), tap the one-tap clipboard bank SMS banner, or import `.csv`, `.txt`, and Android SMS Backup `.xml` files without granting `READ_SMS`.
- **Interactive Finance Charts:** Safe-bounded `LineChart` trend visualization and interactive `PieChart` breakdown in the Finance Overview screen categorized by **Bank** or **Spending Type**.
- **AI Financial Q&A & Task Reminders:** Time/day/month financial Q&A, chronological/priority/alphabetical task sorting, and calendar reminder chimes.

## Installing Sideloaded APKs (`Google Play Protect` Notice)

Because this APK retains `READ_SMS` for automatic inbox scanning, Android's **Google Play Protect Enhanced Fraud Protection** blocks installing the APK directly from a phone's **Downloads** folder ("App blocked to protect your device"). To install it on a test device:

- **Option 1 (Recommended — USB ADB):**
  ```bash
  adb install -r app-debug.apk
  ```
- **Option 2 (Device Sideload):** Temporarily open **Google Play Store -> Profile icon -> Play Protect -> Settings** and turn off **Scan apps with Play Protect** while installing `app-debug.apk`, then turn scanning back on immediately afterward.


