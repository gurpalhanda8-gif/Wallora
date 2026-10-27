# LockStyle v15.1 — Static Bug-Fix Report

## Changes made

1. **Renderer bitmap cache cleanup**: cached bitmaps are now recycled when the cache is evicted, rather than only dropping map references. Cache cleanup remains inside the renderer's synchronized draw path.
2. **Depth-image bitmap lifecycle**: the source bitmap used for ML Kit segmentation is recycled after success/failure or when background work cannot be queued. Segmenter cleanup is included in the async processing paths.
3. **Activity lifecycle guard**: depth-processing UI callbacks now avoid touching UI after the activity is finishing/destroyed.
4. **Background lock-screen launch resilience**: launch attempts from the screen-off receiver are guarded against platform/runtime restrictions so they do not crash the foreground service.

## Validation status

- ZIP archive integrity: checked after packaging.
- XML parsing: not re-run for this patch; no XML files were changed.
- Kotlin/Android compilation: **not run** because Android SDK and Gradle are unavailable in the current environment.
- Device/runtime tests: **not run**.

## Known limitation

Android versions may block launching an Activity directly from a background receiver. The exception guard prevents service crashes but cannot guarantee the custom lock screen opens on every device. A real device test and possibly a user-visible notification/full-screen-intent design are needed for reliable behavior. This app does not replace Android's secure PIN/password/biometric keyguard.
