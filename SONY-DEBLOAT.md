# SonyDebloat — Sony BRAVIA VU1 (Google TV) debloat + Apple-Glass launcher

This is the working fork for **one TV**: the Sony BRAVIA VU1 at `192.168.0.51:5555`
(Android 11 / SDK 30, build `RTT7.210105.001`, Google TV platform `BRAVIA_VU1_UC`,
2 GB RAM, no root, bootloader locked).

Two halves live here:

| Where | What |
|---|---|
| `launcher/` | The launcher app (`com.gathin.tvhome`) — the Apple-Glass home screen: clock + dock on page 1, two poster shelves on page 2. |
| `sony-debloat/` | This TV's debloat: package list, the apply/restore scripts, the pre-debloat snapshot, and known-good launcher APKs. |
| `archive/debloat-project-skeleton/` | The first launcher attempt (Kotlin, `com.example.mylauncher`). Superseded by `launcher/`; kept only for reference. |

`docs/`, `tools/`, `tests/`, `themes/`, `examples/` come from upstream and are unchanged.

## Build and install the launcher

    cd launcher
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
    export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
    ./gradlew assembleDebug --console=plain
    adb -s 192.168.0.51:5555 install -r app/build/outputs/apk/debug/app-debug.apk
    adb -s 192.168.0.51:5555 shell am force-stop com.gathin.tvhome
    adb -s 192.168.0.51:5555 shell am start -n com.gathin.tvhome/.MainActivity

`launcher/build-and-install.sh` does the whole thing (build + push + install + make it the
Home app) in one go; it also switches the stock Home app off and needs `TV_HOST` set.

## Debloat this TV

    cd sony-debloat
    ./debloat.sh --list     # what would be disabled
    ./debloat.sh --apply    # disable the CORE tier
    ./undo.sh --list        # what would be re-enabled
    ./undo.sh               # back to CORE
    ./undo.sh --all         # back to CORE + OPTIONAL

Every package is only `pm disable-user --user 0`ed — nothing is uninstalled, nothing is
rooted, so it is always reversible. The stock Google TV launcher comes back with
`tools/restore.sh` from the repo root.

`snapshot/` is the "before" half: `props.txt` (device properties), `disabled.txt`,
`meminfo.txt`, `df.txt`, `ps-top.txt` for this TV, plus a `pre-<timestamp>/` directory for
every step of the debloat. The device serial is redacted.

`sony-debloat/apk-backups/` holds known-good launcher builds; `RESTORE.md` there says which
one to install and how.

## Upstream

The launcher is a fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)
(MIT). `upstream` points there, `origin` points at this fork.

    git fetch upstream
    git log --oneline upstream/main          # upstream's current state
    git log --oneline upstream/main..main    # the work that is only here

This branch was built on upstream's `42a9419` ("README: counts and tables after the Round M
merges") and has **not** been rebased onto upstream since — upstream has moved on
(launcher 2.6, versionCode 7; this tree is versionCode 6). Pulling upstream in is a
rebase job, not a merge, and the shelves work touches the same files upstream changed.

## The Netflix shelves

Page 2 shows two shelves; each poster opens the title in the Netflix app.

- **Netflix Movies** / **Netflix Series** — the Malaysia Top 10 read from the public Tudum
  pages (`Shelves.netflixTudum()`), which carry Netflix's own `videoId` per rank.
  Pressing OK fires `VIEW https://www.netflix.com/title/<id>` at `com.netflix.ninja`,
  the same link Netflix's own app registers.
- **TMDB Movies** / **TMDB Series** — TMDB weekly trending. These carry no Netflix id, so
  pressing OK copies the title to the clipboard instead of pretending to open Netflix.

Hold OK on any poster for the **Customize** menu, to swap a shelf for another source.
The choice is saved in `files/shelf_slots.json` on the TV and survives restarts.
