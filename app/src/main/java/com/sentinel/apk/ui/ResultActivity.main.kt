package com.sentinel.apk.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class PermissionItem(
    val name: String,
    val risk: String,
    val reason: String
)

val permissions = listOf(
    PermissionItem("SEND_SMS", "HIGH", "Can silently send SMS messages"),
    PermissionItem("READ_CONTACTS", "HIGH", "Reads your contact list"),
    PermissionItem("RECORD_AUDIO", "HIGH", "Can activate microphone"),
    PermissionItem("ACCESS_FINE_LOCATION", "HIGH", "Tracks your exact location"),
    PermissionItem("CAMERA", "MEDIUM", "Can access your camera"),
    PermissionItem("READ_EXTERNAL_STORAGE", "MEDIUM", "Can read files on your device"),
    PermissionItem("INTERNET", "LOW", "Can access the internet")
)

val BgColor = Color(0xFF0D1117)
val CardColor = Color(0xFF161B22)

val HighRisk = Color(0xFFF85149)
val MediumRisk = Color(0xFFD29922)
val LowRisk = Color(0xFF3FB950)

val TextColor = Color(0xFFE6EDF3)
val MutedText = Color(0xFF8B949E)

@Composable
fun ResultScreen() {

    val appName = "Flashlight Pro"
    val packageName = "com.shady.flashlight"
    val riskScore = 78

    val safetyGrade = when {
        riskScore > 80 -> "F"
        riskScore > 60 -> "D"
        riskScore > 40 -> "C"
        riskScore > 20 -> "B"
        else -> "A"
    }

    var scanning by remember { mutableStateOf(true) }
    var showPermissions by remember { mutableStateOf(false) }
    var selectedPermission by remember { mutableStateOf<PermissionItem?>(null) }

    LaunchedEffect(true) {
        delay(2000)
        scanning = false
    }

    if (scanning) {
        ScanScreen()
    } else {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(16.dp)
        ) {

            Text(
                text = appName,
                color = TextColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Text(
                text = packageName,
                color = MutedText,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RiskScoreCircle(riskScore) {
                    showPermissions = true
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GradeBadge(safetyGrade)

            Spacer(modifier = Modifier.height(30.dp))

            AnimatedVisibility(
                visible = showPermissions,
                enter = slideInVertically { it } + fadeIn()
            ) {

                LazyColumn {
                    items(permissions) { permission ->
                        PermissionRow(permission) {
                            selectedPermission = permission
                        }
                    }
                }
            }
        }
    }

    selectedPermission?.let { permission ->
        AlertDialog(
            onDismissRequest = { selectedPermission = null },
            confirmButton = {
                Button(onClick = { selectedPermission = null }) {
                    Text("Close")
                }
            },
            title = { Text(permission.name) },
            text = { Text(permission.reason) }
        )
    }
}

@Composable
fun ScanScreen() {

    val progress = remember { Animatable(0f) }

    LaunchedEffect(true) {
        progress.animateTo(
            1f,
            animationSpec = tween(2000)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Analyzing APK...",
            color = TextColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = progress.value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "${(progress.value * 100).toInt()}%",
            color = MutedText
        )
    }
}

@Composable
fun RiskScoreCircle(score: Int, onAnimationFinished: () -> Unit) {

    val progress = remember { Animatable(0f) }

    val color = when {
        score > 60 -> HighRisk
        score >= 40 -> MediumRisk
        else -> LowRisk
    }

    LaunchedEffect(true) {

        progress.animateTo(
            score / 100f,
            animationSpec = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            )
        )

        onAnimationFinished()
    }

    Box(
        modifier = Modifier.size(170.dp),
        contentAlignment = Alignment.Center
    ) {

        Canvas(modifier = Modifier.fillMaxSize()) {

            val strokeWidth = 18f

            drawArc(
                color = Color.DarkGray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                text = "Risk Factor",
                color = TextColor,
                fontSize = 13.sp
            )

            Text(
                text = "${(progress.value * 100).toInt()}%",
                color = TextColor,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun GradeBadge(grade: String) {

    val gradeColor = when (grade) {
        "F" -> HighRisk
        "D" -> MediumRisk
        "C" -> Color(0xFF58A6FF)
        "B" -> Color(0xFF3FB950)
        else -> Color(0xFF2EA043)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .background(gradeColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {

            Text(
                text = "Safety Grade: $grade",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun PermissionRow(permission: PermissionItem, onClick: () -> Unit) {

    val badgeColor = when (permission.risk) {
        "HIGH" -> HighRisk
        "MEDIUM" -> MediumRisk
        else -> LowRisk
    }

    val icon = when (permission.risk) {
        "HIGH" -> Icons.Default.Warning
        "MEDIUM" -> Icons.Default.Info
        else -> Icons.Default.CheckCircle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(CardColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = badgeColor
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = permission.name,
                color = TextColor,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = permission.reason,
                color = MutedText,
                fontSize = 13.sp
            )
        }

        Box(
            modifier = Modifier
                .background(badgeColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = permission.risk,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}