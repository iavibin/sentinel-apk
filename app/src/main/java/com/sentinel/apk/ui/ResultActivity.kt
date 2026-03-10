package com.sentinel.apk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.sentinel.apk.ui.theme.SentinelAPKTheme
import kotlinx.coroutines.delay

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reportJson = intent.getStringExtra("report_json") ?: ""
        val report = try {
            Gson().fromJson(reportJson, AuditReport::class.java)
        } catch (e: Exception) {
            null
        }

        setContent {
            SentinelAPKTheme {
                Surface(color = BgColor, modifier = Modifier.fillMaxSize()) {
                    if (report != null) {
                        ResultScreen(report)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("No Report Found or Invalid JSON", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultScreen(report: AuditReport) {
    val appName = report.app_name
    val packageName = report.package_name
    val riskScore = report.permissions.risk_score
    val safetyGrade = report.safety_grade

    var scanning by remember { mutableStateOf(true) }
    var showPermissions by remember { mutableStateOf(false) }
    var selectedPermission by remember { mutableStateOf<PermissionDetail?>(null) }
    var selectedThreat by remember { mutableStateOf<ThreatPattern?>(null) }

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

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = showPermissions,
                enter = slideInVertically { it } + fadeIn()
            ) {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    
                    val threats = report.threat_patterns
                    if (!threats.isNullOrEmpty()) {
                        item {
                            Text(
                                text = "⚠️ Threat Patterns Detected",
                                color = HighRisk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
                            )
                        }
                        items(threats) { tp ->
                            ThreatPatternRow(tp) {
                                selectedThreat = tp
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    item {
                        Text(
                            text = "Permissions (${report.permissions.total})",
                            color = TextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(report.permissions.details) { permission ->
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
            title = { Text(permission.short_name) },
            text = { Text(permission.reason) }
        )
    }

    selectedThreat?.let { threat ->
        AlertDialog(
            onDismissRequest = { selectedThreat = null },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HighRisk),
                    onClick = { selectedThreat = null }
                ) {
                    Text("Close")
                }
            },
            title = { Text(threat.name, color = HighRisk) },
            text = { Text("${threat.description}\n\nMatched: ${threat.matched.joinToString(", ")}") }
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
            color = Color(0xFF3FB950),
            trackColor = Color.DarkGray,
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
fun ThreatPatternRow(threat: ThreatPattern, onClick: () -> Unit) {
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
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = HighRisk
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = threat.name,
                color = HighRisk,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = threat.description,
                color = MutedText,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun PermissionRow(permission: PermissionDetail, onClick: () -> Unit) {
    val badgeColor = when (permission.risk) {
        "HIGH", "CRITICAL" -> HighRisk
        "MEDIUM" -> MediumRisk
        else -> LowRisk
    }

    val icon = when (permission.risk) {
        "HIGH", "CRITICAL" -> Icons.Default.Warning
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
                text = permission.short_name,
                color = TextColor,
                fontWeight = FontWeight.Bold
            )
            if (permission.reason.isNotEmpty()) {
                Text(
                    text = permission.reason,
                    color = MutedText,
                    fontSize = 13.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .background(badgeColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = permission.risk,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}