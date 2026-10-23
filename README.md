# Reader

A personal EPUB / PDF / TXT / Markdown reader for Android, with five switchable looks. See `PLAN.md` for the design and architecture.

## Install on a device

1. On the phone or tablet, turn on Developer options, then USB debugging:
   - Pixel: Settings → About phone → tap Build number 7 times.
   - Samsung: Settings → About tablet → Software information → tap Build number 7 times.
2. Connect it by USB and accept the "Allow USB debugging" prompt.
3. Run:
   ```
   ./gw :app:assembleRelease && ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
   ```
   `./gw` is gradlew with Android Studio's bundled Java.

On first launch, choose the folder that holds your books. Reading positions go into `<folder>/.reader/`, one file per device. Sync the folder between phone and tablet (Syncthing, for example) and positions follow.

## Develop

- `./gw :app:installDebug` installs a debug build on the connected device or emulator.
- `LOOKS=readout ./gw :app:recordRoborazziDebug --tests app.reader.LookShots` renders screenshots of a look (or of all looks if `LOOKS` is unset) to `app/screens/`.
