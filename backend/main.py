"""
Sentinel APK — Backend API
FastAPI service that performs local static analysis on uploaded APK files
using androguard and returns a structured JSON risk report.
"""

from __future__ import annotations

import os
import tempfile
from typing import Annotated

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

# analyzer components
from analyzer import detect_lethal_clusters, get_permission_reason, get_safety_grade

# androguard components
from androguard.core.bytecodes.apk import APK

# ---------------------------------------------------------------------------
# App bootstrap
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Sentinel APK Auditor",
    description=(
        "Local static-analysis engine for Android APK files. "
        "Accepts an APK upload and returns a structured risk report."
    ),
    version="0.1.0",
)

# Allow the Android emulator (10.0.2.2) and localhost during development.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # tighten for production
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# Permissions considered high-risk for a human-readable summary
HIGH_RISK_PERMISSIONS: set[str] = {
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_CALL_LOG",
    "android.permission.PROCESS_OUTGOING_CALLS",
    "android.permission.SEND_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_SMS",
    "android.permission.RECORD_AUDIO",
    "android.permission.CAMERA",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.INSTALL_PACKAGES",
    "android.permission.DELETE_PACKAGES",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_DEVICE_ADMIN",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.GET_ACCOUNTS",
    "android.permission.USE_BIOMETRIC",
    "android.permission.USE_FINGERPRINT",
}


def classify_permission(permission: str) -> str:
    """Return 'HIGH', 'MEDIUM', or 'LOW' risk label for a permission."""
    if permission in HIGH_RISK_PERMISSIONS:
        return "HIGH"
    if "READ" in permission or "ACCESS" in permission or "GET" in permission:
        return "MEDIUM"
    return "LOW"


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@app.get("/", summary="Health check")
async def root() -> dict:
    return {"service": "Sentinel APK Auditor", "status": "online", "version": "0.1.0"}


@app.post(
    "/audit",
    summary="Audit an APK file",
    response_description="Structured static-analysis report",
    status_code=status.HTTP_200_OK,
)
async def audit_apk(
    file: UploadFile = File(...),
) -> JSONResponse:
    """
    Accept an APK file upload, run androguard static analysis, and return
    a JSON report. Always returns 200 OK to maintain Android client stability.
    """
    print(f"[*] Received upload request: {file.filename}")

    # Basic validation ---------------------------------------------------
    filename: str = file.filename or "unknown.apk"
    if not filename.lower().endswith(".apk"):
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content={"status": "error", "message": "Only .apk files are accepted."},
        )

    # Save to a temp file (androguard needs a real file path) ------------
    apk_bytes: bytes = await file.read()
    if len(apk_bytes) == 0:
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content={"status": "error", "message": "Uploaded file is empty."},
        )

    import logging
    logger = logging.getLogger(__name__)

    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            tmp_path = tmp.name
            tmp.write(apk_bytes)

        # 1. DEFENSIVE APK INITIALIZATION --------------------------------
        apk_obj = None
        try:
            # Try normal parsing first
            apk_obj = APK(tmp_path)
        except Exception as e:
            logger.warning(f"Standard APK parsing failed: {e}. Trying skip_resources=True...")
            try:
                # Fallback: Skip resource parsing (common cause of ResParserError)
                apk_obj = APK(tmp_path, skip_resources=True)
            except Exception as e2:
                logger.error(f"Total APK parse failure: {e2}")
                return JSONResponse(
                    status_code=status.HTTP_200_OK,
                    content={
                        "status": "error", 
                        "message": "Sentinel Error: This APK structure is incompatible with our static analyzer."
                    },
                )

        # 2. DEFENSIVE FIELD EXTRACTION ----------------------------------
        try:
            package_name: str = apk_obj.get_package() or "unknown.package"
        except:
            package_name = "unknown.package"

        # Wrap get_app_name in try-except to handle ResParserError
        try:
            app_name: str = apk_obj.get_app_name() or package_name
        except Exception as e:
            logger.warning(f"ResParserError in get_app_name: {e}. Falling back to package name.")
            app_name = package_name

        print(f"[*] Processing APK: {package_name} for {app_name}")

        try:
            version_name: str = apk_obj.get_androidversion_name() or "unknown"
            version_code: str = apk_obj.get_androidversion_code() or "unknown"
            min_sdk: str = apk_obj.get_min_sdk_version() or "unknown"
            target_sdk: str = apk_obj.get_target_sdk_version() or "unknown"
        except:
            version_name = version_code = min_sdk = target_sdk = "unknown"

        # 3. PERMISSION ANALYSIS -----------------------------------------
        try:
            declared_permissions: list[str] = sorted(apk_obj.get_permissions())
        except:
            declared_permissions = []

        # Annotated permissions
        annotated_permissions: list[dict] = [
            {
                "name": perm,
                "risk": classify_permission(perm),
                "short_name": perm.split(".")[-1],
                "reason": get_permission_reason(perm),
            }
            for perm in declared_permissions
        ]

        # Count by risk level
        risk_counts: dict[str, int] = {"HIGH": 0, "MEDIUM": 0, "LOW": 0}
        for p in annotated_permissions:
            risk_counts[p["risk"]] += 1

        # Simple aggregate risk score (0–100)
        risk_score: int = min(
            100,
            risk_counts["HIGH"] * 10 + risk_counts["MEDIUM"] * 3 + risk_counts["LOW"],
        )

        threat_patterns = detect_lethal_clusters(declared_permissions)
        safety_grade = get_safety_grade(risk_score)

        report = {
            "status": "success",
            "file": filename,
            "app_name": app_name,
            "package_name": package_name,
            "safety_grade": safety_grade,
            "version": {
                "name": version_name,
                "code": version_code,
            },
            "sdk": {
                "min": min_sdk,
                "target": target_sdk,
            },
            "threat_patterns": threat_patterns,
            "permissions": {
                "total": len(declared_permissions),
                "breakdown": risk_counts,
                "risk_score": risk_score,
                "details": annotated_permissions,
            },
        }

        print(f"[+] Audit complete: {package_name} (Risk: {risk_score})")
        return JSONResponse(status_code=status.HTTP_200_OK, content=report)

    except Exception as exc:  # noqa: BLE001
        logger.exception("Unexpected error during APK audit")
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content={"status": "error", "message": f"Sentinel Internal Error: {str(exc)}"},
        )

    finally:
        # Always clean up the temp file
        if tmp_path:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass