# Sony BRAVIA VU1 (Google TV) — debloat & UI plan

Target: `192.168.0.51:5555` · BRAVIA VU1 · Android 11 (SDK 30) · build `RTT7.210105.001`
· Google TV (Sony platform `BRAVIA_VU1_UC`) · `ro.boot.flash.locked=1` · `verifiedbootstate=green`

---

## 1. Baseline (snapshot taken 2026-10-21, `snapshot/`)

| Metric | Value | Verdict |
|---|---|---|
| `/data` | 11G total, **10G free** | storage is **not** the problem |
| `/system` `/vendor` | 94% / 85% used | read-only, irrelevant |
| RAM | 2.0G total, 504M free, **362M swap in use** | RAM **is** the constraint |
| CPU | 4× Realtek rtd2870, ~23% busy, mostly idle | not the problem |
| Uptime | 30 days | |
| Packages | **110 total**, 5 already disabled | already a lean build |

### Already disabled by someone (before this work)
`com.android.camera2` · `com.amazon.amazonvideo.livingroom` · `com.google.android.youtube.tvmusic` ·
`com.google.android.videos` · `com.google.android.play.games`

### Every `/data` app is factory-stamped (`firstInstallTime=2009-01-01`)
except `org.videolan.vlc` (2025-05-09) → **the Play Store has not auto-installed junk**.

### Top RSS consumers (`snapshot/ps-top.txt`)

```
243988  com.google.android.tvlauncher     keep  (home screen)
184168  com.google.android.gms              keep  (DRM, cast, play services)
147604  com.android.vending                 keep* (Play Store)
145172  com.google.android.katniss:search   keep* (Cast / Assistant)
144076  com.google.android.gms.unstable     keep
141320  system_server                       keep
131708  com.google.android.katniss:interactor keep
124788  com.android.tv.settings             keep
122588  com.android.vending:background      keep*
119344  com.google.android.syncadapters.calendar   <-- TRIM  (calendar on a TV)
 91332  com.android.vending:instant_app_installer   keep*
 90776  com.google.android.youtube.tv        keep* (YouTube TV)
 89128  com.google.android.youtube.tv.recommendations  keep*
 83628  com.google.android.tungsten.setupwraith     <-- TRIM  (setup leftover)
 71916  com.android.bluetooth               keep  (BT remote)
 65644  com.sony.dtv.remotecontrolsettings  keep  (remote pairing)
 63184  com.realtek.servicemenu             keep  (hidden service menu)
 62816  com.google.android.tvrecommendations keep
 54624  com.google.android.partnersetup             <-- TRIM
```

---

## 2. Plan

### Stage 1 — memory trim (this repo's scripts)
`./debloat.sh --apply` → 21 CORE packages. Realistic reclaim **~350–450 MB RSS**,
i.e. roughly a quarter of the free RAM on a 2 GB box. Everything is
`pm disable-user --user 0` → **no root, no bootloader, fully reversible**.

### Stage 2 — UI (not started, see `ui-options.md`)
Two viable routes, with a real trade-off between them:

- **Rootless** — sideload a third-party launcher over ADB and make it the HOME app.
  Keeps Widevine L1 (Netflix/Disney+ HD) and the warranty intact.
- **Rooted** — unlock bootloader + Magisk, then install a full desktop-style launcher
  (Krypton / Phantom / LauF) and freely edit the Google TV launcher database.
  Costs you **Widevine L1 → L3** (Netflix drops to SD) and effectively the warranty.

### Stage 3 — hardening
Play Store auto-update + Play Protect scanning off; Google activity/fitness reporting off;
USB debugging off at the end; `adb` over network disabled.

---

## 3. Hard rules

1. **Do not unlock the bootloader.** Verified boot is green and the bootloader is locked.
   Unlocking drops Widevine L1 → L3, Netflix/Disney+ lose HD, and warranty is void.
   Nothing in Stage 1 or the rootless part of Stage 2 needs it.
2. **Never disable** `com.google.android.gms`, `com.android.vending`,
   `com.google.android.gsf`, `com.google.android.katniss` (Cast/Assistant),
   `com.google.android.apps.mediashell`, `com.google.android.tvlauncher`,
   `com.android.systemui`, `com.android.tv.settings`, `com.sony.dtv.smartui`,
   `com.sony.dtv.remotecontrolsettings`, `com.sony.dtv.ime.chww`,
   `com.android.bluetooth`, or anything DRM/Widevine-related.
3. **Snapshot before every change** — `debloat.sh` does this automatically into
   `snapshot/pre-<timestamp>/`.
4. Rollback is always available: `./undo.sh [--all]`.

---

## 4. Files

| File | Purpose |
|---|---|
| `packages.conf` | the package list, tagged `CORE` / `OPTIONAL` with notes |
| `debloat.sh` | apply (`--apply`, `--all`), dry-run by default, `--list` |
| `undo.sh` | re-enable everything, `--all` for the optional tier |
| `ui-options.md` | launcher / UI customisation options and their costs |
| `snapshot/` | baseline taken before any change |

```bash
./debloat.sh --list          # what would be touched
./debloat.sh                 # dry run
./debloat.sh --apply         # CORE only
./debloat.sh --apply --all   # CORE + OPTIONAL
./undo.sh --all              # roll everything back
```
