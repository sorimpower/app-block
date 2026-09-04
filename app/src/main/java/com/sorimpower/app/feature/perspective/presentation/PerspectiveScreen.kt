package com.sorimpower.app.feature.perspective.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sorimpower.app.feature.perspective.data.InterestPeriod
import com.sorimpower.app.feature.perspective.data.InterestPeriodAnalysisEntity
import com.sorimpower.app.feature.perspective.data.WatchedVideoEntity
import com.sorimpower.app.feature.perspective.data.toInterestCategories
import com.sorimpower.app.feature.perspective.data.toPersonalInterestInsights
import com.sorimpower.app.feature.perspective.data.toInterestVideoRecommendations
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

@Composable
fun PerspectiveScreen(
    padding: PaddingValues,
    viewModel: PerspectiveViewModel,
    onSwipeEdgeLeft: () -> Unit = {},
    onSwipeEdgeRight: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val analyzing by viewModel.analyzing.collectAsState()
    val message by viewModel.message.collectAsState()
    val watchedVideoPlayback by viewModel.watchedVideoPlayback.collectAsState()
    var period by remember { mutableStateOf(InterestPeriod.WEEK) }
    val reports = state.analyses.filter { it.periodType == period.type }.sortedByDescending { it.periodKey }
    var selectedReportKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period) { selectedReportKey = null }
    LaunchedEffect(reports) {
        if (selectedReportKey !in reports.map { it.periodKey }) selectedReportKey = reports.firstOrNull()?.periodKey
    }
    val selectedReportIndex = reports.indexOfFirst { it.periodKey == selectedReportKey }.coerceAtLeast(0)
    val selectedReport = reports.getOrNull(selectedReportIndex)
    val selectedVideos = selectedReport?.let { report ->
        val range = report.timeRange()
        state.videos.filter { it.watchedAt in range }
    }.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("관심 변화", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("시청 기록을 모아 어떤 분야를 많이 봤는지와 관심 흐름의 변화를 보여드려요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InterestPeriod.entries.forEach { item -> FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item.label) }) }
                }
                AssistChip(
                    onClick = { viewModel.analyze(period) },
                    enabled = analyzing == null,
                    label = { Text(if (analyzing == period) "분석 중…" else "${period.label} AI 분석하기") },
                    leadingIcon = { if (analyzing == period) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null) },
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        if (reports.isEmpty()) item {
            EmptyHistory(period, state.videos.size)
        }
        selectedReport?.let { report ->
            item {
                PeriodNavigator(
                    report = report,
                    canGoPrevious = selectedReportIndex < reports.lastIndex,
                    canGoNext = selectedReportIndex > 0,
                    onPrevious = { selectedReportKey = reports[selectedReportIndex + 1].periodKey },
                    onNext = { selectedReportKey = reports[selectedReportIndex - 1].periodKey },
                )
            }
            item(key = "${report.periodType}:${report.periodKey}") { InterestReportCard(report) }
        }
        if (selectedReport != null && selectedVideos.isNotEmpty()) {
            item { Text("이 기간의 시청 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp)) }
            items(selectedVideos, key = WatchedVideoEntity::id) { video ->
                VideoHistoryRow(video, watchedVideoPlayback[video.id], onResolve = { viewModel.resolveWatchedVideo(video) }) { viewModel.deleteWatchRecord(video.id) }
            }
        }
    }
}

private fun InterestPeriodAnalysisEntity.timeRange(): LongRange {
    if (periodFrom > 0 && periodUntil > periodFrom) return periodFrom until periodUntil
    val zone = ZoneId.systemDefault()
    val start = runCatching {
        when (periodType) {
            "day" -> LocalDate.parse(periodKey)
            "week" -> {
                val (year, week) = Regex("(\\d{4})-W(\\d{2})").matchEntire(periodKey)!!.destructured
                LocalDate.of(year.toInt(), 1, 4)
                    .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
                    .with(WeekFields.ISO.dayOfWeek(), 1)
            }
            "month" -> java.time.YearMonth.parse(periodKey).atDay(1)
            "year" -> LocalDate.of(periodKey.toInt(), 1, 1)
            else -> error("Unknown period")
        }
    }.getOrElse { return LongRange.EMPTY }
    val end = when (periodType) {
        "day" -> start.plusDays(1)
        "week" -> start.plusWeeks(1)
        "month" -> start.plusMonths(1)
        "year" -> start.plusYears(1)
        else -> start
    }
    return start.atStartOfDay(zone).toInstant().toEpochMilli() until end.atStartOfDay(zone).toInstant().toEpochMilli()
}

@Composable private fun PeriodNavigator(
    report: InterestPeriodAnalysisEntity,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = canGoPrevious) { Icon(Icons.Rounded.ChevronLeft, "이전 ${report.periodType} 분석") }
        Text(report.periodLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, enabled = canGoNext) { Icon(Icons.Rounded.ChevronRight, "다음 ${report.periodType} 분석") }
    }
}

@Composable private fun EmptyHistory(period: InterestPeriod, videoCount: Int) = Card {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (videoCount == 0) "아직 수집된 시청 기록이 없어요." else "${period.label} 분석이 아직 없어요.", fontWeight = FontWeight.Bold)
        Text(if (videoCount == 0) "YouTube를 시청하면 기록이 백그라운드로 쌓입니다." else "위의 AI 분석 버튼으로 지금 바로 관심 흐름을 만들 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun InterestReportCard(report: InterestPeriodAnalysisEntity) = Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(1.dp)) {
    val categories = remember(report.categoriesJson) { report.categoriesJson.toInterestCategories() }
    val personalInsights = remember(report.personalInsightsJson) { report.personalInsightsJson.toPersonalInterestInsights() }
    val recommendations = remember(report.recommendedVideosJson) { report.recommendedVideosJson.toInterestVideoRecommendations() }
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(report.periodLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); Text("시청 영상 ${report.videoCount}개", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("AI 요약", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        categories.forEachIndexed { index, category ->
            val colors = listOf(Color(0xFF167C5A), Color(0xFF4267D5), Color(0xFFE78132), Color(0xFF8C63BB), Color(0xFFBD4E74))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.name, Modifier.width(78.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(9.dp).weight(category.percentage.coerceAtLeast(1).toFloat()).background(colors[index % colors.size], RoundedCornerShape(99.dp)))
                Spacer(Modifier.width(8.dp)); Text("${category.percentage}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(report.summary, style = MaterialTheme.typography.bodyMedium)
        if (report.trendSummary.isNotBlank()) Text(report.trendSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (personalInsights.isNotEmpty()) {
            Text("시청 기록에서 읽힌 나", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text("시청 기록만으로 정답을 단정하지 않은, 이번 기간의 관찰이에요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            personalInsights.forEach { insight ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(insight.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(insight.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (recommendations.isNotEmpty()) {
            Text("이 취향이라면 좋아할 영상", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text("이번 시청 취향을 바탕으로 찾은 공개 YouTube 영상이에요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recommendations.forEach { recommendation -> RecommendedVideoRow(recommendation) }
        }
    }
}

@Composable private fun RecommendedVideoRow(recommendation: com.sorimpower.app.feature.perspective.data.InterestVideoRecommendation) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recommendation.url))) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recommendation.thumbnailUrl.isNotBlank()) AsyncImage(
                model = recommendation.thumbnailUrl,
                contentDescription = "${recommendation.title} 썸네일",
                modifier = Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(recommendation.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                if (recommendation.channelName.isNotBlank()) Text(recommendation.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recommendation.reason.isNotBlank()) Text(recommendation.reason, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable private fun VideoHistoryRow(video: WatchedVideoEntity, playback: com.sorimpower.app.feature.perspective.data.WatchedVideoPlayback?, onResolve: () -> Unit, onDelete: () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f))) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(video.id) { onResolve() }
    val date = remember(video.watchedAt) { Instant.ofEpochMilli(video.watchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일")) }
    val resolvedId = playback?.videoId ?: video.youtubeVideoId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    val targetUrl = playback?.url ?: video.url
    val thumbnailUrl = playback?.thumbnailUrl ?: resolvedId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
    val hasLink = targetUrl.isNotBlank()
    Row(
        Modifier.fillMaxWidth().then(if (hasLink) Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))) } else Modifier).padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "${video.title} 썸네일",
                modifier = Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else Box(
            Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("YouTube", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text("${video.channelName.ifBlank { "YouTube" }} · $date · ${video.watchedSec / 60}분", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasLink) Text("YouTube에서 열기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "시청 기록 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
