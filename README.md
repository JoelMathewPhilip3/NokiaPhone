# Retro Minimal Launcher — V1.3

A battery-conscious Android launcher inspired by classic feature-phone interaction. It keeps the home screen simple while still providing quick access to selected Android apps, contacts, favorites, the dialer, recent calls, and minimal notification counts.

## V1.3 highlights

- Real Android HOME launcher
- Retro LCD-style themes with Night as the fresh-install default
- Minimal home screen with time, date, battery, message count, and missed-call count
- Notification contents are never shown by the launcher; only active message/missed-call notification counts are summarized
- Optional Android Notification Access, enabled from Menu → Options
- Built-in dialer UI that hands calls to Android's system dialer
- Starred Android contacts shown as Favorites
- Full lightweight Contacts screen with T9/name/number search
- Contact Call and Message actions
- Optional Recents screen showing up to 20 recent calls
- Recents refreshes from Android's call-log content observer rather than polling
- Selected-app menu with Options permanently placed at the bottom
- Launcher Settings moved behind Menu → Options → Launcher Settings
- D-pad navigation and optional haptics
- No network permission, analytics, location, WorkManager jobs, foreground services, or continuous animation
- Clock refreshes only once per minute
- Battery state uses Android battery broadcasts
- Installed apps refresh only on package changes
- Contacts refresh only on Contacts Provider changes

## Permissions / access

### Contacts
`READ_CONTACTS` is requested only when Contacts is opened. It is used for the in-launcher contact list, T9 search, and starred favorites.

### Recent calls
`READ_CALL_LOG` is requested only when Recents is opened. It is used to display a short local recent-call list. Some Android distributions/install methods can restrict call-log access, and Google Play applies additional policy requirements to apps requesting call-log permissions. The rest of the launcher works if this permission is unavailable.

### Notification counts
Notification Access is optional and must be enabled explicitly in Android settings from Menu → Options → Enable Notification Counts. The listener counts active notifications categorized by Android as messages or missed calls. It does not display notification titles, message bodies, senders, or other notification content.

## Build on GitHub

The existing `.github/workflows/android.yml` from V1/V1.2 does not need to change. Build with:

```bash
gradle :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Version

- Version code: 4
- Version name: 1.3.0
- Minimum Android: API 26 / Android 8.0
- Target/compile SDK: 36
