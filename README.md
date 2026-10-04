# SnapLoad

**Developer by Saad Hamdy**

> **Fast. Simple. Lightweight.**
> A modern Android application for downloading and playing authorized media from direct links and public platforms.

---

## 🌟 Overview

SnapLoad is a native Android application engineered for speed, privacy, and standalone reliability. The user pastes an authorized media link, and SnapLoad validates the URL, extracts authorized streams and qualities, downloads the file using a high-performance chunked streaming engine, and enables instant offline playback via an integrated Media3 ExoPlayer.

### Key Highlights
- **100% Standalone APK:** No user accounts, no login, no remote tracking database, no Firebase required.
- **Provider Architecture:** Clean extensible `MediaSourceProvider` pattern supporting Direct media links, YouTube, TikTok, Instagram, X/Twitter, and OpenGraph Web Media.
- **Resumable Chunked Downloader:** HTTP Range header support, live speed, remaining ETA calculation, duplicate detection, and disk space pre-flight validation.
- **Foreground Service & System Notifications:** Background downloads with status bar progress and completed alerts.
- **Built-in Media3 Player:** Hardware-accelerated local video and audio player with seek, 10s jump, and playback controls.
- **Multi-language Support:** Complete English and Arabic (العربية) localization with native RTL support.
- **Scoped Storage & MediaStore:** Safe scoped storage handling with Android FileProvider for secure media sharing.

---

## 🏗️ Architecture

The app follows the modern **Clean Architecture & MVVM** pattern:

```text
com.example/
 ├── data/
 │    ├── local/
 │    │    ├── AppDatabase.kt          (Room database)
 │    │    ├── DownloadDao.kt          (Data Access Object with Flow)
 │    │    ├── DownloadEntity.kt       (Table schema)
 │    │    └── SettingsManager.kt      (Jetpack DataStore preferences)
 │    └── repository/
 │         └── DownloadRepository.kt   (Abstracted data source)
 ├── domain/
 │    └── model/
 │         └── MediaModels.kt          (MediaInfo, MediaFormat, DownloadStatus, MediaType)
 ├── downloader/
 │    ├── DownloadEngine.kt            (Chunked streaming, resume, speed calculation)
 │    ├── DownloadNotificationHelper.kt(Notification channel and progress builder)
 │    └── DownloadService.kt           (Foreground service for background downloading)
 ├── media/
 │    └── provider/
 │         ├── MediaSourceProvider.kt  (Provider interface)
 │         ├── DirectMediaProvider.kt  (MP4, WEBM, MKV, MP3, M4A, etc.)
 │         ├── YouTubeSourceProvider.kt(YouTube oEmbed & public formats)
 │         ├── TikTokSourceProvider.kt (TikTok oEmbed & formats)
 │         ├── InstagramSourceProvider.kt
 │         ├── TwitterXSourceProvider.kt
 │         ├── GenericWebMediaProvider.kt(OpenGraph & HTML5 video tags)
 │         └── MediaAnalyzer.kt        (Router and URL validator)
 ├── ui/
 │    ├── about/                       (About screen with developer branding)
 │    ├── components/                  (SnapLoadHeader, Logo, DeveloperFooter)
 │    ├── details/                     (MediaDetailsSheet, DuplicateFileDialog)
 │    ├── downloads/                   (DownloadsScreen & DownloadsViewModel)
 │    ├── home/                        (HomeScreen & HomeViewModel)
 │    ├── legal/                       (Legal information screen)
 │    ├── navigation/                  (SnapLoadApp & NavRoutes)
 │    ├── onboarding/                  (First-launch onboarding screen)
 │    ├── player/                      (MediaPlayerScreen with Media3 ExoPlayer)
 │    ├── privacy/                     (Privacy policy screen)
 │    ├── settings/                    (SettingsScreen: Theme, Lang, Quality, Storage)
 │    ├── splash/                      (Animated splash screen)
 │    └── theme/                       (M3 Dark and Light color schemes, typography)
 └── utils/
      └── StorageUtils.kt              (Filename sanitization, StatFs disk checks)
```

---

## 🔒 Permissions & Security

SnapLoad strictly follows least-privilege permissions:
- `android.permission.INTERNET`: Required for fetching media streams and checking URLs.
- `android.permission.ACCESS_NETWORK_STATE`: Monitors connection availability.
- `android.permission.POST_NOTIFICATIONS`: Displays ongoing download progress and completion alerts on Android 13+.
- `android.permission.FOREGROUND_SERVICE`: Keeps downloads running reliably when the app is in the background.
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: Complies with Android 14+ foreground service types.

**No invasive permissions requested:** No Camera, No Microphone, No Contacts, No Location, No SMS, No broad storage access.

---

## 📁 Where Downloads are Stored

Downloaded media files are saved in application-specific scoped storage directories:
- **Videos:** `Android/data/com.aistudio.snapload.shmdy/files/Movies/`
- **Audio:** `Android/data/com.aistudio.snapload.shmdy/files/Music/`

Files are accessible offline, private to the user, and can be shared externally via the standard Android Sharesheet using the configured secure `FileProvider`.

---

## ⚖️ Legal & Compliance Notice

- Users are responsible for ensuring that they have authorization to download and save any media.
- SnapLoad does not encourage copyright infringement.
- SnapLoad does **not** bypass DRM (Digital Rights Management), paywalls, or private authentication barriers.
- Support for an online source depends on that source's technical and legal policies. Protected or DRM-restricted media streams display an informative notification instead of attempting circumvention.

---

## 🚀 How to Build & Run

### Requirements
- Android Studio Ladybug / Meerkat or newer
- JDK 17 / JDK 21
- Android SDK 36 (Minimum API 24)

### Build Commands
To compile and test:
```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```

To build a release APK:
```bash
gradle assembleRelease
```
The output APK is generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 👨‍💻 Developer Branding

**Developer by Saad Hamdy**  
SnapLoad — All Rights Reserved.
