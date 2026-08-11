# Retro Minimal Launcher V1.5

A lightweight Android HOME launcher inspired by classic feature phones. The Phone/Dialer screen is the actual home screen.

## V1.5 changes

- Favorites area now supports up to 8 starred contacts in a compact 2-column grid.
- `More Contacts` sits directly below Favorites and opens the full contacts list.
- Menu now includes T9 app search. Tap the search box and type number sequences such as `6277` for Maps. Word-prefix matching also works for app names such as Music inside `Amazon Music`.
- Added a true `Monochrome` theme: black background, off-white foreground, minimal gray selection accent.
- Added double-tap-to-lock on the Phone home screen.
  - Open `Menu -> Options -> Enable Double-Tap Lock` once and approve Android's Device Administrator prompt.
  - After that, double-tap empty space on the Phone home screen to lock immediately.
  - The launcher only declares the `force-lock` device-admin policy.
- Added a real `CALL` button on the dialer.
  - The first call requests Android's Phone permission.
  - After permission is granted, pressing `CALL` places the entered number directly instead of first opening the separate dialer app.
  - Android's normal in-call screen still appears after the call starts.
- Calls from Recents and Contact Details now use the same direct-call flow.
- The system Phone app is no longer part of the fresh-install default app selection, though it can still be enabled manually in Launcher Settings.

## Existing features

- Real Android HOME launcher
- Dialer as home screen
- Contacts and starred favorites
- T9 contact search
- Recent calls
- Direct message shortcut from contact details
- Selectable minimal app menu
- D-pad navigation
- Classic LCD, Green LCD, Amber, Night and Monochrome themes
- Haptics optional and off by default
- No notification listener
- No network permission
- No location permission
- No background service or WorkManager polling

## Permissions

- `READ_CONTACTS` — only requested when Contacts are opened.
- `READ_CALL_LOG` — only requested when Recents are opened.
- `CALL_PHONE` — only requested when you first press CALL.
- Device Administrator — optional and used only for double-tap screen locking.

## Build

The existing GitHub Actions `android.yml` does not need to change.

```bash
gradle :app:assembleDebug
```

The debug APK will be produced under:

`app/build/outputs/apk/debug/app-debug.apk`

## Notes

Device Administrator is an Android system capability. If your device requires it, you may need to disable Retro Minimal Launcher as a device administrator before uninstalling it.
