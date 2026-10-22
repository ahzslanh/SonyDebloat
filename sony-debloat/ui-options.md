# UI customisation options — BRAVIA VU1 (Google TV, Android 11)

Two routes. The decision hinges on one thing: **does Netflix/Disney+ HD matter to you?**
Unlocking the bootloader (required for root) drops **Widevine L1 → L3**, so those services
drop to SD. Everything in Route A keeps L1 intact.

---

## Route A — rootless (no bootloader unlock, no Widevine loss)

### A1. Built-in "Apps only mode" (zero install, zero risk)
Home → **Settings** → **Accounts & Sign in** → your profile → **Apps only mode** → On.

- Hides the personalised recommendation rows and sponsored cards.
- Costs you: Search / Google Assistant, Watchlist "add", "mark as watched", library row.
- Toggle back off any time from the same menu.

### A2. Hide individual home rows
Home → **Settings** → **Device preferences** → **Home screen** → **Customise channels**
→ toggle the rows you don't want (the "Your services" row can also be trimmed per-service:
Settings → Your account → **Your services** → turn individual services off).

### A3. Replace the launcher entirely (the big visual win)
Sideload a third-party launcher and make it the HOME app. No root needed — this is a
normal `adb install` + one `set-home-activity` call.

| Launcher | Notes |
|---|---|
| **FLauncher** | free, open source, lightweight, Play Store + GitHub. The safe default. |
| **Projectivy** | best customisation (themes, wallpaper plugins, per-app recommendation control, no ad slots). Play Store + GitHub. |
| **AT4K** | minimalist, fast, themed, free with paid extras. |
| **HALauncher** | functional home replacement, focuses on running sideloaded phone apps. |
| **Sideload Launcher** (Chainfire) | classic, exposes phone-only apps. |
| **Arc / LTvLauncher** | FLauncher forks, more visual flair. |

Flow (rootless):

```bash
adb -s 192.168.0.51:5555 install Projectivy.apk
# find the HOME activity it declares:
adb -s 192.168.0.51:5555 shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.HOME Projectivy
# point the system at it:
adb -s 192.168.0.51:5555 shell cmd package set-home-activity <pkg>/<activity>
# or set it from the UI: Settings -> Apps -> Default Apps -> Home app
```

Rollback: `adb shell cmd package set-home-activity com.google.android.tvlauncher/com.google.android.tvlauncher.HomeActivity`
(verify the real activity with `resolve-activity` first) or just re-select "Google TV Home"
in Settings → Default Apps → Home.

### A4. Ambient mode / screensaver
Disable `com.google.android.backdrop` (in the OPTIONAL tier) to stop the Google TV
ambient wallpaper engine. Also `com.android.dreams.basic`,
`com.android.wallpaper.livepicker`, `com.android.wallpaperbackup` for the Sony
screensaver path.

### A5. Boot logo, device name, wallpaper, row layout beyond the above
Not possible rootless. The stock Google TV home is baked into `com.google.android.tvlauncher`
and can only be restyled with root or by replacing the launcher (A3).

---

## Route B — rooted (full control, real cost)

Unlocking the bootloader → Magisk gives you:

- **Tablet-style home**: Krypton Launcher, Phantom, Buttons Launcher — grid, widgets,
  app shelves you build yourself.
- **Desktop-style**: LauF / LauFCT — taskbar, windows, a real "computer" feel.
- **Edit the stock launcher directly**: rewrite `launcher.db` to build custom app shelves,
  change icons, hide the Google branding.
- **Rebrand**: `build.prop` edits (model name, boot splash).
- **Ambient mode / screensaver**: replace the whole thing with your own.
- **Deeper debloat**: remove system apps instead of just disabling them.

### The cost, precisely
- `ro.boot.flash.locked=1` + `verifiedbootstate=green` **today**. Unlocking →
  **Widevine L1 → L3** → Netflix and Disney+ lose HD and 5.1.
- Warranty effectively voided.
- Real (if small) brick risk on a device with no official custom ROM support.
- There is **no rootless root** for this platform — every route needs the unlock.

**Verdict: Route B only if you do not care about HD streaming on this panel.**

---

## Suggested combination (what I'd do)

1. **A1 Apps only mode** — kills the sponsored/recommendation rows instantly, free.
2. **A2** — trim any remaining rows and services you don't use.
3. **A3 Projectivy** — the actual "change the UI" you asked for: no ad slots, real
   wallpaper, custom rows, and it doubles as a faster home on a 2 GB box.
   (FLauncher if you want the absolute lightest option.)
4. **A4** — kill ambient mode if the Google TV wallpaper annoys you.
5. **Stage 1 debloat** (`./debloat.sh --apply`) — ~350–450 MB RAM back, which is what
   actually makes the new launcher feel fast.

## Open questions when we talk
- Do you use Netflix/Disney+ in HD? (Decides Route A vs B.)
- Do you use the TV's built-in tuner (DVB-T/C) or an external box?
- Casting from phone (`katniss`) — keep or drop?
- Do you want a launcher that looks like a tablet, or like a clean grid of apps?
