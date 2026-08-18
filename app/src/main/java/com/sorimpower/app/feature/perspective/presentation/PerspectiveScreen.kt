package com.sorimpower.app.feature.perspective.presentation

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.perspective.data.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

private const val MEANINGFUL_WATCH_SECONDS = 300L
private enum class YoutubeTab(val label: String) { BRAIN("뇌 지도"), CHANGE("관심 변화") }
private enum class BrainPeriod(val label: String, val days: Long) { WEEK("이번 주", 7), MONTH("이번 달", 31), YEAR("올해", 366) }
private enum class ChangePeriod(val label: String) { MONTH("월별"), YEAR("연도별") }
private data class TopicExposure(val topicId: String, val label: String, val count: Int, val percentage: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveScreen(
    padding: PaddingValues,
    viewModel: PerspectiveViewModel,
    openExploreRequest: Int = 0,
    openTopicsRequest: Int = 0,
    onSwipeEdgeLeft: () -> Unit = {},
    onSwipeEdgeRight: () -> Unit = {},
    sharedYoutubeUrl: String? = null,
    onSharedYoutubeUrlConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busyVideoId by viewModel.busyVideoId.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(YoutubeTab.BRAIN) }
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    var showTopicSuggestion by remember { mutableStateOf(false) }
    LaunchedEffect(openExploreRequest, openTopicsRequest) {
        if (openExploreRequest > 0 || openTopicsRequest > 0) { tab = YoutubeTab.BRAIN; selectedTopicId = null }
        if (openTopicsRequest > 0) showTopicSuggestion = true
    }
    BackHandler(selectedTopicId != null) { selectedTopicId = null }

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (selectedTopicId == null) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Text("유튜브 분석", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                Text("내 관심과 관점이 어떻게 이어지는지 살펴보세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                YoutubeTab.entries.forEach { item ->
                    Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.label, fontWeight = FontWeight.Bold) })
                }
            }
        }
        message?.let {
            Text(it, Modifier.fillMaxWidth().clickable { viewModel.clearMessage() }.background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        Box(Modifier.weight(1f)) {
            selectedTopicId?.let { TopicThoughtMap(state, it, { selectedTopicId = null }, viewModel::markRecommendedPerspectiveOpened) }
                ?: if (tab == YoutubeTab.BRAIN) BrainMapScreen(state) { selectedTopicId = it } else InterestChangeScreen(state)
        }
    }
    sharedYoutubeUrl?.let { url ->
        SharedYoutubeDialog(
            url = url,
            busy = busyVideoId == "shared",
            onDismiss = onSharedYoutubeUrlConsumed,
            onAnalyze = { viewModel.analyzeSharedUrl(url); onSharedYoutubeUrlConsumed() },
        )
    }
    if (showTopicSuggestion) {
        val suggestion = state.topicSuggestions.firstOrNull()
        if (suggestion != null) {
            TopicSuggestionDialog(
                suggestion = suggestion,
                video = state.videos.firstOrNull { it.id == suggestion.videoId },
                onAccept = {
                    viewModel.acceptTopicSuggestion(suggestion.videoId)
                    showTopicSuggestion = false
                },
                onDismiss = {
                    viewModel.dismissTopicSuggestion(suggestion.videoId)
                    showTopicSuggestion = false
                },
            )
        } else if (state.loaded) {
            LaunchedEffect(Unit) { showTopicSuggestion = false }
        }
    }
}

@Composable
private fun TopicSuggestionDialog(
    suggestion: TopicSuggestionEntity,
    video: WatchedVideoEntity?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("‘${suggestion.proposedName}’ 주제로 정리할까요?", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                video?.title?.takeIf(String::isNotBlank)?.let { Text(it, fontWeight = FontWeight.Bold) }
                suggestion.description.takeIf(String::isNotBlank)?.let { Text(it) }
                Text("등록하면 이 영상이 뇌 지도와 관심 변화에 반영돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("주제 등록") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("건너뛰기") } },
    )
}

@Composable
private fun SharedYoutubeDialog(url: String, busy: Boolean, onDismiss: () -> Unit, onAnalyze: () -> Unit) {
    val videoId = remember(url) { Regex("(?:v=|youtu\\.be/|shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.getOrNull(1) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("이 영상의 다른 관점을 찾아볼까요?", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                videoId?.let { AsyncImage(model = "https://i.ytimg.com/vi/$it/hqdefault.jpg", contentDescription = "공유한 YouTube 영상", modifier = Modifier.fillMaxWidth().height(180.dp)) }
                Text("공유한 YouTube 영상", fontWeight = FontWeight.Bold)
                Text("분석하면 구체적인 관점과 다음 질문이 생각 지도에 추가돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onAnalyze, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                Text("분석하기", Modifier.padding(start = 7.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
    )
}

@Composable
private fun BrainMapScreen(state: PerspectiveState, onTopicClick: (String) -> Unit) {
    var period by remember { mutableStateOf(BrainPeriod.MONTH) }
    val exposures = remember(state.videos, state.videoTopics, state.topics, period) { state.topicExposure(period.days) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("나의 관심 지도", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BrainPeriod.entries.forEach { item -> FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item.label) }) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (exposures.isEmpty()) EmptyBrainMap() else BrainCanvas(exposures.take(5), onTopicClick)
                    Text(
                        if (exposures.isEmpty()) "5분 이상 본 영상부터 관심 지도에 반영돼요." else "${period.label} ${exposures.sumOf { it.count }}개 영상을 의미 있게 시청했어요.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (exposures.isNotEmpty()) Text("영역을 눌러 생각 지도를 확인해보세요.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { CrossTopicCard(exposures.map(TopicExposure::label)) }
    }
}

@Composable
private fun BrainCanvas(exposures: List<TopicExposure>, onTopicClick: (String) -> Unit) {
    val colors = listOf(AppGreen, AppCobalt, AppOrange, Color(0xFF8C6BC5), Color(0xFF58A9A4))
    val centers = listOf(.34f to .28f, .66f to .29f, .29f to .58f, .67f to .59f, .49f to .78f)
    val outline = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(330.dp).pointerInput(exposures) {
        detectTapGestures { tap ->
            val index = exposures.indices.minByOrNull { i -> (tap - Offset(size.width * centers[i].first, size.height * centers[i].second)).getDistance() }
            if (index != null && (tap - Offset(size.width * centers[index].first, size.height * centers[index].second)).getDistance() < 90.dp.toPx()) onTopicClick(exposures[index].topicId)
        }
    }) {
        val brain = Path().apply {
            moveTo(size.width * .50f, size.height * .08f)
            cubicTo(size.width * .22f, size.height * .01f, size.width * .10f, size.height * .25f, size.width * .16f, size.height * .48f)
            cubicTo(size.width * .05f, size.height * .68f, size.width * .28f, size.height * .91f, size.width * .47f, size.height * .86f)
            cubicTo(size.width * .48f, size.height * .96f, size.width * .57f, size.height * .96f, size.width * .58f, size.height * .85f)
            cubicTo(size.width * .82f, size.height * .88f, size.width * .94f, size.height * .66f, size.width * .84f, size.height * .48f)
            cubicTo(size.width * .91f, size.height * .24f, size.width * .73f, size.height * .02f, size.width * .50f, size.height * .08f)
            close()
        }
        drawPath(brain, outline.copy(alpha = .05f)); drawPath(brain, outline.copy(alpha = .45f), style = Stroke(4f))
        drawLine(outline.copy(alpha = .17f), Offset(size.width * .5f, size.height * .1f), Offset(size.width * .52f, size.height * .84f), 3f)
        exposures.forEachIndexed { index, exposure ->
            val center = Offset(size.width * centers[index].first, size.height * centers[index].second)
            val radius = (44f + exposure.percentage * .65f).coerceAtMost(74f)
            drawCircle(colors[index].copy(alpha = .20f + exposure.percentage.coerceAtMost(40) / 100f), radius, center)
            drawCircle(colors[index], radius, center, style = Stroke(3f))
            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply { color = android.graphics.Color.rgb(37, 45, 63); textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 14.dp.toPx() }
                drawText(exposure.label.take(8), center.x, center.y - 3.dp.toPx(), paint)
                paint.textSize = 12.dp.toPx(); paint.color = colors[index].argb()
                drawText("${exposure.percentage}%", center.x, center.y + 16.dp.toPx(), paint)
            }
        }
    }
}

private fun Color.argb() = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun EmptyBrainMap() {
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Psychology, null, Modifier.size(112.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .22f))
            Text("아직 그려진 관심사가 없어요", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CrossTopicCard(currentTopics: List<String>) {
    val suggestions = listOf("AI와 직업", "수면과 집중력", "인구 변화", "30대 근력 변화").filter { candidate -> currentTopics.none { candidate.contains(it) } }.take(3)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7F2))) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Spa, null, tint = AppGreen); Text("요즘 안 보던 쪽으로", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
            Text("최근 관심사와 조금 다른 질문도 둘러보세요.", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                suggestions.forEach { Surface(shape = RoundedCornerShape(14.dp), color = Color.White) { Text(it, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) } }
            }
        }
    }
}

@Composable
private fun TopicThoughtMap(state: PerspectiveState, topicId: String, onBack: () -> Unit, onRecommendationOpen: (String) -> Unit) {
    val topic = state.topics.firstOrNull { it.id == topicId }
    val nodes = state.nodes.filter { it.topicId == topicId && it.type == "perspective" }
    var selectedNode by remember(topicId) { mutableStateOf<ThoughtNodeEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "뇌 지도로") }
            Column { Text("${topic?.name ?: "기타"} 생각 지도", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("관점을 눌러 근거와 다음 질문을 확인하세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (nodes.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("이 주제에서 아직 분석된 관점이 없어요.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else {
            ThoughtCanvas(topic?.name ?: "기타", nodes) { selectedNode = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LegendDot(AppGreen, "내가 본 관점"); Spacer(Modifier.size(20.dp)); LegendDot(Color.Gray, "추천 관점") }
        }
    }
    selectedNode?.let { node -> PerspectiveDialog(node, state.perspectives.firstOrNull { it.id == node.perspectiveId }, state, { selectedNode = null }, onRecommendationOpen) }
}

@Composable
private fun ThoughtCanvas(topicName: String, nodes: List<ThoughtNodeEntity>, onNodeClick: (ThoughtNodeEntity) -> Unit) {
    val visible = nodes.take(8)
    val lineColor = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(460.dp).padding(12.dp).pointerInput(visible) {
        detectTapGestures { tap ->
            val center = Offset(size.width / 2f, size.height / 2f)
            visible.forEachIndexed { index, node ->
                val angle = Math.PI * 2 * index / visible.size - Math.PI / 2
                val point = center + Offset((cos(angle) * size.width * .34).toFloat(), (sin(angle) * size.height * .34).toFloat())
                if ((tap - point).getDistance() <= 52.dp.toPx()) onNodeClick(node)
            }
        }
    }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        visible.forEachIndexed { index, node ->
            val angle = Math.PI * 2 * index / visible.size - Math.PI / 2
            val point = center + Offset((cos(angle) * size.width * .34).toFloat(), (sin(angle) * size.height * .34).toFloat())
            drawLine(lineColor.copy(alpha = .28f), center, point, 3f)
            val visited = node.status == "visited"
            drawCircle(if (visited) AppGreen else Color(0xFFF7F7F7), 46.dp.toPx(), point)
            drawCircle(if (visited) AppGreen else Color.Gray, 46.dp.toPx(), point, style = Stroke(3f))
            drawContext.canvas.nativeCanvas.drawText(node.label.take(9), point.x, point.y + 5.dp.toPx(), Paint().apply { color = if (visited) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 12.dp.toPx() })
        }
        drawCircle(primary, 55.dp.toPx(), center)
        drawContext.canvas.nativeCanvas.drawText(topicName.take(9), center.x, center.y + 6.dp.toPx(), Paint().apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 14.dp.toPx() })
    }
}

@Composable
private fun PerspectiveDialog(node: ThoughtNodeEntity, perspective: PerspectiveEntity?, state: PerspectiveState, onDismiss: () -> Unit, onRecommendationOpen: (String) -> Unit) {
    val context = LocalContext.current
    val sourceVideo = perspective?.let { p -> state.videos.firstOrNull { it.id == p.videoId } }
    val recommended = perspective?.let { p -> state.recommendedVideos.firstOrNull { it.perspectiveId == p.id } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(node.label, fontWeight = FontWeight.Black) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sourceVideo?.let { Text("내가 이 관점을 접한 영상\n${it.title}\n${it.channelName}", style = MaterialTheme.typography.bodySmall) }
            perspective?.description?.takeIf(String::isNotBlank)?.let { Text("이 관점의 핵심", fontWeight = FontWeight.Bold); Text(it) }
            perspective?.representativeQuestion?.takeIf(String::isNotBlank)?.let { Text("다음에 볼 만한 질문", fontWeight = FontWeight.Bold); Text("○ $it", color = MaterialTheme.colorScheme.primary) }
            recommended?.let { video ->
                Card(Modifier.fillMaxWidth().clickable { perspective?.id?.let(onRecommendationOpen); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url))) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); Text(video.channelName, style = MaterialTheme.typography.labelSmall) }; Icon(Icons.Rounded.OpenInNew, null) }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("닫기") } })
}

@Composable
private fun LegendDot(color: Color, label: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(11.dp).background(color, CircleShape)); Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun InterestChangeScreen(state: PerspectiveState) {
    var mode by remember { mutableStateOf(ChangePeriod.MONTH) }
    val periods = remember(state.videos, state.videoTopics, state.topics, mode) { state.changePeriods(mode) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("관심 변화", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("많이 본 주제가 어떻게 달라졌는지 비교해요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ChangePeriod.entries.forEach { item -> FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) }) } }
            }
        }
        if (periods.isEmpty()) item { Card { Text("5분 이상 본 영상이 쌓이면 관심 변화를 보여드릴게요.", Modifier.padding(22.dp), textAlign = TextAlign.Center) } }
        items(periods, key = { it.first }) { (label, exposure) -> PeriodCard(label, exposure) }
    }
}

@Composable
private fun PeriodCard(label: String, exposures: List<TopicExposure>) {
    val colors = listOf(AppGreen, AppCobalt, AppOrange, Color(0xFF8C6BC5))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            exposures.take(5).forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, Modifier.width(76.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { item.percentage / 100f }, Modifier.weight(1f).height(9.dp), color = colors[index % colors.size], trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Text("${item.percentage}%", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun PerspectiveState.topicExposure(days: Long, now: Long = System.currentTimeMillis()) =
    exposureFor(videos.filter { it.source != "share" && it.watchedAt >= now - days * 86_400_000L && it.watchedSec >= MEANINGFUL_WATCH_SECONDS })

private fun PerspectiveState.exposureFor(periodVideos: List<WatchedVideoEntity>): List<TopicExposure> {
    val ids = periodVideos.mapTo(hashSetOf(), WatchedVideoEntity::id)
    val links = videoTopics.filter { it.videoId in ids }
    val total = links.size.coerceAtLeast(1)
    return links.groupBy { it.topicId }.map { (topicId, topicLinks) -> TopicExposure(topicId, topicName(topicId), topicLinks.size, (topicLinks.size * 100f / total).toInt()) }.sortedByDescending(TopicExposure::percentage)
}

private fun PerspectiveState.changePeriods(mode: ChangePeriod): List<Pair<String, List<TopicExposure>>> {
    val zone = ZoneId.systemDefault(); val meaningful = videos.filter { it.source != "share" && it.watchedSec >= MEANINGFUL_WATCH_SECONDS }
    return when (mode) {
        ChangePeriod.MONTH -> meaningful.groupBy { YearMonth.from(Instant.ofEpochMilli(it.watchedAt).atZone(zone)) }.toSortedMap(compareByDescending { it }).entries.take(12).map { (month, items) -> "${month.year}년 ${month.monthValue}월" to exposureFor(items) }
        ChangePeriod.YEAR -> meaningful.groupBy { Instant.ofEpochMilli(it.watchedAt).atZone(zone).year }.toSortedMap(compareByDescending { it }).map { (year, items) -> "${year}년" to exposureFor(items) }
    }
}
