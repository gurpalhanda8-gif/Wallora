# LockStyle v15 Pro — Build Attempt Report

Date: 2026-10-27

## Results
- ZIP extraction: passed
- Android XML parsing: passed (8 XML files)
- Android manifest class references: all declared classes found in source
- Gradle build: BLOCKED before compilation because `gradle` is not installed in the execution environment (`gradle: command not found`).
- Android SDK: no SDK installation or `ANDROID_HOME` / `ANDROID_SDK_ROOT` was found.
- APK: not generated. No claim of successful compilation or device testing is made.

## Build on a machine with Android Studio
1. Install Android Studio and Android SDK Platform 34.
2. Use JDK 17 and Gradle 8.2.
3. Open this project folder (the one containing `settings.gradle`).
4. Run `gradle --no-daemon assembleDebug` in the project terminal, or use Android Studio's Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Debug APK output should be `app/build/outputs/apk/debug/app-debug.apk` if the build succeeds.

## CI option
The included `.github/workflows/android-ci.yml` builds a debug APK on GitHub Actions when this project is pushed to a GitHub repository or the workflow is manually run. The APK is uploaded as a workflow artifact if the build succeeds.

## Device validation still required
Install and test on the target Android device. XML validation is not a substitute for Kotlin compilation, APK installation, or runtime testing.
