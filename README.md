# PDF Lens

A simple Android app that scans documents and assembles them into PDFs. No
ads, no accounts. Scanning is delegated to Google Play Services' ML Kit
Document Scanner, which runs entirely on-device — captures never leave the
device.

The app itself does not request the `INTERNET` permission and makes no network
calls of its own. Play Services downloads the scanner module on first use
(handled in its own process), so the **device** needs a connection once to
fetch the model; after that, scanning works fully offline.

## Stack

- **UI**: Kotlin + Jetpack Compose + Material 3
- **Scanning**: [ML Kit Document Scanner](https://developers.google.com/ml-kit/vision/doc-scanner)
  via Google Play Services — handles camera preview, edge detection,
  perspective correction, filters, and PDF assembly on-device
- **Min SDK**: 24 (Android 7.0) · **Target SDK**: 35

## Project layout

```
app/                                  Android app module
  src/main/java/dev/pdflens/
    MainActivity.kt                   Entry point
    ui/PdfLensApp.kt                  Hosts the scanner launcher + library
    ui/DocumentListScreen.kt          PDF library
    data/DocumentRepository.kt        Lists saved PDFs
.github/workflows/
  build.yml                           Debug APK on every push/PR (artifact)
  release.yml                         Signed release APK on tags (GitHub Release)
```

The scanning UI, edge detection, perspective correction, and PDF generation all
live inside Google Play Services — the app simply launches the scanner and
copies the resulting PDF into its private storage.

## Building locally

```bash
gradle wrapper                        # one-time: generates gradle-wrapper.jar
./gradlew assembleDebug               # debug APK
./gradlew assembleRelease             # release APK (debug-signed unless keystore provided)
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

The device or emulator running the app needs Google Play Services installed for
the scanner to launch.

## Cutting a release on GitHub

1. Generate a keystore once:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias pdflens \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w 0 release.keystore > release.keystore.b64
   ```
2. In repo Settings → Secrets and variables → Actions, add:
   - `SIGNING_KEY` — contents of `release.keystore.b64`
   - `SIGNING_KEY_ALIAS` — e.g. `pdflens`
   - `SIGNING_KEY_PASSWORD`
   - `SIGNING_STORE_PASSWORD`
3. Tag and push:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
4. The `Release` workflow builds a signed APK and attaches it to a new GitHub
   Release. End users download from the repo's Releases page — no Play Store,
   no login required.

If you don't add the secrets, the workflow still builds an unsigned/debug-signed
APK as an artifact (downloadable by repo collaborators only).

## Privacy

- No `INTERNET` permission in this app — it cannot make network calls itself.
  Google Play Services downloads the scanner module on first use in its own
  process; once cached, no further network access is required.
- No `CAMERA` permission in this app's manifest. The ML Kit scanner activity
  declares its own camera permission and runs in the Play Services process;
  scan images are processed on-device and never uploaded.
- PDFs are written to the app's private internal storage (`filesDir`); other
  apps cannot read them.
- `allowBackup="false"` and explicit data extraction rules exclude scans from
  Android cloud backup and device-to-device transfer.
