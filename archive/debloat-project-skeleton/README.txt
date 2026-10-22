TV Launcher Project — Apple‑Glass style, no‑scroll grid, static Netflix top row
=============================================================================

This folder contains a minimal Android TV launcher prototype that can be
sideloaded onto a Sony Google TV (model BRAVIA VU1, Android 11) without
root or bootloader unlock.

Folder layout
-------------
README.txt          – this file
gradle-androidtv.gradle – tiny Gradle wrapper config
settings.gradle     – root project settings
build.gradle        – root build script (includes the app module)

app/
  build.gradle      – app‑level build configuration
  src/
    main/
      AndroidManifest.xml
      java/com/example/mylauncher/LauncherActivity.kt
      java/com/example/mylauncher/LauncherFragment.kt
      res/
        values/
          strings.xml
          styles.xml
        layout/
          launcher_fragment.xml
          grid_item.xml
        drawable/   – put 10 PNGs (≈100×140 px) here:
          stranger_things.png
          the_crown.png
          wednesday.png
          squid_game.png
          ozark.png
          bridgerton.png
          black_mirror.png
          money_heist.png
          the_witcher.png
          kerala_crime.png   (any 10th you like)

Quick start
-----------
1. Add your 10 PNG icons (100 × 140 px, centre‑aligned) to
   app/src/main/res/drawable/.

2. Build the debug APK from the project root:
   ./gradlew assembleDebug
   (The APK will appear at app/build/outputs/debug/app-debug.apk.)

3. Install it on the TV (the TV is at 192.168.0.51:5555):
   adb -s 192.168.0.51:5555 install -r app/build/outputs/debug/app-debug.apk

4. Make it the default home screen:
   adb -s 192.168.0.51:5555 shell cmd package set-home-activity \
       com.example.mylauncher/.LauncherActivity

5. Press the TV Home button – you should see a 5‑column grid with the
   “Top on Netflix” banner and the 10 hard‑coded titles. No scrolling
   is needed because the grid has exactly as many items as columns.

6. To go back to the original Google TV launcher:
   adb -s 192.168.0.51:5555 shell cmd package set-home-activity \
       com.google.android.tvlauncher/com.google.android.tvlauncher.LeanbackHomeActivity

Customisation ideas
-------------------
* Replace the hard‑coded `staticNetflixItems` list with a Retrofit fetch
  of Netflix’s public API (requires a partner key).
* Add a frosted‑glass PNG (`glass_blur.png`) and reference it in
  `grid_item.xml` via `android:foreground` for the “Apple Glass” look.
* Change `setColumnCount(5)` to 4 or 6 to match a different column width.
* Add icons for other apps (YouTube, Settings, etc.) – just increase the
  list size and ensure the total fits the column count so no scrolling
  occurs.

Notes
-----
* No root, no bootloader unlock – everything runs through normal ADB and
  the Android TV permission sandbox.
* The launcher package name is `com.example.mylauncher`; change it in
  `AndroidManifest.xml` and the `set-home-activity` command if you want
  a different namespace.
* MinSdk = 21, TargetSdk = 33 – works on Android TV 4.0+ and on the
  Google TV Android 11 build on the test device.

Enjoy your custom home screen! If you hit any build errors, just drop me
a line and I’ll help debug the Gradle/AAPT issues.