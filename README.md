# Luma Pro Camera

A native Android camera project focused on a consistent live-view and still-photo rendering pipeline, with professional controls and device-aware capabilities.

## Product goals

- Live preview with GPU-rendered looks.
- Keep rendered JPEG/HEIF captures visually consistent with the viewfinder.
- Preserve an unmodified DNG when the camera hardware and Android camera stack support RAW.
- Expose only controls that the current device actually supports.
- Professional manual controls, color tools, presets, and a built-in gallery.

## Technology

- Kotlin + Jetpack Compose
- CameraX with Camera2 interop for device-aware camera access
- GPU image processing (planned)
- JPEG, DNG and HEIF output where supported by the device and Android APIs

## Current status

The repository now contains the initial Android Gradle project and a first live camera-preview screen. Capture, manual controls, GPU color processing, RAW and HEIF export are planned milestones and are not implemented yet.

## Build

Open this repository in Android Studio with JDK 17 and Android SDK 36 installed, then build the `:app` module.

## Compatibility promise

“Universal” means the app adapts to the camera capabilities exposed by each Android device. It does not mean every phone supports every lens, manual sensor control, RAW mode, or HEIF encoding.
