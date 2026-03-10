"""
Sentinel APK — analyzer.py
============================
Core intelligence layer for the backend.
Imported by main.py to handle:
  1. Plain-English reason mapping for dangerous permissions
  2. Lethal Permission Cluster detection
  3. Safety Grade calculation (A–F)
"""

from __future__ import annotations

# ---------------------------------------------------------------------------
# 1. PERMISSION REASONS
#    Maps each dangerous permission to a plain-English explanation
#    shown directly in the risk report UI.
# ---------------------------------------------------------------------------

PERMISSION_REASONS: dict[str, str] = {
    # SMS
    "android.permission.SEND_SMS":              "Can silently send SMS messages — may incur charges",
    "android.permission.RECEIVE_SMS":           "Can read every SMS you receive including OTPs",
    "android.permission.READ_SMS":              "Can read all your private SMS messages and bank 2FA codes",
    "android.permission.RECEIVE_MMS":           "Can intercept MMS messages sent to you",

    # Contacts & Accounts
    "android.permission.READ_CONTACTS":         "Can read your entire contact list",
    "android.permission.WRITE_CONTACTS":        "Can add, edit, or delete your contacts",
    "android.permission.GET_ACCOUNTS":          "Can see all Google and app accounts on your device",

    # Location
    "android.permission.ACCESS_FINE_LOCATION":      "Can track your precise GPS location in real time",
    "android.permission.ACCESS_COARSE_LOCATION":    "Can track your approximate location via Wi-Fi/cell",
    "android.permission.ACCESS_BACKGROUND_LOCATION":"Can track your location even when the app is closed",

    # Camera & Microphone
    "android.permission.CAMERA":               "Can activate your camera without your knowledge",
    "android.permission.RECORD_AUDIO":         "Can activate your microphone and record conversations",

    # Storage
    "android.permission.READ_EXTERNAL_STORAGE":    "Can read all files stored on your device",
    "android.permission.WRITE_EXTERNAL_STORAGE":   "Can create, modify, or delete files on your device",
    "android.permission.MANAGE_EXTERNAL_STORAGE":  "Has unrestricted access to all files on your device",

    # Phone & Calls
    "android.permission.READ_PHONE_STATE":     "Can read your phone number, IMEI, and call status",
    "android.permission.CALL_PHONE":           "Can make phone calls without your confirmation",
    "android.permission.READ_CALL_LOG":        "Can read your full call history",
    "android.permission.WRITE_CALL_LOG":       "Can modify or delete your call history",
    "android.permission.PROCESS_OUTGOING_CALLS": "Can intercept and redirect your outgoing calls",

    # Biometrics & Security
    "android.permission.USE_BIOMETRIC":        "Can request biometric authentication (fingerprint/face)",
    "android.permission.USE_FINGERPRINT":      "Can request fingerprint authentication",

    # Device Admin & Accessibility (very high risk)
    "android.permission.BIND_ACCESSIBILITY_SERVICE": "Can read everything on your screen — used by spyware",
    "android.permission.BIND_DEVICE_ADMIN":    "Can enforce device policies and remotely wipe your phone",
    "android.permission.SYSTEM_ALERT_WINDOW":  "Can draw overlays on top of other apps — used for phishing",
    "android.permission.INSTALL_PACKAGES":     "Can silently install other apps on your device",
    "android.permission.DELETE_PACKAGES":      "Can silently uninstall apps from your device",

    # Network & Bluetooth
    "android.permission.INTERNET":             "Can send and receive data over the internet",
    "android.permission.BLUETOOTH_SCAN":       "Can scan for nearby Bluetooth devices",
    "android.permission.BLUETOOTH_CONNECT":    "Can connect to Bluetooth devices around you",

    # Sensors
    "android.permission.BODY_SENSORS":         "Can access health data from wearable sensors",
    "android.permission.ACTIVITY_RECOGNITION": "Can monitor your physical activity (walking, driving)",
}

# Default reason for permissions not in the dictionary
DEFAULT_REASON = "Requests access to a sensitive system resource"


def get_permission_reason(permission: str) -> str:
    """Return a plain-English reason for a given permission string."""
    return PERMISSION_REASONS.get(permission, DEFAULT_REASON)


# ---------------------------------------------------------------------------
# 2. LETHAL PERMISSION CLUSTERS
#    Detects dangerous permission combinations that together indicate
#    a specific threat pattern — even if each permission alone seems harmless.
# ---------------------------------------------------------------------------

# Each cluster is a dict with:
#   "name"        — human-readable threat pattern label
#   "required"    — ALL of these permissions must be present to trigger
#   "severity"    — "CRITICAL" or "HIGH"
#   "description" — plain-English explanation shown in the report

LETHAL_CLUSTERS: list[dict] = [
    {
        "name": "SMS Exfiltration",
        "required": [
            "android.permission.READ_SMS",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can read your private messages and silently upload them to a remote server.",
    },
    {
        "name": "OTP Interceptor",
        "required": [
            "android.permission.RECEIVE_SMS",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can intercept one-time passwords (bank 2FA codes) and send them to an attacker.",
    },
    {
        "name": "Silent SMS Bomber",
        "required": [
            "android.permission.SEND_SMS",
            "android.permission.READ_CONTACTS",
        ],
        "severity": "CRITICAL",
        "description": "Can send SMS messages to all your contacts without your knowledge.",
    },
    {
        "name": "Location Tracker",
        "required": [
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.INTERNET",
        ],
        "severity": "HIGH",
        "description": "Can continuously track your real-time GPS location and upload it remotely.",
    },
    {
        "name": "Background Stalker",
        "required": [
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can track your location 24/7 even when the app is not open.",
    },
    {
        "name": "Contact Harvester",
        "required": [
            "android.permission.READ_CONTACTS",
            "android.permission.INTERNET",
        ],
        "severity": "HIGH",
        "description": "Can steal your entire contact list and upload it to a remote server.",
    },
    {
        "name": "Remote Spy",
        "required": [
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can secretly record audio and video and transmit it to a remote server.",
    },
    {
        "name": "Screen Phisher",
        "required": [
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can overlay fake login screens on top of banking apps to steal credentials.",
    },
    {
        "name": "Silent App Installer",
        "required": [
            "android.permission.INSTALL_PACKAGES",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can silently download and install additional malicious apps without your consent.",
    },
    {
        "name": "Full Device Surveillance",
        "required": [
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.INTERNET",
        ],
        "severity": "CRITICAL",
        "description": "Can read everything displayed on your screen and send it to a remote server.",
    },
    {
        "name": "Call Interceptor",
        "required": [
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.RECORD_AUDIO",
        ],
        "severity": "CRITICAL",
        "description": "Can intercept and record your phone calls.",
    },
    {
        "name": "File Exfiltrator",
        "required": [
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.INTERNET",
        ],
        "severity": "HIGH",
        "description": "Can read all files on your device and upload them to a remote server.",
    },
]


def detect_lethal_clusters(permissions: list[str]) -> list[dict]:
    """
    Check the app's permission list against all known lethal clusters.
    Returns a list of matched threat patterns.

    Each result contains:
      - name        : threat pattern label
      - severity    : CRITICAL or HIGH
      - description : plain-English explanation
      - matched     : which permissions triggered this cluster
    """
    permission_set = set(permissions)
    detected: list[dict] = []

    for cluster in LETHAL_CLUSTERS:
        required = set(cluster["required"])
        if required.issubset(permission_set):
            detected.append({
                "name":        cluster["name"],
                "severity":    cluster["severity"],
                "description": cluster["description"],
                "matched":     [p.split(".")[-1] for p in cluster["required"]],
            })

    return detected


# ---------------------------------------------------------------------------
# 3. SAFETY GRADE
#    Converts the 0-100 risk score into a letter grade shown in the UI.
# ---------------------------------------------------------------------------

def get_safety_grade(risk_score: int) -> str:
    """
    Convert a 0-100 risk score to a letter grade.

      A  →  0–19   (Clean)
      B  →  20–39  (Low Risk)
      C  →  40–59  (Moderate Risk)
      D  →  60–79  (High Risk)
      F  →  80–100 (Critical / Do Not Install)
    """
    if risk_score < 20:
        return "A"
    elif risk_score < 40:
        return "B"
    elif risk_score < 60:
        return "C"
    elif risk_score < 80:
        return "D"
    else:
        return "F"