# Retro Minimal Launcher V1.4

A battery-conscious Android HOME launcher inspired by classic feature phones.

## V1.4 changes

- The Phone/Dialer screen is now the launcher home screen.
- Pressing Home returns to the dialer when Android delivers a HOME intent to the launcher.
- Menu is accessible directly from the dialer.
- Favorites, Contacts and Recents remain accessible directly from the dialer.
- Removed the separate clock/status home page.
- Removed all launcher notification-count UI.
- Removed the NotificationListenerService and notification-access option entirely.
- Android's normal notification shade remains available by swiping down from the top.
- Settings remains behind Menu -> Options -> Launcher Settings.

## Existing features

- Real Android HOME launcher
- Retro monochrome themes
- Text-only app menu with user-selected visible apps
- Favorites from starred Android contacts
- Full contacts list with T9 search
- Contact call/message actions
- Optional recent-call list (requires call-log permission)
- Dial pad that hands calls to Android's system dialer
- No network permission
- No analytics
- No background worker or polling service
- Haptics off by default

## Permissions

- `READ_CONTACTS` — requested only when Contacts/Favorites are used.
- `READ_CALL_LOG` — requested only when Recents is opened.

There is no notification-listener permission/service in V1.4.

## Build

The existing GitHub Actions `android.yml` from the previous version does not need to change.

Build command:

```bash
gradle :app:assembleDebug
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`
