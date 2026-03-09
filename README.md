# Sentinel APK

> **An active APK security auditor for Android — because "unsafe" is not an explanation.**

## The Problem

Google Play Protect is a black box. It warns users that an APK is _"potentially harmful"_ but never explains **why**. Users who sideload apps from third-party sources have no actionable information.

## The Solution

Sentinel APK intercepts APK downloads, performs **local static analysis**, and produces a **human-readable risk report** that shows exactly which permissions are dangerous and why — without ever sending your files to the cloud.

| Mode | Description |
|------|-------------|
| 🗼 **Active Watchtower** | Background service that monitors your Downloads folder and auto-scans any new `.apk` |
| 🧪 **Manual Sandbox** | Upload any APK manually and get an instant risk report |

---

## Project Structure

```
Sentinel APK/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissions & service declarations
│   │   ├── java/com/sentinel/apk/
│   │   │   └── ui/
│   │   │       └── MainActivity.kt      # Permission flow + Compose UI
│   │   └── res/xml/
│   │       ├── file_provider_paths.xml
│   │       ├── backup_rules.xml
│   │       └── data_extraction_rules.xml
│   └── build.gradle.kts                 # App-level Gradle (Retrofit, Compose …)
├── gradle/
│   └── libs.versions.toml              # Centralised version catalog
├── build.gradle.kts                     # Project-level Gradle
├── settings.gradle.kts
└── backend/
    ├── main.py                          # FastAPI /audit endpoint (androguard)
    └── requirements.txt
```

---

## Android Setup

### Requirements
- Android Studio Ladybug (2024.2+)
- Android SDK 35 / 36 (Android 15 / 16)
- Kotlin 2.1+

### Build
Open the project root in Android Studio and sync Gradle. No extra steps needed.

---

## Backend Setup

```bash
cd backend
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS / Linux
source .venv/bin/activate

pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

The API will be available at `http://localhost:8000`.  
Interactive docs: `http://localhost:8000/docs`

### `/audit` Endpoint

```
POST /audit
Content-Type: multipart/form-data
Body: file=<apk file>
```

**Response:**
```json
{
  "status": "success",
  "package_name": "com.example.app",
  "permissions": {
    "total": 12,
    "breakdown": { "HIGH": 3, "MEDIUM": 5, "LOW": 4 },
    "risk_score": 45,
    "details": [
      { "name": "android.permission.CAMERA", "risk": "HIGH", "short_name": "CAMERA" }
    ]
  }
}
```

---

## Key Permissions

| Permission | Why Sentinel Needs It |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Read APK files from Downloads & SD card for analysis |
| `FOREGROUND_SERVICE` | Keep Watchtower alive while scanning in background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required on Android 14+ for file-monitoring services |
| `POST_NOTIFICATIONS` | Alert the user when a risky APK is detected |

---

*Built for a hackathon. Stay safe, stay informed.*