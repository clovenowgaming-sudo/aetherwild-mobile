# Aetherwild: Lost Horizon

An original Android 3D survival/exploration prototype built with Java and OpenGL ES 2.0. It is designed to be lightweight enough to test on budget Android devices, including phones around Helio P35 / 3 GB RAM, though real performance depends on device thermals, GPU drivers, and screen resolution.

## Current gameplay
- Procedural island terrain, forest, rocks, an ancient beacon, and floating Ether Shards.
- Third-person movement, camera look, jump, sentinel combat, health, XP, and a collection quest.
- Day/night sky and directional lighting changes.
- Three saved graphics presets: **1 Low**, **2 Medium**, and **3 Extreme**. Presets adjust terrain density, vegetation count, and shader distance/detail behavior.
- Touch controls: left-side joystick, drag the right side to look, and action buttons on the right.
- Custom Aetherwild launcher icon and SVG logo source.

## Build APK on GitHub
Every pushed v* tag runs .github/workflows/android-release.yml, builds the APK, uploads it as a workflow artifact, and publishes it to GitHub Releases.

APK filename: Aetherwild-v1.0.3.apk

## Requirements
- Android 6.0+ (API 23), OpenGL ES 2.0-capable GPU.
- Build: JDK 17, Gradle 8.9, Android SDK platform 35/build-tools 35.0.0.
- For Helio P35 / 3 GB RAM, start with **Low**; Medium and Extreme increase geometry and visual workload.

## Graphics note
This is a procedural mobile 3D prototype, not a photorealistic PBR engine. It uses vertex-coloured geometry, directional lighting, animated water, fog, and a day/night tint; it does not yet include physically based materials, real-time shadow maps, detailed texture packs, weather simulation, online multiplayer, or a large authored campaign. Extreme is heavier and is not guaranteed to run smoothly at a particular FPS on every phone.

## Repository layout
- app/: Android activity, touch HUD, OpenGL ES renderer, launcher resources.
- .github/workflows/: automated build and APK release.
- app/src/main/assets/aetherwild-logo.svg: editable logo source.

## License
MIT. See LICENSE.
