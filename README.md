# SkyCast

A native Android weather app (Kotlin + Jetpack Compose, Material 3).

## Features
- **Material You** – wallpaper-derived dynamic color on Android 12+ (light/dark), blue fallback palette on older versions
- **Live weather** from Open-Meteo (no API key): current conditions, 24-hour and 7-day forecast, feels-like, humidity, wind, pressure, UV, air quality, sunrise/sunset
- **Ambient sounds** synthesised on-device (no audio files): rain, wind, thunder, birdsong, night crickets. *Auto* mode picks the soundscape from the live weather; or choose one manually. Volume slider; pauses when the app leaves the screen
- Animated weather icons and falling rain / snow in the hero card
- City search, "use my location", multiple saved places, °C/km/h or °F/mph, pull to refresh

## Get the APK

### Option A – GitHub Actions (no installs)
1. Create a new GitHub repository and push this folder to it.
2. Open the **Actions** tab, run **Build APK** (it also runs on every push).
3. Download the `SkyCast-apk` artifact, unzip, copy `app-release.apk` to your phone and open it
   (allow "Install unknown apps" when asked).

### Option B – Android Studio
Open this folder, wait for Gradle sync, then **Build ▸ Build APK(s)**.

### Option C – command line
Requires JDK 17 and the Android SDK (platform 35):
```
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it installs directly. To publish on Google Play, add your own signing config in `app/build.gradle.kts`.

Requires Android 8.0 (API 26) or newer.
