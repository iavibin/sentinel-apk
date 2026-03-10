# Sentinel-APK: Your Mobile Security Watchtower (v1.0.0)

**Sentinel-APK** is a powerful security tool designed to protect your Android device from hidden threats. It acts like a digital 'X-ray' for apps, checking them for danger before they ever get a chance to steal your data.

## 1. The Directory Tree
A clean view of the Sentinel-APK project structure:

```text
Sentinel APK/
├── app/                        # Android Application (Kotlin/Compose)
│   ├── src/main/
│   │   ├── java/com/sentinel/apk/
│   │   │   ├── service/        # Background scan logic
│   │   │   ├── ui/             # Modern user interface
│   │   │   └── model/          # Data structures
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── backend/                    # Security Brain (Python/FastAPI)
│   ├── main.py                 # API Endpoints
│   ├── analyzer.py             # Security analysis logic
│   └── requirements.txt
├── build.gradle.kts             # Project configuration
└── README.md                   # Project documentation
```

## 2. Project Overview (Layman's Terms)

### Active Watchtower (SentinelWatcherService)
**In plain English:** A digital security guard that watches your downloads 24/7. It stays alert in the background, making sure every new app you download is checked immediately.

### Static Analysis Engine (FastAPI & Androguard)
**In plain English:** A 'cloud brain' that takes an app apart to see how it works without actually running it. This allows us to find hidden traps before the app even starts.

### Risk Reporting (ResultActivity)
**In plain English:** A simple dashboard that gives you a 'Safety Grade' from A to F so you know if an app is safe to install. No technical jargon—just a clear "Yes" or "No" for your safety.

## 3. Technical Stack Highlights

*   **Kotlin & Jetpack Compose**: Used for a smooth, modern Android interface that feels premium and responsive.
*   **FastAPI**: Ensures high-speed communication between the phone and the analyzer, providing near-instant results.
*   **Androguard**: The industry-standard tool for deconstructing Android files, used to uncover deep-seated security risks.

## 4. Current Status: Prototype v1.0.0
**End-to-End Loop is Operational:**

- ✅ **Real-time APK detection** in the Downloads folder.
- ✅ **Successful transmission** to the analysis backend.
- ✅ **Generation of Risk Scores** and Threat Patterns (like SMS Exfiltration).
- ✅ **High-priority user notifications** and interactive reports.

---
*Prototype v1.0.0. Simple, transparent, and secure.*
