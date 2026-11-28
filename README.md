# Luma Pro Camera — Native Android

Native Kotlin camera app built with Jetpack Compose, CameraX and Camera2 interop. The first delivery milestone focuses on a stable camera preview, high-quality JPEG capture and saving through Android MediaStore.

## Target device and SDK

- Tested target: Redmi 13C 4G (MediaTek Helio G85), Android 13/14 MIUI
- Minimum Android: API 29 (Android 10)
- Target Android: API 34
- Compile SDK: API 36
- JDK 17 / Gradle 8.13

Camera hardware capabilities vary by device and firmware. RAW, manual sensor settings, 50MP streams, optical stabilization and OEM extensions are enabled only when the active camera advertises support.

## Current implementation

- CameraX rear/front preview and camera switching.
- JPEG capture using CameraX quality capture mode.
- MediaStore photo saving to `DCIM/Camera` with Luma filename prefix.
- Rear/front camera capability discovery and zoom/exposure controls.
- Camera2 interop for supported high-quality still capture requests and manual sensor capabilities.
- Video capture support with device-selected quality fallback.
- Dark Compose camera UI, gallery and camera diagnostics.
- Background image analysis for histogram, clipping and focus-peaking overlays.
- Live image adjustments and RAW development features present in the existing app source; their visual and output quality must be verified on the target phone.

## Build locally

1. Install Android Studio or Android command-line tools, JDK 17 and Android SDK 36.
2. Open the repository in Android Studio and allow Gradle sync.
3. Build with:

```bash
gradle --no-daemon :app:assembleDebug
```

4. Install `app/build/outputs/apk/debug/app-debug.apk` on the phone.

## GitHub Actions APK

Every push to any branch and every pull request targeting `main` runs the Android debug build. When successful, open the repository's **Actions** tab, select the latest **Luma Pro Camera Android CI** run, and download the `luma-pro-camera-debug-<run>` artifact. The APK is a debug-signed installable test build; no release keystore is required.

## Phone test checklist — milestone 1

- Grant camera permission and confirm preview starts without a black screen.
- Capture 10 photos in succession; confirm the shutter remains responsive.
- Open Gallery and verify photos appear in `DCIM/Camera`.
- Switch rear/front cameras and repeat a capture.
- Lock/unlock the phone and return to the app; verify the preview recovers.
- Check that denying microphone permission does not prevent photo capture.

## Planned milestones

1. Preview, capture, MediaStore save and device compatibility baseline.
2. Live adjustments, applied-to-JPEG processing and preset persistence.
3. Bounded-memory burst capture, alignment/merge and edge-preserving denoise.
4. Pro controls, manual focus feedback, white balance and exposure lock.
5. Portrait refinement, video stabilization checks, memory/performance tuning and device validation.
