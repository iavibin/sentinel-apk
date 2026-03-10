package com.sentinel.apk.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.sentinel.apk.ui.theme.SentinelAPKTheme

/**
 * MainActivity — App entry point.
 *
 * Responsibilities:
 * 1. On Android 11+ (API 30+), check for MANAGE_EXTERNAL_STORAGE ("All Files Access").
 *    If not granted, show an explanation UI and redirect to system settings.
 * 2. On Android 13+ (API 33+), request POST_NOTIFICATIONS permission.
 * 3. Once all permissions are satisfied, launch the home screen.
 */
class MainActivity : ComponentActivity() {

    // ──────────────────────────────────────────────────────────────
    // Activity-result launchers (must be registered before onCreate)
    // ──────────────────────────────────────────────────────────────

    /** Launches the "All Files Access" system settings page and returns here. */
    private val allFilesAccessLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // User returned from Settings — re-check the permission state.
            checkAndUpdatePermissionState()
        }

    /** Requests POST_NOTIFICATIONS at runtime (API 33+). */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Regardless of the result we move forward; notifications are nice-to-have.
            checkAndUpdatePermissionState()
        }

    // ──────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────

    /** Drives the Compose UI — true when all required permissions are granted. */
    private val allPermissionsGranted = mutableStateOf(false)

    // ──────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SentinelAPKTheme {
                val permissionsOk by allPermissionsGranted

                if (permissionsOk) {
                    // ── All permissions satisfied — show main home screen ──
                    HomeScreen()
                } else {
                    // ── Missing storage permission — explain & prompt ──
                    StoragePermissionScreen(
                        onGrantClicked = { openAllFilesAccessSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate every time the user returns to the app (e.g., after settings).
        checkAndUpdatePermissionState()
    }

    // ──────────────────────────────────────────────────────────────
    // Permission helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Checks all required permissions and updates [allPermissionsGranted].
     * Also triggers POST_NOTIFICATIONS request on API 33+ if not yet granted.
     */
    private fun checkAndUpdatePermissionState() {
        val storageOk = isAllFilesAccessGranted()

        if (storageOk) {
            // Storage is fine — now handle notifications (best-effort).
            requestNotificationPermissionIfNeeded()
            allPermissionsGranted.value = true
        } else {
            allPermissionsGranted.value = false
        }
    }

    /**
     * Returns true if MANAGE_EXTERNAL_STORAGE is granted (API 30+)
     * or if the device is below API 30 (not needed there).
     */
    private fun isAllFilesAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Below API 30: READ_EXTERNAL_STORAGE is sufficient and
            // is handled by the normal runtime permission flow.
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * On API 33+ Tiramisu, requests POST_NOTIFICATIONS if not already granted.
     * This is a "nice-to-have" for Watchtower alerts — we don't block on it.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    /**
     * Deep-links the user to the "All Files Access" settings page for this app
     * so they can manually toggle MANAGE_EXTERNAL_STORAGE.
     */
    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            allFilesAccessLauncher.launch(intent)
        } else {
            // Fallback: request READ_EXTERNAL_STORAGE via the standard runtime dialog
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_LEGACY_STORAGE
            )
        }
    }

    companion object {
        private const val REQUEST_CODE_LEGACY_STORAGE = 1001
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Composables
// ════════════════════════════════════════════════════════════════════════════

/**
 * Shown when MANAGE_EXTERNAL_STORAGE is NOT granted.
 * Explains WHY the permission is required and provides a single CTA button
 * that takes the user directly to the system settings.
 */
@Composable
private fun StoragePermissionScreen(onGrantClicked: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1117), Color(0xFF161B22))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ── Icon ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3FB75D), Color(0xFF1A6E35))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

                // ── Headline ──────────────────────────────────────
                Text(
                    text = "Storage Access Required",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // ── Explanation card ──────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1C2128),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ReasonRow(
                            icon = "🛡️",
                            title = "Protect every download",
                            body = "Sentinel needs to read APK files in your Downloads " +
                                    "folder and SD card to scan them before you install."
                        )
                        HorizontalDivider(color = Color(0xFF30363D))
                        ReasonRow(
                            icon = "🔒",
                            title = "Local-only analysis",
                            body = "Your files NEVER leave your device. Analysis runs " +
                                    "entirely offline using on-device static analysis."
                        )
                        HorizontalDivider(color = Color(0xFF30363D))
                        ReasonRow(
                            icon = "🚫",
                            title = "No Play Protect black-box",
                            body = "Unlike Play Protect, Sentinel shows you exactly " +
                                    "which permissions and behaviours are suspicious—" +
                                    "and why."
                        )
                    }
                }

                // ── CTA button ────────────────────────────────────
                Button(
                    onClick = onGrantClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF238636),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Grant All Files Access",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "You'll be taken to System Settings.\nTap \"Allow\" to activate Sentinel.",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun ReasonRow(icon: String, title: String, body: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6EDF3)
            )
            Text(
                text = body,
                fontSize = 13.sp,
                color = Color(0xFF8B949E),
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * Placeholder home screen shown once all permissions are satisfied.
 * Replace this with your actual NavHost / MainScreen when ready.
 */
@Composable
private fun HomeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val intent = Intent(context, com.sentinel.apk.service.SentinelWatcherService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = Color(0xFF3FB75D),
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Sentinel is Active",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "All permissions granted. Ready to audit.",
                fontSize = 14.sp,
                color = Color(0xFF8B949E)
            )
        }
    }
}
