package com.sorimpower.app.feature.bodylog.domain

data class BodyLogAiAnalysis(
    val headline: String,
    val trendSummary: String,
    val encouragement: String,
    val mealAssessment: String = "",
    val calorieAssessment: String = "",
    val nextSteps: List<String>,
    val safetyNote: String,
    val analyzedAt: Long = System.currentTimeMillis(),
)
