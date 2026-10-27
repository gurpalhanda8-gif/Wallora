# LockStyle v15 Pro — Enhanced Android source

LockStyle is an Android live-wallpaper and clock-customization project. This archive contains **source code**, not a prebuilt APK/AAB.

## v15 changes

- Bumped app version to `1.5.0` / version code `15`.
- Serialized renderer access to protect shared drawing state and bitmap cache from concurrent preview/wallpaper rendering.
- Clamped persisted clock-color indices in widget rendering so stale/out-of-range preferences cannot crash the renderer.
- Disabled cleartext network traffic at the application level; the app does not require network access for its core local features.
- Added a GitHub Actions workflow to build a debug APK and upload it as an artifact.
- Added a build/device test checklist and `.gitignore` for Android Studio/Gradle outputs.

## Build locally

1. Install Android Studio, JDK 17, Android SDK Platform 34, and Build Tools.
2. Open this folder (the one containing `settings.gradle`).
3. Sync Gradle and allow Android Gradle Plugin/dependencies to download.
4. Run `gradle --no-daemon assembleDebug` from this folder, or use **Build → Build Bundle(s) / APK(s) → Build APK(s)** in Android Studio.
5. Debug APK output is normally `app/build/outputs/apk/debug/app-debug.apk`.

The included GitHub Actions workflow can also build the debug APK when the project is pushed to a GitHub repository and the workflow is run. The resulting APK appears under the workflow run's **Artifacts** section.

## Compatibility and limitations

- Application ID: `com.lockstyle.app`
- Minimum SDK: 26 (Android 8.0)
- Compile SDK: 34
- Java/Kotlin target: 17
- Depth effect uses ML Kit selfie segmentation, designed for prominent people; it may not separate objects, pets, landscapes, or groups reliably.
- The optional overlay lock-screen simulation does **not** replace Android's secure system lock screen. Keep PIN/password/biometrics enabled.
- Notification access, activity recognition, overlay access, and battery optimization settings remain user-controlled.
- This environment does not have the Android SDK/Gradle installed, so the source has not been compiled or tested on a physical device here. Treat the first generated APK as a test build.

See `BUILD_CHECKLIST.md` for device testing steps.
