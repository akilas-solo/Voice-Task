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

Because Android build output folders are excluded from Git commits, pushing this repository triggers the **Android CI Build & Test** workflow in GitHub Actions:

1. Push your changes to GitHub.
2. Open your repository's **Actions** tab on GitHub.
3. Open the latest **Android CI Build & Test** run.
4. Download **`app-debug-apk`** from the **Artifacts** section to install the latest build.

## Latest Build Highlights (`v1.1` / `versionCode 2`)

- **Finance Module & Bank SMS Parsing:** Reads Telebirr, CBE, Bank of Abyssinia, Awash, and other bank SMS alerts with runtime `READ_SMS` permission handling and Room deduplication.
- **Interactive Finance Charts:** Includes `LineChart` trend visualization and interactive `PieChart` breakdown in the Finance Overview screen categorized by **Bank** or **Spending Type**.
- **AI Financial Q&A:** Answers questions filtered by specific time-of-day, day, or month.
- **Task Sorting & Reminder Picker:** Chronological, priority, and alphabetical sorting plus calendar date/time presets and botanical alarm chimes.

