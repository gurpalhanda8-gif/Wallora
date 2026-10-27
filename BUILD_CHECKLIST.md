# LockStyle v15 — build and device validation

## Build

- Android Studio: open the folder containing `settings.gradle`.
- Use JDK 17, Android SDK Platform 34, and Gradle 8.2 or a compatible wrapper.
- Run `gradle --no-daemon assembleDebug` from this project directory.
- Expected output: `app/build/outputs/apk/debug/app-debug.apk`.
- GitHub Actions workflow `.github/workflows/android-ci.yml` builds and uploads a debug APK when run on GitHub.

## Required manual tests

1. Install on Android 8+ and launch the app.
2. Add landscape, portrait, very large, and rotated photos.
3. Confirm the preview and live wallpaper update after changing image, clock, color, font, zoom, blur, and dim settings.
4. Test subject cutout with a single person and a photo without a person; ensure the app degrades gracefully when segmentation fails.
5. Test wallpaper preview/application and reboot behavior.
6. Test step permission denial/grant, notification-listener denial/grant, and battery saver.
7. Test lock-screen overlay only with the phone's secure system lock enabled; verify swipe invokes the system keyguard and does not bypass authentication.
8. Test on the target Realme device and check battery use, crashes, orientation, and manufacturer background restrictions.

## Known limitations

- This source archive does not contain a compiled APK or Gradle wrapper JAR.
- The depth mask uses ML Kit selfie segmentation, intended for prominent people rather than arbitrary objects, pets, or complex group photos.
- The overlay lock screen is a visual companion only, not a replacement for Android's secure keyguard.
- A successful CI build does not replace real-device testing or release signing.
