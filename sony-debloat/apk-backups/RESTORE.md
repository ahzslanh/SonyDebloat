# Launcher restore points

| File | What it is |
|---|---|
| `launcher-working-dock-and-longpress.apk` | Known-good: dock (Netflix/VLC/YouTube), grey band removed, long-press tile menu (blur + icons: App info / Move left / Move right / Quit app), reorder button. No shelves. |
| `launcher-with-shelves-broken.apk` | Shelves present but rails collapse (1 poster, no dock). Kept only for reference. |

## Restore a known-good launcher

    adb -s 192.168.0.51:5555 install -r launcher-working-dock-and-longpress.apk
    adb -s 192.168.0.51:5555 shell am force-stop com.gathin.tvhome
    adb -s 192.168.0.51:5555 shell am start -n com.gathin.tvhome/.MainActivity

## Source

    cd /Volumes/Xbyte/Projects/SonyDebloat
    git log --oneline            # cbade8c = working state
    git tag                      # working-state-before-shelf-fix, upstream-clean

## Theme (dock apps, hidden apps, columns)

    cd /Volumes/Xbyte/Projects/SonyDebloat && TV_HOST=192.168.0.51 tools/theme.sh themes/vu1
    TV_HOST=192.168.0.51 tools/theme.sh --reset     # back to default theme
    tools/restore.sh                                # stock Google TV launcher back

## Back to stock (the debloat is reversible)

Nothing here was uninstalled or rooted: every package was only `pm disable-user --user 0`ed.

    cd /Volumes/Xbyte/Projects/SonyDebloat/sony-debloat
    ./undo.sh --list        # what would be re-enabled
    ./undo.sh               # re-enable the CORE tier
    ./undo.sh --all         # re-enable CORE + OPTIONAL
    adb -s 192.168.0.51:5555 reboot

The stock Google TV launcher comes back with:

    cd /Volumes/Xbyte/Projects/SonyDebloat
    tools/restore.sh        # stock HOME app again

`snapshot/` holds the pre-debloat state of this exact TV (`pre-*/disabled.txt` lists the
packages that were disabled at that moment, plus meminfo/df/ps-top), so the "before"
half of any comparison can still be read.

## Current best (shelves working)

    adb -s 192.168.0.51:5555 install -r launcher-with-shelves-working.apk
    adb -s 192.168.0.51:5555 shell am force-stop com.gathin.tvhome
    adb -s 192.168.0.51:5555 shell am start -n com.gathin.tvhome/.MainActivity

Source tag: `shelves-working` (commit 03a39ba)

### Shelves
- "Top 10 Movies on Netflix"  — Netflix Tudum, Malaysia
- "Top 10 Series on Netflix"  — Netflix Tudum, Malaysia
- "Trending on TMDB"          — TMDB weekly
Posters from TMDB, cached in files/shelves.json + cache/posters, refreshed every 6 hours.

## Two pages (current)

Page 1: clock + dock (Netflix / VLC / YouTube).
Page 2: two shelves. Press Down on a dock tile to flip to the shelves; Up returns.
Hold OK on any poster to open the "Customize" menu and replace that shelf with another source:
Top 10 Movies on Netflix / Top 10 Series on Netflix / Trending Movies on TMDB / Trending Series on TMDB.

Choice is saved in files/shelf_slots.json and survives restarts.

Source tag: `shelves-pages`
