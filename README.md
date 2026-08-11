# Retro Minimal Launcher V1.6.1.1

A battery-conscious Android HOME launcher inspired by classic feature phones.

## V1.6.1.1 — Retro Snake

Snake is deliberately tucked away under `Menu -> Options -> Snake`; it is not shown on the main Home/Menu screen and does not run when you are outside the game.

### Snake gameplay

- Black-and-white retro board and controls.
- D-pad movement only; no swipe controls.
- Center button starts/pauses/resumes/restarts.
- Snake grows by one segment after eating food.
- Food is always placed on a free cell.
- Game over when the snake hits the outer wall, an obstacle, or its own tail.
- 180-degree direction reversals are blocked.
- Score and persistent local high score.
- Starts slowly and speeds up gradually, with a capped maximum speed.
- 3-2-1 countdown before a new game and after resume.
- Pauses automatically when the launcher loses window focus, the activity pauses, the notification shade takes focus, or another app/call interrupts play.
- Game state is discarded when you leave Snake; high score remains saved.
- Optional short food beep, OFF by default.
- Snake forces portrait orientation only while the game is open.
- No network, ads, online leaderboard, achievements, skins, power-ups, or background service.

### Level progression

- Level 1 / score 0–9: open board.
- Level 2 / score 10–19: one short wall appears.
- Level 3 / score 20–29: the obstacle layout changes to a longer wall.
- Level 4 / score 30+: two short wall segments are used.
- A short `LEVEL` overlay pauses play when the board changes.
- Wall candidates are chosen only when they do not overlap the snake/food and are not immediately beside the snake's head.

## Launcher features

- Real Android HOME launcher.
- Menu-first Home screen.
- Dial pad opens only from the `Dial` softkey.
- Dialer with direct CALL button.
- Up to 8 starred Favorites plus More Contacts.
- Full contacts list with T9 search.
- Optional Recents.
- T9 app search from Menu.
- Classic, Green, Amber, Night, and true Monochrome themes.
- User-selected visible apps.
- Direct call support after one-time Phone permission.
- Launcher-level optional double-tap screen lock.
- No notification-listener service; use Android's normal pull-down notification shade.
- No network permission.
- No analytics.
- No background worker/polling service.
- Haptics off by default.

## Permissions

- `READ_CONTACTS` — requested only when Contacts/Favorites are used.
- `READ_CALL_LOG` — requested only when Recents is opened.
- `CALL_PHONE` — requested only when direct calling is first used.
- Device Administrator — optional, used only for double-tap-to-lock.
- Snake requires no additional Android permission.

## Build

The existing GitHub Actions `android.yml` does not need to change.

```bash
gradle :app:assembleDebug
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`
