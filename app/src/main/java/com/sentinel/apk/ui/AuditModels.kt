package com.sentinel.apk.ui

/**
 * AuditModels.kt
 * Centralised data models for the APK analysis report.
 */

data class AuditReport(
    val status: String,
    val app_name: String,
    val package_name: String,
    val safety_grade: String,
    val threat_patterns: List<ThreatPattern>?,
    val permissions: PermissionReport
)

data class ThreatPattern(
    val name: String,
    val severity: String,
    val description: String,
    val matched: List<String>
)

data class PermissionReport(
    val total: Int,
    val breakdown: Map<String, Int>,
    val risk_score: Int,
    val details: List<PermissionDetail>
)

data class PermissionDetail(
    val name: String,
    val risk: String,
    val short_name: String,
    val reason: String
)
