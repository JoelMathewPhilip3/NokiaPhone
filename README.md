# Retro Minimal Launcher — V1

A distraction-reducing Android launcher inspired by the simplicity and navigation style of classic feature phones. It intentionally avoids Nokia logos, copyrighted UI assets, and brand-specific graphics.

## V1 features

- Real Android **HOME launcher** (can be selected as the default Home app)
- Minimal home screen with time, date, battery, faux signal indicator, and one-tap retro dialer
- Retro monochrome UI with four themes: Classic LCD, Green LCD, Amber, and Night
- User-selectable list of visible apps; everything else stays out of the main launcher menu
- Direction-pad style navigation with a center Select button
- Long-press center key shortcut to Android Home settings
- Built-in retro numeric keypad that hands the entered number to the system dialer; no phone permission required
- Optional key haptics
- GitHub Actions workflow that builds a debug APK
- No analytics, ads, accounts, network permissions, or tracking

## Recommended development setup

- Android Studio with JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- Jetpack Compose BOM 2026.06.01

## Run locally

1. Clone or unzip this repository.
2. Open the root folder in Android Studio.
3. Allow Gradle sync to finish.
4. Run the `app` configuration on an Android device or emulator (Android 8.0+).
5. Press the device Home button.
6. Choose **Retro Minimal Launcher** and select **Always** if you want it as the default launcher.

If Android does not prompt you, open **Settings → Apps → Default apps → Home app** and select Retro Minimal Launcher.

## Build on GitHub

Push the project to a GitHub repository. The included workflow at `.github/workflows/android.yml` runs automatically on pushes to `main` or `master`, on pull requests, and manually through **Actions → Android Build → Run workflow**.

The resulting APK is uploaded as the workflow artifact:

`retro-minimal-launcher-debug-apk`

You can also build from a machine with Gradle 8.13 installed:

```bash
gradle :app:assembleDebug
```

The APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```text
NokiaMinimalLauncher/
├── .github/workflows/android.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/retro/minimallauncher/MainActivity.kt
│       └── res/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── LICENSE
└── README.md
```

## V1 design decisions

This version launches the user's existing Phone, Messages, Camera, Maps, WhatsApp, Music, and other Android apps rather than rebuilding those applications. That keeps the launcher useful, small, and permission-light.

The app list is configurable rather than hard-coded. On first run, the launcher tries to preselect common essentials based on their displayed labels. Users can change the visible list at any time in Settings.

## Suggested V2 roadmap

- True T9 app search
- Notification count / intentionally simplified notification inbox
- Work / Weekend profiles with different allowed-app sets
- Optional app-opening delay for distracting apps
- Custom retro bitmap font bundled under an appropriate license
- Icon abstraction so modern app icons can be represented by monochrome glyphs
- Favorite contacts / speed dial
- Screen-time summary without gamification
- Better keyboard / D-pad accessibility support
- Instrumented UI tests and screenshot tests

## Notes

This project is an independent retro-style launcher. "Nokia" is not used in the app name, package name, artwork, or UI branding. If you publish it, keep the branding distinct and review any assets you add for licensing/trademark issues.
