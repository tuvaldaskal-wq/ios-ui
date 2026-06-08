# iLauncher — an iOS-style Android home screen

A native Android **launcher** (home app) that recreates the iOS home screen
one-to-one: an iOS status bar, a paged grid of rounded "squircle" app icons
with white labels, page dots, a Spotlight-style search pill, a translucent
frosted dock, and a home indicator pill — all on an iOS-style gradient
wallpaper.

The prebuilt APK lives at [`dist/ios-launcher.apk`](dist/ios-launcher.apk).

## Install

```bash
adb install -r dist/ios-launcher.apk
```

Then press Home and choose **iLauncher** (set as default to make it your home
screen). It will display all installed apps in the iOS grid; tap any icon to
launch it, swipe left/right to page, and the first four apps appear in the dock.

- `minSdkVersion` 21 (Android 5.0), `targetSdkVersion` 23 — runs on essentially
  any modern device.

## What it recreates

| iOS element        | Implementation |
|--------------------|----------------|
| Status bar         | `StatusBarView` — vector-drawn clock, cellular bars, Wi-Fi, battery glyph |
| App icons          | `IconUtils` masks every app icon into the iOS continuous-corner squircle |
| Paged home screen  | `PagedScrollView` snaps to full-width pages like iOS |
| Page dots          | Live active-dot indicator that follows the current page |
| Search pill        | Spotlight-style frosted pill above the dock |
| Dock               | Translucent rounded dock holding the first four apps (no labels) |
| Home indicator     | Rounded white pill at the bottom |
| Wallpaper          | iOS-style blue→purple→pink gradient |

## Building from source

The project builds **without the Google Android SDK or any Google network
access** — it uses only the Debian-packaged Android tools, so it works in
locked-down/offline CI.

```bash
# One-time tool install (Debian/Ubuntu):
sudo apt-get install -y android-sdk-build-tools android-sdk-platform-23 \
                        aapt apksigner dalvik-exchange

./build.sh        # -> dist/ios-launcher.apk
```

The build pipeline (`build.sh`):

1. `aapt2 compile` + `aapt2 link` — resources & manifest → base APK + `R.java`
2. `javac` (Java 8 bytecode, inline string concat) — compile against `android.jar`
3. `dalvik-exchange` (`dx`) — `.class` → `classes.dex`
4. `zip` — add `classes.dex` to the APK
5. `zipalign` + `apksigner` — align and debug-sign

## Project layout

```
app/
  AndroidManifest.xml        # HOME/LAUNCHER intent filter -> acts as a launcher
  res/                       # gradient wallpaper, dock, dots, search pill, icon
  src/com/ioslauncher/
    LauncherActivity.java    # builds the whole home screen
    StatusBarView.java       # iOS status bar (vector drawn)
    PagedScrollView.java     # iOS-style page snapping
    IconUtils.java           # squircle icon masking
    AppInfo.java             # app model
build.sh                     # offline APK build
dist/ios-launcher.apk        # prebuilt, debug-signed APK
```
