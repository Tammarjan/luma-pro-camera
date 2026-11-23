# Luma Pro Camera

A native Android camera project focused on professional capture and a consistent live-view/still-photo rendering pipeline.

## Current milestone

- Native Kotlin + Jetpack Compose Android app.
- CameraX live rear-camera preview.
- JPEG still-photo capture using the highest-quality CameraX capture mode.
- Rear/front camera switching where both lenses are available.
- Exposure compensation control with per-camera range detection and graceful fallback when unsupported.
- Manual ISO and shutter-speed controls on cameras advertising Android Camera2 MANUAL_SENSOR capability; otherwise the controls are clearly unavailable.
- 1× and 2× zoom presets, capped to the selected camera's reported zoom capability.
- Captures are saved to the device's Pictures/Luma Pro Camera gallery folder on Android versions that support relative media paths.
- Device-aware feature detection, manual controls, GPU color grading, DNG/RAW and HEIF are planned next milestones.

## Technology

- Kotlin + Jetpack Compose
- CameraX with Camera2 interop planned for manual sensor controls and hardware capability discovery
- GPU image processing planned for live Lightroom-style adjustments
- JPEG, DNG and HEIF output where supported by the device and Android APIs

## Build

The GitHub Actions workflow builds a debug APK on pushes to `main`. The APK is uploaded as the `luma-pro-camera-debug` workflow artifact when the build succeeds.

For a local build, use JDK 17, Android SDK 36 and Gradle 8.13, then run:

```bash
gradle --no-daemon :app:assembleDebug
```

## Compatibility

“Universal” means the app adapts to capabilities exposed by each Android device. Not every phone supports every lens, manual sensor control, RAW mode or HEIF encoding.
