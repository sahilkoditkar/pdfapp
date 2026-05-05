# PDF Scanner

A simple, offline Android app that scans documents from the camera and assembles
them into PDFs. No ads, no accounts, no network — the app does not request the
`INTERNET` permission, so captures never leave the device.

## Stack

- **UI**: Kotlin + Jetpack Compose + Material 3
- **Capture**: CameraX
- **Edge detection / perspective**: OpenCV (Maven AAR — fully offline, no Play Services)
- **PDF assembly**: `android.graphics.pdf.PdfDocument`
- **Min SDK**: 24 (Android 7.0) · **Target SDK**: 35

## Project layout

```
app/                                  Android app module
  src/main/java/dev/pdfscanner/
    MainActivity.kt                   Entry point; loads OpenCV
    ui/PdfScannerApp.kt               Nav graph
    ui/CameraScreen.kt                CameraX capture
    ui/EdgeEditScreen.kt              Drag-the-corners editor
    ui/DocumentListScreen.kt          PDF library
    scan/EdgeDetector.kt              OpenCV contour-based quad detection
    scan/PerspectiveTransform.kt      Warp + adaptive threshold "scan look"
    pdf/PdfBuilder.kt                 Multi-page PDF generation
    data/DocumentRepository.kt        Lists saved PDFs
.github/workflows/
  build.yml                           Debug APK on every push/PR (artifact)
  release.yml                         Signed release APK on tags (GitHub Release)
```

## Building locally

```bash
gradle wrapper                        # one-time: generates gradle-wrapper.jar
./gradlew assembleDebug               # debug APK
./gradlew assembleRelease             # release APK (debug-signed unless keystore provided)
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Cutting a release on GitHub

1. Generate a keystore once:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias pdfscanner \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w 0 release.keystore > release.keystore.b64
   ```
2. In repo Settings → Secrets and variables → Actions, add:
   - `SIGNING_KEY` — contents of `release.keystore.b64`
   - `SIGNING_KEY_ALIAS` — e.g. `pdfscanner`
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

- No `INTERNET` permission — the app cannot make network calls.
- Captures and PDFs are written to the app's private internal storage
  (`filesDir`); other apps cannot read them.
- `allowBackup="false"` and explicit data extraction rules exclude scans from
  Android cloud backup and device-to-device transfer.
